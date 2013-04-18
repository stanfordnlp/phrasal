package edu.stanford.nlp.mt.tools.aPTM;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.FlatPhraseTable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.RawSequence;
import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.decoder.inferer.AbstractInferer;
import edu.stanford.nlp.mt.decoder.inferer.impl.PrefixDecoder;
import edu.stanford.nlp.mt.decoder.util.EnumeratedConstrainedOutputSpace;
import edu.stanford.nlp.mt.tools.aPTM.Messages.MessageType;
import edu.stanford.nlp.mt.tools.aPTM.Messages.PTMBaseRequest;
import edu.stanford.nlp.mt.tools.aPTM.Messages.PTMDoneRequest;
import edu.stanford.nlp.mt.tools.aPTM.Messages.PTMInitRequest;
import edu.stanford.nlp.mt.tools.aPTM.Messages.PTMOOVRequest;
import edu.stanford.nlp.mt.tools.aPTM.Messages.PTMOOVResponse;
import edu.stanford.nlp.mt.tools.aPTM.Messages.PTMPredictionRequest;
import edu.stanford.nlp.mt.tools.aPTM.Messages.PTMPredictionResponse;
import edu.stanford.nlp.mt.tools.aPTM.Messages.PTMStatusOk;
import edu.stanford.nlp.util.Pair;

/**
 * Servlet that loads Phrasal as a private member.
 * <p>
 * TODO(spenceg): Add logger instead of printing to stderr/stdout. Should be a very fast logger. Unified logging.
 * <p>
 * TODO(spenceg): I'm sure that this is not the right thing to do for even moderate loads.
 * But for now, just load phrasal in the servelt since we're using jetty embedded.
 * 
 * @author Spence Green
 *
 */
public class PhrasalUnifiedServlet extends HttpServlet {

  private static final long serialVersionUID = -2229782317949182871L;

  public static final String DEBUG_PROPERTY = PhrasalUnifiedServlet.class.getSimpleName();
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));
  
  private final PrefixDecoder<String> decoder;
  
  private final Gson gson = new Gson();
  
  public PhrasalUnifiedServlet(String iniFileName, String wordAlignmentModel) {
    decoder = initializePhrasal(iniFileName, wordAlignmentModel);
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private static PrefixDecoder<String> initializePhrasal(String iniFileName, String wordAlignmentModel) {
    Map<String, List<String>> config = null;
    try {
      config = Phrasal.readConfig(iniFileName);
      Phrasal.initStaticMembers(config);
      Phrasal p = new Phrasal(config);
      FlatPhraseTable.lockIndex();      
      AbstractInferer infererModel = (AbstractInferer) p.inferers.get(0);      
      PrefixDecoder<String> prefixDecoder = new PrefixDecoder<String>(infererModel, wordAlignmentModel);
      return prefixDecoder;
      
    } catch (IOException e) {
      e.printStackTrace();
    } catch (IllegalArgumentException e) {
      e.printStackTrace();
    } catch (SecurityException e) {
      e.printStackTrace();
    } catch (InstantiationException e) {
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
    throw new RuntimeException("Could not start " + PhrasalUnifiedServlet.class.getName());
  }
  
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
  {
    String responseString = null;
    Pair<MessageType,PTMBaseRequest> message = Messages.requestToMessage(request);
    MessageType messageType = message.first();
    PTMBaseRequest baseRequest = message.second();

    if(DEBUG) {
      System.err.println("Received request: " + messageType.toString());
    }
    
    if (messageType == MessageType.INIT) {
      PTMInitRequest ptmRequest = (PTMInitRequest) baseRequest;

      List<String> oovStringsList = getOOVs(ptmRequest.source);

      PTMOOVResponse ptmResponse = new PTMOOVResponse(oovStringsList);
      Type t = new TypeToken<PTMOOVResponse>() {}.getType();           
      responseString = gson.toJson(ptmResponse, t);
    } else if (messageType == MessageType.SEND_OOV) {
      PTMOOVRequest ptmRequest = (PTMOOVRequest) baseRequest;
      if (DEBUG) {
        System.err.println("PTMOOVRequest: " + gson.toJson(ptmRequest));
      }
      responseString = gson.toJson(new PTMStatusOk());  
    
    } else if (messageType == MessageType.PREDICTION) {       
      PTMPredictionRequest ptmRequest = (PTMPredictionRequest) baseRequest;
      if (DEBUG) {
        System.err.println("PTMPredictionRequest: " + gson.toJson(ptmRequest)); 
      }

      List<ScoredCompletion> completions = getCompletions(ptmRequest.source, ptmRequest.prefix);
      List<Prediction> predictions = filterCompletions(completions, ptmRequest.maxPredictions);
      PTMPredictionResponse ptmResponse = new PTMPredictionResponse(ptmRequest.prefix, predictions);
      Type t = new TypeToken<PTMPredictionResponse>() {}.getType();           
      responseString = gson.toJson(ptmResponse, t);
    
    } 
//    else if (hasParameter(request, "ptmUserSelection")) {
//      PTMCompletionSelectionRequest ptmRequest = gson.fromJson(request.getParameter("ptmUserSelection"), PTMCompletionSelectionRequest.class);
//      if (DEBUG) {
//        System.err.println("PTMCompletionSelectonRequest: " + gson.toJson(ptmRequest));
//      }
//      //responseString = wrapResponse("ptmUserSelectionResponse", gson.toJson(new PTMStatusOk()));
//      responseString = gson.toJson(new PTMStatusOk());      
//    
//    } 
    else if (messageType == MessageType.DONE) {
      PTMDoneRequest ptmRequest = (PTMDoneRequest) baseRequest;
      if (DEBUG) {
        System.err.println("PTMDoneRequest: " + gson.toJson(ptmRequest));
      }
      System.out.printf("LOG: %s\n", ptmRequest);
      //responseString = wrapResponse("ptmDoneResponse", gson.toJson(new PTMStatusOk()));
      responseString = gson.toJson(new PTMStatusOk());       
    } 

    RequestUtils.writeJavascriptResponse(response, responseString);
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
    if (decoder != null) {
      if (DEBUG) {
        System.err.println("Prefix decoder");
      }
      EnumeratedConstrainedOutputSpace<IString, String> prefixConstraints = 
        new EnumeratedConstrainedOutputSpace<IString, String>(Arrays.asList(prefix), decoder.getPhraseGenerator().longestSourcePhrase());
      List<RichTranslation<IString, String>> translations = decoder.nbest(source, 0, prefixConstraints, null, -1);
      if(DEBUG) {
        System.err.printf("n-best list: %s\n", translations);
      }
      
      scoredOpts = new ArrayList<ScoredCompletion>(translations.size());
      String prefixCoverage = null;
      for (RichTranslation<IString,String> translation : translations) {
        if (prefixCoverage == null) {
          prefixCoverage = getPrefixSourceCoverage(translation);
        }
        String srcPhrase = translation.featurizable.sourcePhrase.toString();
        String tgtPhrase = translation.translation.subsequence(prefix.size(), translation.translation.size()).toString();
        String completionCoverage = getCompletionSourceCoverage(translation);
        scoredOpts.add(new ScoredCompletion(prefixCoverage, srcPhrase, tgtPhrase, completionCoverage, translation.score));
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
  
  
  List<String> getOOVs(String sourceStr) {
    List<String> OOVs = new ArrayList<String>();
    OOVs.add("tets1");
    OOVs.add("test2");
    // TODO(spenceg): This should be a lookup into the phrase table...or something.
    return OOVs;
  }
  
  /**
   * Returns a string representing the source positions covered by the most
   * recent translation option in this hypothesis. For example, if the most
   * recent option covered 2,3,4,10 then this method would return "2-3-4-10".
   * 
   * @param opt The rich hypothesis object
   */
  private static String getCompletionSourceCoverage(RichTranslation<IString, String> opt) {
    CoverageSet coverage = opt.featurizable.option.sourceCoverage;
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
      int offset = featurizer.sourcePosition;
      for (int i = 0; i < featurizer.sourcePhrase.size(); ++i) {
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
}
