package edu.stanford.nlp.mt.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;


import edu.stanford.nlp.mt.base.FlatPhraseTable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.RawSequence;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.TranslationOption;
import edu.stanford.nlp.mt.base.FlatPhraseTable.IntArrayTranslationOption;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

public class MergePhraseTableEntries {

  static final boolean DEBUG = false;

  public static void main(String[] args) throws IOException {
    if (args.length != 2) {
      System.err.println("Usage:\n\t...MergePhraseTableEntries (phrasetable) (merge threshold)");
      System.exit(-1);
    }
    
    String phraseTable = args[0];
    double threshold = Double.parseDouble(args[1]);
    
    FlatPhraseTable.createIndex(false);
    System.err.printf("Loading phrase table\n");
    FlatPhraseTable<String> ppt = new FlatPhraseTable<String>(phraseTable);
    System.err.printf("Loading reverse phrase table\n");
    FlatPhraseTable<String> pptr = new FlatPhraseTable<String>(phraseTable, true);
    
    for (int pi=0; pi<ppt.translations.size(); ++pi) { // For each input phrase:
      List<IntArrayTranslationOption> iOpts = ppt.translations.get(pi);
      int[] foreignInts = FlatPhraseTable.foreignIndex.get(pi);
      RawSequence<IString> sPhrase = new RawSequence<IString>(IStrings.toIStringArray(foreignInts));
      Set<Sequence<IString>> mergeCandidates = new HashSet<Sequence<IString>>();
      
      for (IntArrayTranslationOption iOpt : iOpts) {
        RawSequence<IString> tPhrase = new RawSequence<IString>(
            iOpt.translation, IString.identityIndex());
        List<TranslationOption<IString>> rOpts = pptr.getTranslationOptions(tPhrase);
        for (TranslationOption<IString> rOpt : rOpts) {
          mergeCandidates.add(rOpt.target);
        }
      }
      if (DEBUG) {
        System.err.printf("Source Phrase: %s\n", sPhrase);
        for (Sequence<IString> mergeCandidate : mergeCandidates) {
          System.err.printf("\t%s\n", mergeCandidate);
        }
      }
      List<TranslationOption<IString>> opts = ppt.getTranslationOptions(sPhrase);
      Counter<String> piVector = toCounter(opts);
      List<Counter<String>> sumListCounter = toListCounters(opts);
      Set<Sequence<IString>> mergeSet = new HashSet<Sequence<IString>>();
      if (DEBUG) {
        System.err.println("orig: ");
        System.err.println(sumListCounter);
      }
      for (Sequence<IString> mergeCandidate : mergeCandidates) { 
        if (mergeCandidate.equals(sPhrase)) continue;
        List<TranslationOption<IString>> candidateOpts = ppt.getTranslationOptions(mergeCandidate);
        Counter<String> mcVector = toCounter(candidateOpts);
        double cosine = Counters.cosine(piVector, mcVector); 
        
        if (cosine > threshold) {
          if (DEBUG) {
            System.err.printf("Merging: %s and %s cosine: %e\n", sPhrase, mergeCandidate, cosine);
          }
          mergeSet.add(mergeCandidate);
          addListCounter(sumListCounter, toListCounters(candidateOpts));
        }
      }
      if (DEBUG) {
        System.err.println("summed: ");
        System.err.println(sumListCounter);
        System.err.println("mergeSet.size()+1: " + (mergeSet.size()+1));
      }
      for (Counter<String> c : sumListCounter) {
        Counters.divideInPlace(c, mergeSet.size()+1);
      }
      if (DEBUG) {
        System.err.printf("Final Merge Set for %s: %s\n", sPhrase, mergeSet);
      }
      for (String tPhrase : new TreeSet<String>(sumListCounter.get(0).keySet())) {
         System.out.printf("%s ||| %s |||", sPhrase, tPhrase);
         for (Counter<String> c : sumListCounter) {
            double p = c.getCount(tPhrase);
            if (p > 0) {
              System.out.printf(" %f", p);
            } else {
              System.out.printf(" 2.718");
            }
         }
         System.out.println();
      }
    }
  }
  
  static public void addListCounter(List<Counter<String>> target, List<Counter<String>> source) {
    for (int i = 0; i < target.size(); i++) {
      target.get(i).addAll(source.get(i));
    }
  }
  
  static public List<Counter<String>> toListCounters(List<TranslationOption<IString>> opts) {
    List<Counter<String>> listCounters = new ArrayList<Counter<String>>(opts.get(0).scores.length);
    for (int i = 0; i < opts.get(0).scores.length; i++) {
      listCounters.add(new ClassicCounter<String>());
    }
    for (TranslationOption<IString> opt : opts) {
      for (int i = 0; i < opt.scores.length; i++) {
        listCounters.get(i).setCount(opt.target.toString(), 
            (Math.abs(Math.exp(opt.scores[i]) - 2.718) < 0.01 ? -1 : Math.exp(opt.scores[i])));
      }
    }
    return listCounters;
  }
  static public Counter<String> toCounter(List<TranslationOption<IString>> opts) {
     ClassicCounter<String> counter = new ClassicCounter<String>();
     for (TranslationOption<IString> opt : opts) {
        counter.setCount(opt.target.toString(), Math.exp(opt.scores[0])); 
     }
     return counter;
  }
}
