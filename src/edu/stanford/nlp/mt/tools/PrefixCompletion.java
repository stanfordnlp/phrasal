package edu.stanford.nlp.mt.tools;

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


//import java.io.BufferedReader;
//import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.base.ARPALanguageModel;
import edu.stanford.nlp.mt.base.FlatPhraseTable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.LanguageModel;
import edu.stanford.nlp.mt.base.LanguageModels;
import edu.stanford.nlp.mt.base.RawSequence;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.Sequences;
import edu.stanford.nlp.mt.base.TranslationOption;
import edu.stanford.nlp.util.Pair;

/**
 * Prefix completion prototype
 * 
 * @author daniel
 *
 */

public class PrefixCompletion extends AbstractHandler {
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
  
  public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
  {
     String requestJSON = baseRequest.getParameter("requestJSON");
     PrefixRequest preq;
     Gson gson = new Gson();
     if (requestJSON != null) {
       preq = gson.fromJson(requestJSON, PrefixRequest.class);
     } else {
       // try to use get parameters
       String prefix = baseRequest.getParameter(PREFIX_GET_NAME);
       String source = baseRequest.getParameter(SOURCE_GET_NAME);
       preq = new PrefixRequest(prefix, source);
     }
     System.err.printf("Source: %s Prefix: %s\n", preq.source, preq.prefix);
     try {
       List<Pair<Double, TranslationOption<IString>>> completions = getCompletions(preq.source, preq.prefix);
       Type t = new TypeToken<List<Pair<Double, TranslationOption<IString>>>>() {}.getType();
       String jsonOut = gson.toJson(completions, t);
       
       response.setContentType("application/json;charset=utf-8");     
       response.setStatus(HttpServletResponse.SC_OK);
       baseRequest.setHandled(true);
       response.getWriter().println(jsonOut);
     } catch (Exception e) {
       response.setContentType("text/text;charset=utf-8");
       response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
       baseRequest.setHandled(true);
       response.getWriter().println(e);
     }
  }
  
  public PrefixCompletion(String lmFn, String phrasetableFn, double lmWt, double[] phrTableWts) throws IOException {
    lm = ARPALanguageModel.load(lmFn);
    phr = new FlatPhraseTable<String>(null, null, phrasetableFn, false);
    this.lmWt = lmWt;
    this.phrTableWts = Arrays.copyOf(phrTableWts, phrTableWts.length);
  }
  
  List<Pair<Double, TranslationOption<IString>>> getCompletions(String sourceStr, String prefixStr) {
    RawSequence<IString> source = new RawSequence<IString>(IStrings.toIStringArray(sourceStr.split("\\s+")));
    RawSequence<IString> prefix = new RawSequence<IString>(IStrings.toIStringArray(prefixStr.split("\\s+")));
    List<TranslationOption<IString>> possibleCompletions = new LinkedList<TranslationOption<IString>>();
    for (int i = 0; i < source.size(); i++) {      
      for (int j = i+1; j < Math.min(phr.longestForeignPhrase()+i,source.size()); j++) {
        List<TranslationOption<IString>> phraseTranslations = phr.getTranslationOptions(source.subsequence(i, j));
        if (phraseTranslations != null) {
           possibleCompletions.addAll(phraseTranslations);
        }
      }
    }        
    List<Pair<Double, TranslationOption<IString>>> scoredOpts = new ArrayList<Pair<Double, TranslationOption<IString>>>();
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
      scoredOpts.add(new Pair<Double,TranslationOption<IString>>(modelScore, opt));        
    }
    Collections.sort(scoredOpts);
    System.err.println(scoredOpts);
    return scoredOpts;    
  }
  
  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.err.println("Usage:\n\tjava ...PrefixCompletion (lm) (phrase table) (lm wt) (phr table wt1) (phr table wt2) ...");
      System.exit(-1);
    }
    double lmWt = 1;
    double phrTableWts[] = new double[0]; 
    
    if (args.length > 2) {
      lmWt = Double.parseDouble(args[2]);
    }
    if (args.length > 3) {
      phrTableWts = new double[args.length-3];
      for (int i = 3; i < args.length; i++) {
        phrTableWts[i-3] = Double.parseDouble(args[i]);
      }
    }
    
    
    PrefixCompletion pc = new PrefixCompletion(args[0],args[1], lmWt, phrTableWts);       
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
