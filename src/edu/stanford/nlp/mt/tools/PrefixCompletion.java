package edu.stanford.nlp.mt.tools;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import java.io.IOException;
import java.lang.reflect.Type;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import scala.collection.mutable.HashMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.base.ARPALanguageModel;
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
  public static final String DEFAULT_WEB_PAGE = "edu/stanford/nlp/mt/resources/prefix_default.html";
  PrefixDecoder<IString,String> prefixDecoder;
  LanguageModel<IString> lm;
  FlatPhraseTable<String> phr;
  double lmWt;
  double[] phrTableWts;
  
  public static final String PREFIX_GET_NAME = "prefix";
  public static final String SOURCE_GET_NAME = "source";
  
  class PrefixRequest {
    String prefix;
    String source;
    
    public PrefixRequest(String prefix, String source) {
      this.prefix = prefix;
      this.source = source;
    }
  }
  
  private boolean hasParameter(Request baseRequest, String paramName) {
    String param = baseRequest.getParameter(paramName);
    return param != null & !"".equals(param);
  }
  
  private String wrapResponse(String functionName, String json) {
    return String.format("%s(%s);",functionName,json);
  }
  
  public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
  {
     String responseString = null;
     
     Gson gson = new Gson();
     if (hasParameter(baseRequest, "ptmInit")) {
       List<String> OOVs = new ArrayList<String>();
       // TODO get OOVs
       // for now just add some random oovs
       String[] oovStrings = {"xyx", "qzy", "alw", "idj"};
       for (String oov : oovStrings) {
         OOVs.add(oov);
       }
       PTMOOVResponse ptmResponse = new PTMOOVResponse(OOVs);
       Type t = new TypeToken<PTMOOVResponse>() {}.getType();           
       responseString = wrapResponse("ptmInitResponse", gson.toJson(ptmResponse, t));
     } else if (hasParameter(baseRequest, "ptmOOV")) {
       responseString = wrapResponse("ptmOOVResponse", gson.toJson(new PTMStatusOk()));       
     } else if (hasParameter(baseRequest, "ptmPredict")) {       
       // TODO
       List<PTMPrediction> predictions = new ArrayList<PTMPrediction>();
       // for now just add some random predictions
       String[] predictionStrings = {"The", "red", "cat", "eat", "the", "blue", "ball", "and", "the", "orange", "banana"};
       for (String pred : predictionStrings) {
         predictions.add(new PTMPrediction("This is a prefix", pred));
       }
       PTMPredictionResponse ptmResponse = new PTMPredictionResponse(predictions);
       Type t = new TypeToken<PTMPredictionResponse>() {}.getType();           
       responseString = wrapResponse("ptmPredictResponse", gson.toJson(ptmResponse, t));
     } else if (hasParameter(baseRequest, "ptmUserSelection")) {
       responseString = wrapResponse("ptmUserSelectionResponse", gson.toJson(new PTMStatusOk()));
     } else if (hasParameter(baseRequest, "ptmDone")) {
       responseString = wrapResponse("ptmDoneResponse", gson.toJson(new PTMStatusOk()));
     } 
     
     if (responseString != null) {
       response.setContentType("application/x-javascript;charset=utf-8");     
       response.setStatus(HttpServletResponse.SC_OK);
       baseRequest.setHandled(true);
       response.getWriter().println(responseString);
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
    lm = ARPALanguageModel.load(lmFn);
    phr = new FlatPhraseTable<String>(null, null, phrasetableFn, false);
    this.lmWt = lmWt;
    this.phrTableWts = Arrays.copyOf(phrTableWts, phrTableWts.length);
  }
  
  public PrefixCompletion(PrefixDecoder<IString,String> prefixDecoder) {
    this.prefixDecoder = prefixDecoder;
  }
  
  @SuppressWarnings("unchecked")
  List<Completion> getCompletions(String sourceStr, String prefixStr) {
    RawSequence<IString> source = new RawSequence<IString>(IStrings.toIStringArray(sourceStr.split("\\s+")));
    RawSequence<IString> prefix = new RawSequence<IString>(IStrings.toIStringArray(prefixStr.split("\\s+")));
    
    List<Completion> scoredOpts = new ArrayList<Completion>();
    if (prefixDecoder != null) {
      EnumeratedConstrainedOutputSpace<IString, String> prefixConstraints = 
        new EnumeratedConstrainedOutputSpace<IString, String>(Arrays.asList(prefix), prefixDecoder.getPhraseGenerator().longestForeignPhrase());
      List<RichTranslation<IString, String>> translations = prefixDecoder.nbest(source, 0, prefixConstraints, null, -1);
      System.err.printf("n-best list: %s\n", translations);
      for (RichTranslation<IString,String> translation : translations) {
        scoredOpts.add(new Completion("TODO", translation.translation.toString(), translation.score));
      }
    } else {
      List<TranslationOption<IString>> possibleCompletions = new LinkedList<TranslationOption<IString>>();
      for (int i = 0; i < source.size(); i++) {      
        for (int j = i+1; j < Math.min(phr.longestForeignPhrase()+i,source.size()); j++) {
          List<TranslationOption<IString>> phraseTranslations = phr.getTranslationOptions(source.subsequence(i, j));
          if (phraseTranslations != null) {
             possibleCompletions.addAll(phraseTranslations);
          }
        }
      }
      
      for (TranslationOption<IString> opt : possibleCompletions) {
        if (opt.translation.size() == 1 && !opt.translation.get(0).toString().matches("\\w") ) {
          continue;
        }
        Sequence<IString> prefixPlus = Sequences.concatenate(prefix, opt.translation);
        double lmScore = LanguageModels.scoreSequence(lm,prefixPlus);
        double modelScore = lmScore*lmWt;
        System.err.printf("%s lmScore: %e\n", prefixPlus, lmScore);        
        for (int i = 0; i < opt.scores.length; i++) {
          System.err.printf(" modelScore[%d]: %e\n", i, opt.scores.length);
          modelScore += opt.scores[i]*(phrTableWts.length > i ? phrTableWts[i] : phrTableWts.length == 0 ? 1 : 0.0);          
        }
        Completion completion = new Completion(opt.foreign.toString(), opt.translation.toString(), modelScore);
        scoredOpts.add(completion);        
      }  
    }            
        
    Collections.sort(scoredOpts, new Comparator<Completion>() {
      public int compare(Completion o1, Completion o2) {
        return (int)Math.signum(o2.score-o1.score); 
      }
      public boolean equals(Object obj) {
        return obj == this;
      }
    });
    System.err.println(scoredOpts);
    if (scoredOpts.size() >= 1) {
      System.err.println("Best guess: " + scoredOpts.get(0));
    }
    return scoredOpts;    
  }
  
  public static void usage() {
    System.err.println("Usage:\n\tjava ...PrefixCompletion -simple (lm) (phrase table) (lm wt) (phr table wt1) (phr table wt2) ...");
    System.err.println("\nOr:\n\tjava ...PrefixCompletion -phrasal phrasal_ini");
  }
  
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
      Map<String, List<String>> config = Phrasal.readConfig(args[1]);      
      Phrasal.initStaticMembers(config);
      Phrasal p = new Phrasal(config);
      FlatPhraseTable.lockIndex();
      @SuppressWarnings("rawtypes")
      AbstractInferer infererModel = (AbstractInferer)p.inferers.get(0);
      @SuppressWarnings("unchecked")
      PrefixDecoder<IString,String> prefixDecoder = new PrefixDecoder<IString,String>(infererModel);      
      pc = new PrefixCompletion(prefixDecoder);            
    } else {
      usage();
      System.exit(-1);
    } 
  
    Server server = new Server(8080);
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

class Completion {
  public final String sourcePhrase;
  public final String targetPhrase;
  public final double score;
  
  public Completion(String sourcePhrase, String targetPhrase, double score) {
    this.sourcePhrase = sourcePhrase;
    this.targetPhrase = targetPhrase;
    this.score = score;
  }
  
  public String toString() {
    return String.format("Source material: %s, Translation: %s, Model Score: %e", sourcePhrase,targetPhrase,score);
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

class PTMPrediction {
  final String prefix;
  final String completion;
  
  public PTMPrediction(String prefix, String completion) {
    this.prefix = prefix;
    this.completion = completion;
  }
}

class PTMPredictionResponse {
  final List<PTMPrediction> predictions;
  public PTMPredictionResponse(List<PTMPrediction> predictions) {
    this.predictions = new ArrayList<PTMPrediction>(predictions);
  }
}

class PTMError {
  public final String errorMsg;
  public PTMError(String errorMsg) {
    this.errorMsg = errorMsg;
  }
}