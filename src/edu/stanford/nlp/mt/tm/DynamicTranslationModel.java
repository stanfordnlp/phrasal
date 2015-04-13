package edu.stanford.nlp.mt.tm;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import edu.stanford.nlp.mt.decoder.feat.Featurizer;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.util.RuleGrid;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.ParallelSuffixArray;
import edu.stanford.nlp.mt.util.ParallelSuffixArray.SentenceSample;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.util.TranslationModelIndex;

/**
 * 
 * See here for lex re-ordering: https://github.com/moses-smt/mosesdecoder/commit/51824355f9f469c186d5376218e3396b92652617
 * 
 * @author Spence Green
 *
 */
public class DynamicTranslationModel<FV> implements TranslationModel<IString,FV>,Serializable {

  /**
   * TODO(spenceg) Implement kryo
   */
  private static final long serialVersionUID = 5876435802959430120L;
  
  private static final transient int DEFAULT_LONGEST_PHRASE = 7;
  private static final transient int DEFAULT_SAMPLE_SIZE = 100;
  
  public static enum FeatureTemplate {DENSE, DENSE_EXT, DENSE_EXT_LEX};
  
  public ParallelSuffixArray sa;
  
  private transient int longestSourcePhrase = DEFAULT_LONGEST_PHRASE;
  private transient int longestTargetPhrase = DEFAULT_LONGEST_PHRASE;
  private transient FeatureTemplate featureTemplate = FeatureTemplate.DENSE;
  private transient Featurizer<IString, FV> featurizer;
  private transient boolean isSystemIndex = false;
  private transient int sampleSize = DEFAULT_SAMPLE_SIZE;
  
  public DynamicTranslationModel(ParallelSuffixArray suffixArray) {
    this.sa = suffixArray;
    
    // TODO(spenceg): Setup caches
  }

  public void setFeatureTemplate(FeatureTemplate t) {
    featureTemplate = t;
  }
  
  public void setLongestSourcePhrase(int dim) {
    longestSourcePhrase = dim;
  }
  
  public void setLongestTargetPhrase(int dim) {
    longestTargetPhrase = dim;
  }
  
  public void setIsSystemIndex(boolean b) {
    this.isSystemIndex = b;
  }
  
  public void setSampleSize(int k) {
    this.sampleSize = k;
  }
  
  @Override
  public int longestSourcePhrase() { return longestSourcePhrase; }

  @Override
  public int longestTargetPhrase() { return longestTargetPhrase; }
  
  @Override
  public void setFeaturizer(RuleFeaturizer<IString, FV> featurizer) {
    this.featurizer = featurizer;
  }

  @Override
  public List<String> getFeatureNames() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getName() {
    // TODO Auto-generated method stub
    return null;
  }
  
  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
  
  @Override
  public List<ConcreteRule<IString, FV>> getRules(Sequence<IString> source,
      InputProperties sourceInputProperties, List<Sequence<IString>> targets,
      int sourceInputId, Scorer<FV> scorer) {
    if (source == null || source.size() == 0) return new ArrayList<>(0);
    List<ConcreteRule<IString,FV>> concreteRules = new ArrayList<>(source.size() * source.size() * 100);
    int longestSourcePhrase = this.longestSourcePhrase();
    if (longestSourcePhrase < 0)
      longestSourcePhrase = -longestSourcePhrase;
    
    // TODO(spenceg) Optimizations 
    // * Filtering of higher-order n-grams here
    // * Caching of frequent rules
    int[] sourceInts = isSystemIndex ? Sequences.toIntArray(source) : 
      Sequences.toIntArray(source, this.sa.getIndex());
    BitSet misses = new BitSet(source.size());
    for (int len = 1; len <= longestSourcePhrase; len++) {
      // TODO(spenceg): Parallel rule extraction
      BitSet newMisses = new BitSet();
      for (int i = 0, limit = source.size() - len; i < limit; ++i) {
        final int j = i + len;

        // Check for misses
        int nextMiss = misses.nextSetBit(i);
        if (nextMiss < j) {
          newMisses.set(i, j);
          continue;
        }

        // Sample from the suffix array
        int[] sourcePhrase = Arrays.copyOfRange(sourceInts, i, j);
        List<SentenceSample> samples = sa.sample(sourcePhrase, true, sampleSize);

        // Extract rules
        for (SentenceSample s : samples) {
          
        }
        // Collect counts
      }
      misses = newMisses;
      
//      List<SentenceSample> rules = 
//          IntStream.rangeClosed(0, source.size() - len).parallel().flatMap(i -> {
     
      
      for (int i = 0, sz = source.size(); i < sz; i++) {
        final int j = i + len;
        if (j > sz)
          break;
        CoverageSet sourceCoverage = new CoverageSet(sz);
        sourceCoverage.set(i, j);
        // Sequence<IString> sourcePhrase = source.subsequence(i, j);
        
        
//        if (rules != null) {
//          for (Rule<IString> ruleOpt : rules) {
//            concreteRules.add(new ConcreteRule<IString,FV>(ruleOpt, 
//                sourceCoverage, phraseFeaturizer, scorer, source, this
//                .getName(), sourceInputId, sourceInputProperties));
//          }
//        }
      }
    }
    return concreteRules;
  }

  private Stream<SampledRule> extractRules(SentenceSample s) {
    // TODO Auto-generated method stub
    return null;
  }

  private static class SampledRule {
    public final int srcI;
    public final int srcJ;
    public final int tgtI;
    public final int tgtJ;
    public final SentenceSample s;
    public SampledRule(int i, int j, int a, int b, SentenceSample s) {
      srcI = i;
      srcJ = j;
      tgtI = a;
      tgtJ = b;
      this.s = s;
    }
  }

  @Override
  public RuleGrid<IString, FV> getRuleGrid(Sequence<IString> source,
      InputProperties sourceInputProperties, List<Sequence<IString>> targets,
      int sourceInputId, Scorer<FV> scorer) {
    throw new UnsupportedOperationException("Not yet implemented");
  }
  
  // TODO(spenceg) query functions for dense features and lex re-ordering
  // Queries should prune higher-order n-grams
  
  // TODO(spenceg) Setup caches
  // Top-k rules
  // LRU cache
  
  public static void main(String[] args) {
    String fileName = args[0];
    String queryString = args[1];
    
    try {
      DynamicTranslationModel<String> tm = IOTools.deserialize(fileName);
      TranslationModelIndex.setSystemIndex(tm.sa.getIndex());      
//      PrintWriter pw = new PrintWriter(System.out);
//      tm.sa.print(true, pw);
//      pw.flush();
//      System.exit(-1);
      
      Sequence<IString> query = IStrings.tokenize(queryString);
      int[] queryInts = Sequences.toIntArray(query);
      System.out.printf("%s :: %d%n", queryString, tm.sa.count(queryInts, true));
      long startTime = System.nanoTime();
      List<SentenceSample> samples = tm.sa.sample(queryInts, true, 100);
      long elapsedTime = System.nanoTime() - startTime;
      double numSecs = (double) elapsedTime / 1e9;
      System.out.printf("Sample time %.6fs%n", numSecs);
      System.out.println("Samples:");
      for (SentenceSample sample : samples) {
        System.out.printf("%d\t%s%n", sample.sentenceId, sample.sentence.getSource(tm.sa.getIndex()));
      }
      
    } catch (ClassNotFoundException | IOException e) {
      e.printStackTrace();
    }
  }
  
}
