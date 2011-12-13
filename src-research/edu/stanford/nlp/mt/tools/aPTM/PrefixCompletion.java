package edu.stanford.nlp.mt.tools.aPTM;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import java.io.IOException;
import java.lang.reflect.Type;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.FlatPhraseTable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.LanguageModel;
import edu.stanford.nlp.mt.base.LanguageModels;
import edu.stanford.nlp.mt.base.RawSequence;
import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.Sequences;
import edu.stanford.nlp.mt.base.TranslationOption;
import edu.stanford.nlp.mt.decoder.inferer.AbstractInferer;
import edu.stanford.nlp.mt.decoder.inferer.impl.PrefixDecoder;
import edu.stanford.nlp.mt.decoder.util.EnumeratedConstrainedOutputSpace;

/**
 * Prefix completion prototype
 * 
 * @author daniel
 *
 */

public class PrefixCompletion extends AbstractHandler {

  public static final String DEBUG_PROPERTY = "DebugPrefixCompletion";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "true"));

  public static final String HTML_RESOURCES_PATH = "edu/stanford/nlp/mt/tools/aPTM/ui";
  public static final String DEFAULT_WEB_PAGE = "/translate.html";
  PrefixDecoder<String> prefixDecoder; 
  LanguageModel<IString> lm;
  FlatPhraseTable<String> phr;
  double lmWt;
  double[] phrTableWts;

  public static final String PREFIX_GET_NAME = "prefix";
  public static final String SOURCE_GET_NAME = "source";

  private static boolean hasParameter(Request baseRequest, String paramName) {
    String param = baseRequest.getParameter(paramName);
    if (DEBUG && param != null && param.length() > 0) {
      System.err.printf("%s: %s\n", paramName, param);
    }
    return param != null && param.length() > 0;
  }

  public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
  {
    String responseString = null;

    Gson gson = new Gson();
    @SuppressWarnings("unchecked")
    Enumeration<String> e = baseRequest.getParameterNames();
    List<String> parameters = new ArrayList<String>();
    while (e.hasMoreElements()) {
      parameters.add(e.nextElement());
    }

    if(DEBUG) {
      System.err.println("Received request wiht parameters " + parameters);
    }
    
    if (hasParameter(baseRequest, "ptmInit")) {
      PTMInitRequest ptmRequest = gson.fromJson(baseRequest.getParameter("ptmInit"), PTMInitRequest.class);
      if (DEBUG) {
        System.err.println("PTMInitRequest: " + gson.toJson(ptmRequest));
      }

      List<String> oovStringsList = getOOVs(ptmRequest.source);

      PTMOOVResponse ptmResponse = new PTMOOVResponse(oovStringsList);
      Type t = new TypeToken<PTMOOVResponse>() {}.getType();           
      //responseString = wrapResponse("ptmInitResponse", gson.toJson(ptmResponse, t));
      responseString = gson.toJson(ptmResponse, t);

    } else if (hasParameter(baseRequest, "ptmOOV")) {
      PTMOOVRequest ptmRequest = gson.fromJson(baseRequest.getParameter("ptmOOV"), 
          PTMOOVRequest.class);
      if (DEBUG) {
        System.err.println("PTMOOVRequest: " + gson.toJson(ptmRequest));
      }
      // responseString = wrapResponse("ptmOOVResponse", gson.toJson(new PTMStatusOk()));  
      responseString = gson.toJson(new PTMStatusOk());  
    
    } else if (hasParameter(baseRequest, "ptmPredict") ||
        (hasParameter(baseRequest, "prefix"))) {       
      PTMPredictionRequest ptmRequest;
      if (hasParameter(baseRequest, "ptmPredict")) {
        ptmRequest = gson.fromJson(baseRequest.getParameter("ptmPredict"), PTMPredictionRequest.class);
      } else {
        String sourceLang = baseRequest.getParameter("sourceLang");
        String targetLang = baseRequest.getParameter("targetLang");
        String source = baseRequest.getParameter("source");
        String prefix = baseRequest.getParameter("prefix");
        int maxPredictions;
        if (hasParameter(baseRequest, "maxPredictions")) {
          maxPredictions = Integer.parseInt(baseRequest.getParameter("maxPredictions"));
        } else {
          maxPredictions = Integer.MAX_VALUE;
        }
        ptmRequest = new PTMPredictionRequest(sourceLang, targetLang, source, prefix, maxPredictions);
      }

      if (DEBUG) {
        System.err.println("PTMPredictionRequest: " + gson.toJson(ptmRequest)); 
      }

      List<ScoredCompletion> completions = getCompletions(ptmRequest.source, ptmRequest.prefix);
      List<Prediction> predictions = filterCompletions(completions, ptmRequest.maxPredictions);
      PTMPredictionResponse ptmResponse = new PTMPredictionResponse(ptmRequest.prefix, predictions);
      Type t = new TypeToken<PTMPredictionResponse>() {}.getType();           
      responseString = gson.toJson(ptmResponse, t);
    
    } else if (hasParameter(baseRequest, "ptmUserSelection")) {
      PTMCompletionSelectionRequest ptmRequest = gson.fromJson(baseRequest.getParameter("ptmUserSelection"), PTMCompletionSelectionRequest.class);
      if (DEBUG) {
        System.err.println("PTMCompletionSelectonRequest: " + gson.toJson(ptmRequest));
      }
      //responseString = wrapResponse("ptmUserSelectionResponse", gson.toJson(new PTMStatusOk()));
      responseString = gson.toJson(new PTMStatusOk());      
    
    } else if (hasParameter(baseRequest, "ptmDone")) {
      PTMDoneRequest ptmRequest = gson.fromJson(request.getParameter("ptmDone"), PTMDoneRequest.class);
      if (DEBUG) {
        System.err.println("PTMDoneRequest: " + gson.toJson(ptmRequest));
      }
      System.out.printf("LOG: %s\n", ptmRequest);
      //responseString = wrapResponse("ptmDoneResponse", gson.toJson(new PTMStatusOk()));
      responseString = gson.toJson(new PTMStatusOk());       
    } 

    if (responseString != null) {
      response.setContentType("application/x-javascript;charset=utf-8");     
      response.setStatus(HttpServletResponse.SC_OK);
      baseRequest.setHandled(true);
      response.getWriter().println(responseString);
    } else {
      if (DEBUG) {
        System.err.printf("Attempting to serve resource with path: %s\n", request.getPathInfo());
      }
      String path = request.getPathInfo();
      if ("/".equals(path)) {
        path = DEFAULT_WEB_PAGE;    
      }
      String resourcePath = HTML_RESOURCES_PATH + path;
      InputStream istream;

      try {
        istream = ClassLoader.getSystemClassLoader().getResource(resourcePath).openStream();
      } catch (NullPointerException npe) {
        response.setContentType("text/html;charset=utf-8");     
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        baseRequest.setHandled(true);
        response.getWriter().println("404");
        return;
      }

      BufferedReader reader = new BufferedReader(new InputStreamReader(istream));
      response.setContentType("text/html;charset=utf-8");     
      response.setStatus(HttpServletResponse.SC_OK);
      baseRequest.setHandled(true);
      for (String line = reader.readLine(); line != null; 
      line = reader.readLine()) {
        response.getWriter().println(line);
      }
      return;
    }

    /*
     String requestJSON = baseRequest.getParameter("requestJSON");
     PrefixRequest preq;

     if (requestJSON != null) {
       preq = gson.fromJson(requestJSON, PrefixRequest.class);
     } else if (baseRequest.getParameter(PREFIX_GET_NAME) != null &&
                baseRequest.getParameter(SOURCE_GET_NAME) != null) {
       // try to use get parameters
       String prefix = baseRequest.getParameter(PREFIX_GET_NAME);
       String source = baseRequest.getParameter(SOURCE_GET_NAME);
       preq = new PrefixRequest(prefix, source);
     } else {
       BufferedReader reader = new BufferedReader(new InputStreamReader(
        ClassLoader.getSystemClassLoader()
        .getResource(DEFAULT_WEB_PAGE).openStream()));
       response.setContentType("text/html;charset=utf-8");     
       response.setStatus(HttpServletResponse.SC_OK);
       baseRequest.setHandled(true);
       for (String line = reader.readLine(); line != null; 
            line = reader.readLine()) {
          response.getWriter().println(line);
       }
       return;
     }
     System.err.printf("Source: %s Prefix: %s\n", preq.source, preq.prefix);
     try {
       List<Completion> completions = getCompletions(preq.source, preq.prefix);
       System.err.printf("Completion count: %d", completions.size());
       Type t = new TypeToken<List<Completion>>() {}.getType();
       String jsonOut = gson.toJson(completions, t);

       //response.setContentType("application/json;charset=utf-8");     
       response.setContentType("application/x-javascript;charset=utf-8");     
       response.setStatus(HttpServletResponse.SC_OK);
       baseRequest.setHandled(true);
       //response.getWriter().println("someFunc({foo: 'bar'})");
       response.getWriter().println("someFunc("+jsonOut+");");
     } catch (Exception e) {
       response.setContentType("text/text;charset=utf-8");
       response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
       baseRequest.setHandled(true);
       response.getWriter().println(e);
     }
     */

  }

  public PrefixCompletion(String lmFn, String phrasetableFn, double lmWt, double[] phrTableWts) throws IOException {
    lm = LanguageModels.load(lmFn);
    phr = new FlatPhraseTable<String>(null, null, phrasetableFn, false);
    this.lmWt = lmWt;
    this.phrTableWts = Arrays.copyOf(phrTableWts, phrTableWts.length);
  }

  public PrefixCompletion(PrefixDecoder<String> prefixDecoder) {
    this.prefixDecoder = prefixDecoder;
  }

  List<String> getOOVs(String sourceStr) {
    List<String> OOVs = new ArrayList<String>();
    RawSequence<IString> source = new RawSequence<IString>(IStrings.toIStringArray(sourceStr.split("\\s+")));
    String OOV = "";
    if (phr != null) {
      for (IString token : source) {
        List<TranslationOption<IString>> phraseTranslations = phr.getTranslationOptions(new RawSequence<IString>(new IString[]{token}));
        if (phraseTranslations == null || phraseTranslations.size() == 0) {
          if (!"".equals(OOV)) {
            OOV = OOV + " ";
          }
          OOV = OOV + token.toString();
          if (DEBUG) {
            System.err.printf("'%s' is an OOV\n", token);
          }
        } else if (!"".equals(OOV)){
          OOVs.add(OOV);
          OOV = "";
          if (DEBUG) {
            System.err.printf("Final OOV phrase %s\n", OOV);
          }
        }

        if (DEBUG && phraseTranslations != null) {
          System.err.printf("%d translations for %s\n", phraseTranslations.size(), token);
        }

      }
      if (!"".equals(OOV)) {
        OOVs.add(OOV);
      }
    }
    return OOVs;
  }

  @SuppressWarnings("unchecked")
  List<ScoredCompletion> getCompletions(String sourceStr, String prefixStr) {
    if (DEBUG) {
      System.err.printf("Source str: %s\n", sourceStr);
      System.err.printf("Prefix str: %s\n", prefixStr);
    } 
    RawSequence<IString> source = new RawSequence<IString>(IStrings.toIStringArray(sourceStr.split("\\s+")));
    RawSequence<IString> prefix = new RawSequence<IString>(IStrings.toIStringArray(prefixStr.split("\\s+")));
    if (DEBUG) {
      System.err.printf("Source seq: %s\n", source);
      System.err.printf("Prefix seq: %s\n", prefix);
    }
    
    // TODO(spenceg): This should just return the list of translations, which will be turned into
    // Prediction objects for transmission over the wire.
    List<ScoredCompletion> scoredOpts = null;

    // Agenda-based prefix decoder, which considers the coverage set.
    if (prefixDecoder != null) {
      if (DEBUG) {
        System.err.println("Prefix decoder");
      }
      EnumeratedConstrainedOutputSpace<IString, String> prefixConstraints = 
        new EnumeratedConstrainedOutputSpace<IString, String>(Arrays.asList(prefix), prefixDecoder.getPhraseGenerator().longestForeignPhrase());
      List<RichTranslation<IString, String>> translations = prefixDecoder.nbest(source, 0, prefixConstraints, 
    		  Arrays.asList((Sequence<IString>)prefix), -1);
      if(DEBUG) {
        System.err.printf("n-best list: %s\n", translations);
      }
      
      scoredOpts = new ArrayList<ScoredCompletion>(translations.size());
      String prefixCoverage = null;
      for (RichTranslation<IString,String> translation : translations) {
        if (prefixCoverage == null) {
          prefixCoverage = getPrefixSourceCoverage(translation);
        }
        String srcPhrase = translation.featurizable.foreignPhrase.toString();
        String tgtPhrase = translation.translation.subsequence(prefix.size(), translation.translation.size()).toString();
        String completionCoverage = getCompletionSourceCoverage(translation);
        scoredOpts.add(new ScoredCompletion(prefixCoverage, srcPhrase, tgtPhrase, completionCoverage, translation.score));
      }

    } else {
      scoredOpts = new ArrayList<ScoredCompletion>();
      // Stupid decoder that generates completions independent of the coverage set.
      if (DEBUG) {
        System.err.println("Simple model");
      }
      List<TranslationOption<IString>> possibleCompletions = new LinkedList<TranslationOption<IString>>();
      if (DEBUG) {
        System.err.printf("Source: %s\n", source);
        System.err.printf("source size: %s\n", source.size());
      }
      for (int i = 0; i < source.size(); i++) {      
        for (int j = i+1; j < Math.min(5+i,source.size()); j++) {
          List<TranslationOption<IString>> phraseTranslations = phr.getTranslationOptions(source.subsequence(i, j));
          if (DEBUG) {
            if (phraseTranslations == null) {
              System.err.printf("%s: none\n", source.subsequence(i, j));
            } else {
              System.err.printf("%s: %d\n", source.subsequence(i,j), phraseTranslations.size());
            }
          }
          if (phraseTranslations != null) {
            possibleCompletions.addAll(phraseTranslations);
          }
        }
      }
      if (DEBUG) {
        System.err.printf("possibleCompletions: %s\n", possibleCompletions.size());
      } 
      System.out.printf("Prefix lm in isolation: %e\n", LanguageModels.scoreSequence(lm, prefix));
      for (TranslationOption<IString> opt : possibleCompletions) {
        if (opt.translation.size() == 1 && !opt.translation.get(0).toString().matches("\\w") ) {
          continue;
        }
        Sequence<IString> prefixPlus = Sequences.concatenate(prefix, opt.translation);
        if (DEBUG) {
          System.err.printf("PrefixPlus: %s\n", prefixPlus);
        }
        double lmScore = LanguageModels.scoreSequence(lm,prefixPlus);
        double modelScore = lmScore*lmWt;
        if(DEBUG) {
          System.err.printf("%s lmScore: %e\n", prefixPlus, lmScore); 
        }
        for (int i = 0; i < opt.scores.length; i++) {
          //          System.err.printf(" modelScore[%d]: %e\n", i, opt.scores.length);
          modelScore += opt.scores[i]*(phrTableWts.length > i ? phrTableWts[i] : phrTableWts.length == 0 ? 1 : 0.0);          
        }
        ScoredCompletion completion = new ScoredCompletion(opt.foreign.toString(), opt.foreign.toString(), opt.translation.toString(), "", modelScore);
        scoredOpts.add(completion);        
      }  
    } // End of completion generation

    // Sort the completions according to their scores
    Collections.sort(scoredOpts, completionComparator);
    
    if(DEBUG) {
      System.err.println(scoredOpts);
      if (scoredOpts.size() >= 1) {
        System.err.println("Best guess: " + scoredOpts.get(0));
      }
    }
    return scoredOpts;    
  }
  
  private static class ScoredCompletionComparator implements Comparator<ScoredCompletion> {
    public int compare(ScoredCompletion o1, ScoredCompletion o2) {
      return (int)Math.signum(o2.score-o1.score); 
    }
    public boolean equals(Object obj) {
      return obj == this;
    }
  }
  private static final ScoredCompletionComparator completionComparator = new ScoredCompletionComparator();
  
  
  /**
   * Returns a string representing the source positions covered by the most
   * recent translation option in this hypothesis. For example, if the most
   * recent option covered 2,3,4,10 then this method would return "2-3-4-10".
   * 
   * @param opt The rich hypothesis object
   */
  private static String getCompletionSourceCoverage(RichTranslation<IString, String> opt) {
    CoverageSet coverage = opt.featurizable.option.foreignCoverage;
    StringBuilder sb = new StringBuilder();
    for (int coveredBit : coverage) {
      if (sb.length() > 0) sb.append("-");
      sb.append(coveredBit);
    }
    return sb.toString();
  }

  private static String getPrefixSourceCoverage(RichTranslation<IString, String> opt) {
    StringBuilder sb = new StringBuilder();
    for (Featurizable<IString, String> featurizer = opt.featurizable.prior; 
          featurizer != null; 
          featurizer = featurizer.prior) {
      int offset = featurizer.foreignPosition;
      for (int i = 0; i < featurizer.foreignPhrase.size(); ++i) {
        if (sb.length() > 0) sb.append("-");
        sb.append(String.valueOf(i + offset));
      }
    }
    return sb.toString();
  }

  private static final Pattern discardCompletion = Pattern.compile("[\\p{Punct}|\\s]$");
  private static List<Prediction> filterCompletions(List<ScoredCompletion> completions, int maxPredictions) {
    List<Prediction> predictionList = new ArrayList<Prediction>(completions.size());
    Set<String> uniqueTargetCompletions = new HashSet<String>(completions.size());
    
    for (ScoredCompletion completion : completions) {
      if (predictionList.size() == maxPredictions) {
        break;
      }
      if (uniqueTargetCompletions.contains(completion.tgtPhrase) ||
          discardCompletion.matcher(completion.tgtPhrase).matches() ||
          discardCompletion.matcher(completion.srcPhrase).matches()) {
        continue;
      }
      uniqueTargetCompletions.add(completion.tgtPhrase);
      Prediction prediction = new Prediction(completion.srcPrefCoverage,
          completion.tgtPhrase, completion.srcCoverage);
      predictionList.add(prediction);
    }
    return predictionList;
  }
  
  public static void usage() {
    System.err.println("Usage:\n\tjava ...PrefixCompletion -simple (lm) (phrase table) (lm wt) (phr table wt1) (phr table wt2) ...");
    System.err.println("\nOr:\n\tjava ...PrefixCompletion -phrasal phrasal_ini");
  }

  @SuppressWarnings("unchecked")
  public static void main(String[] args) throws Exception {
    PrefixCompletion pc = null;

    if (args.length < 2) {
      usage();
      System.exit(-1);
    } else if ("-simple".equals(args[0])) {  
      double lmWt = 1;
      double phrTableWts[] = new double[0]; 

      if (args.length > 3) {
        lmWt = Double.parseDouble(args[3]);
      }

      if (args.length > 4) {
        phrTableWts = new double[args.length-4];
        for (int i = 4; i < args.length; i++) {
          phrTableWts[i-4] = Double.parseDouble(args[i]);
        }
      }      
      pc = new PrefixCompletion(args[1],args[2], lmWt, phrTableWts);  
    } else if ("-phrasal".equals(args[0])) {
      String[] fields = args[1].split(":");
      String phrasalModel = fields[0];
      String berkeleyModel = fields[1];
      Map<String, List<String>> config = Phrasal.readConfig(phrasalModel);      
      Phrasal.initStaticMembers(config);
      Phrasal p = new Phrasal(config);
      FlatPhraseTable.lockIndex();      
      AbstractInferer infererModel = (AbstractInferer)p.inferers.get(0);      
      PrefixDecoder<String> prefixDecoder = new PrefixDecoder<String>(infererModel,berkeleyModel);      
      pc = new PrefixCompletion(prefixDecoder);            
    } else {
      usage();
      System.exit(-1);
    } 

    Server server = new Server(8081);
    server.setHandler(pc);
    server.start();
    server.join();
    /*
    BufferedReader stdinReader = new BufferedReader(new InputStreamReader(System.in));
    System.out.println("Ready");
    for (String line = stdinReader.readLine(); line != null; line = stdinReader.readLine()) {
      String[] fields = line.split("\\|\\|\\|");
      List<Pair<Double, TranslationOption<IString>>> scoredOpts = pc.getCompletions(fields[0], fields[1]);

      System.out.printf("Completion options:\n");
      for (int i = 0; i < 5; i++) {
        System.out.printf("%s (%e)\n", scoredOpts.get(scoredOpts.size()-1-i).second.translation, scoredOpts.get(scoredOpts.size()-1-i).first);
      }

    }
     */
  }
}


abstract class PTMBaseRequest {
  public final String sourceLang;
  public final String targetLang;
  public final String source;
  public PTMBaseRequest(String sourceLang, String targetLang, String source) {
    this.sourceLang = sourceLang;
    this.targetLang = targetLang;
    this.source = source;
  }
}

class PTMInitRequest extends PTMBaseRequest {
  public PTMInitRequest(String sourceLang, String targetLang, String source) {
    super(sourceLang, targetLang, source);
  }
}

class PTMOOVPhrasePair {
  public final String sourcePhrase;
  public final String targetPhrase;
  public PTMOOVPhrasePair(String sourcePhrase, String targetPhrase) {
    this.sourcePhrase = sourcePhrase;
    this.targetPhrase = targetPhrase;
  }
}

class PTMOOVRequest extends PTMBaseRequest {
  final List<PTMOOVPhrasePair> OOVPhrasePairs;
  public PTMOOVRequest(String sourceLang, String targetLang, String source, 
      List<PTMOOVPhrasePair> OOVPhrasePairs) {
    super(sourceLang, targetLang, source);
    this.OOVPhrasePairs = new ArrayList<PTMOOVPhrasePair>(OOVPhrasePairs);
  }
}

class PTMPredictionRequest extends PTMBaseRequest {
  public final String prefix;
  public int maxPredictions;
  public PTMPredictionRequest(String sourceLang, String targetLang,
      String source, String prefix, int maxPredictions) {
    super(sourceLang, targetLang, source);
    this.prefix = prefix;
    this.maxPredictions = maxPredictions;
  }
}

class PTMCompletionSelectionRequest extends PTMBaseRequest {
  public final String prefix;
  public final String completion;
  public PTMCompletionSelectionRequest(String sourceLang, String targetLang,
      String source, String prefix, String completion) {
    super(sourceLang, targetLang, source);
    this.prefix = prefix;
    this.completion = completion;
  }

}

class PTMDoneRequest extends PTMBaseRequest {
  public final String finishedTarget;
  public final int numKeyStrokes;

  public PTMDoneRequest(String sourceLang, String targetLang, String source, String finishedTarget, int numKeyStrokes) {
    super(sourceLang, targetLang, source);
    this.finishedTarget = finishedTarget;
    this.numKeyStrokes  = numKeyStrokes;
  }

  @Override 
  public String toString() {
    return String.format("ptmDone: sourceLang: %s targetLang: %s source: %s finishedTarget: %s numKeyStrokes: %d",
        sourceLang, targetLang, source, finishedTarget, numKeyStrokes);
  }
}

class PTMStatusOk {
  public final String status = "ok";
}

class PTMOOVResponse {
  final List<String> OOVs;
  public PTMOOVResponse(List<String> OOVs) {
    this.OOVs = new ArrayList<String>(OOVs);
  }
}

class PTMPredictionResponse {
  final String prefix;
  final List<Prediction> predictions;
  public PTMPredictionResponse(String prefix, List<Prediction> predictions) {
    this.prefix = prefix;
    this.predictions = predictions;
  }
}

class PTMError {
  public final String errorMsg;
  public PTMError(String errorMsg) {
    this.errorMsg = errorMsg;
  }
}
