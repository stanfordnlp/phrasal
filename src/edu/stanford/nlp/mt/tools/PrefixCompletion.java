package edu.stanford.nlp.mt.tools;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
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
import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.util.Pair;

/**
 * Prefix completion prototype
 * 
 * @author daniel
 *
 */
public class PrefixCompletion {
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
    
    LanguageModel<IString> lm = ARPALanguageModel.load(args[0]);
    FlatPhraseTable<String> phr = new FlatPhraseTable<String>(null, null, args[1], false);
    BufferedReader stdinReader = new BufferedReader(new InputStreamReader(System.in));
    System.out.println("Ready");
    for (String line = stdinReader.readLine(); line != null; line = stdinReader.readLine()) {
      String[] fields = line.split("\\|\\|\\|");
      RawSequence<IString> source = new RawSequence<IString>(IStrings.toIStringArray(fields[0].split("\\s")));
      RawSequence<IString> prefix = new RawSequence<IString>(IStrings.toIStringArray(fields[1].split("\\s")));
      List<TranslationOption<IString>> possibleCompletions = new LinkedList<TranslationOption<IString>>();
      for (int i = 0; i < source.size(); i++) {
        for (int j = i+1; j < Math.min(phr.longestForeignPhrase()+i,source.size()); j++) {
          possibleCompletions.addAll(phr.getTranslationOptions(source.subsequence(i, j)));
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
      System.out.printf("Completion options:\n");
      for (int i = 0; i < 5; i++) {
        System.out.printf("%s (%e)\n", scoredOpts.get(scoredOpts.size()-1-i).second.translation, scoredOpts.get(scoredOpts.size()-1-i).first);
      }
      
    }
  }
  
  
}
