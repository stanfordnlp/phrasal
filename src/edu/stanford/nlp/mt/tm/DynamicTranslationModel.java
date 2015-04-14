package edu.stanford.nlp.mt.tm;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

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
  
  private static final transient String FEATURE_PREFIX = "TM";
  private static final transient int DEFAULT_MAX_PHRASE_LEN = 7;
  private static final transient int DEFAULT_SAMPLE_SIZE = 100;
  
  public static enum FeatureTemplate {DENSE, DENSE_EXT, DENSE_EXT_LEX};
  
  public ParallelSuffixArray sa;
  
  private transient int maxSourcePhrase;
  private transient int maxTargetPhrase;
  private transient FeatureTemplate featureTemplate;
  private transient RuleFeaturizer<IString, FV> featurizer;
  private transient boolean isSystemIndex;
  private transient int sampleSize;
  private transient String[] featureNames;
  
  /**
   * Constructor.
   * 
   * @param suffixArray
   */
  public DynamicTranslationModel(ParallelSuffixArray suffixArray) {
    this.sa = suffixArray;
    this.maxSourcePhrase = DEFAULT_MAX_PHRASE_LEN;
    this.maxTargetPhrase = DEFAULT_MAX_PHRASE_LEN;
    this.isSystemIndex = false;
    this.sampleSize = DEFAULT_SAMPLE_SIZE;
    setFeatureTemplate(FeatureTemplate.DENSE);
  }
  
  
  public void createCaches() {
    // TODO(spenceg) Cache information from the suffix array. 
  }

  public void setFeatureTemplate(FeatureTemplate t) {
    featureTemplate = t;
    if (t == FeatureTemplate.DENSE) {
      featureNames = (String[]) IntStream.range(0, 4).mapToObj(i -> {
        return String.format("%s.%d", FEATURE_PREFIX, i);
      }).toArray(String[]::new);
    
    } else if (t == FeatureTemplate.DENSE_EXT) {
      featureNames = (String[]) IntStream.range(0, 6).mapToObj(i -> {
        return String.format("%s.%d", FEATURE_PREFIX, i);
      }).toArray(String[]::new);
    
    } else {
      throw new UnsupportedOperationException("Not yet implemented.");
    }
  }
  
  public void setMaxSourcePhrase(int dim) {
    maxSourcePhrase = dim;
  }
  
  public void setMaxTargetPhrase(int dim) {
    maxTargetPhrase = dim;
  }
  
  public void setSystemIndex(boolean b) {
    this.isSystemIndex = b;
    if (b) {
      TranslationModelIndex.setSystemIndex(sa.getIndex());
    }
  }
  
  public void setSampleSize(int k) {
    this.sampleSize = k;
  }
  
  @Override
  public int maxLengthSource() { return maxSourcePhrase; }

  @Override
  public int maxLengthTarget() { return maxTargetPhrase; }
  
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
    
    final List<ConcreteRule<IString,FV>> concreteRules = new ArrayList<>(source.size() * source.size() * 100);
    final int[] sourceInts = isSystemIndex ? Sequences.toIntArray(source) : 
      Sequences.toIntArray(source, this.sa.getIndex());
    final BitSet misses = new BitSet(source.size());
    final int longestSourcePhrase = Math.min(maxSourcePhrase, source.size());
    // Iterate over source span lengths
    for (int len = 1; len <= longestSourcePhrase; len++) {
      final BitSet newMisses = new BitSet();
      final int order = len;
      
      // Parallel extraction
      List<ConcreteRule<IString,FV>> ruleList = IntStream.rangeClosed(0, source.size() - len).parallel().mapToObj(i -> {
        final int j = i + order;

        // Check for misses
        int nextMiss = misses.nextSetBit(i);
        if (nextMiss >= 0 && nextMiss < j) {
          newMisses.set(i, j);
          return null;
        }

        // TODO(spenceg) Grow this source phrase if possible.
        
        // Sample from the suffix array
        final int[] sourcePhrase = Arrays.copyOfRange(sourceInts, i, j);
        final List<SentenceSample> samples = sa.sample(sourcePhrase, true, sampleSize);

        // Extract rules and histograms
        final Counter<SampledRule> feCounts = new ClassicCounter<>();
        final Counter<Integer> eCounts = new ClassicCounter<>(),
            fCounts = new ClassicCounter<>();
        for (SentenceSample s : samples) {
          List<SampledRule> rules = extractRules(s, order);
          for (SampledRule rule : rules) {
            feCounts.incrementCount(rule);
            eCounts.incrementCount(rule.eID);
            fCounts.incrementCount(rule.fID);
            
            // TODO(spenceg) Collect lex counts lex counts
          }
        }
        
        // Create histograms for the feature values
        
        List<SampledRule> sampledRules = new ArrayList<>(feCounts.keySet());
        int[] histogram = new int[sampledRules.size()];
        for (int r = 0; r < histogram.length; ++r) {
          histogram[r] = (int) feCounts.getCount(sampledRules.get(r));
          // TODO(spenceg) Compute lex probabilities
          
        }
        
        // TODO(spenceg) Compute confidence intervals for phrase scores
        
        List<ConcreteRule<IString,FV>> scoredRules = new ArrayList<>(histogram.length);
        CoverageSet sourceCoverage = new CoverageSet(source.size());
        sourceCoverage.set(i, j);
        for (int r = 0; r < histogram.length; ++r) {
          SampledRule rule = sampledRules.get(r);
          
          float[] scores;
          if (featureTemplate == FeatureTemplate.DENSE) {
            scores = new float[4];
          
          } else if (featureTemplate == FeatureTemplate.DENSE_EXT) {
            scores = new float[6];
          
          } else {
            throw new UnsupportedOperationException("Not yet implemented.");
          }
          Rule<IString> scoredRule = rule.getRule(scores, featureNames, rule.e2f, this.sa.getIndex());
          
          scoredRules.add(new ConcreteRule<IString,FV>(scoredRule, 
            sourceCoverage, featurizer, scorer, source, this
            .getName(), sourceInputId, sourceInputProperties));
        }
        
        return scoredRules;
        
      }).flatMap(list -> list.stream()).collect(Collectors.toList());
      misses.clear();
      misses.or(newMisses);
      concreteRules.addAll(ruleList);
    }

    return concreteRules;
  }

  /**
   * Extract admissible phrase pairs from the sampled sentence.
   * 
   * @param s
   * @param i
   * @param j
   * @return
   */
  private List<SampledRule> extractRules(SentenceSample s, int length) {    
    // Find the target span
    int minTarget = Integer.MAX_VALUE;
    int maxTarget = -1;
    CoverageSet targetCoverage = new CoverageSet();
    final int startSource = s.wordPosition;
    final int endSource = startSource + length;
    for(int sourcePos = startSource; sourcePos < endSource; sourcePos++) {
      assert sourcePos < s.sentence.source.length : String.format("[%d,%d) %d %d ", startSource, endSource, sourcePos, s.sentence.source.length);
      int[] alignedList = s.sentence.f2e[sourcePos];
      if (alignedList.length == 0) continue;
      for(int ind = 0; ind < alignedList.length; ind++) {
        int targetPos = alignedList[ind];
        if (targetPos < minTarget) {
          minTarget = targetPos;
        }
        if (targetPos > maxTarget) {
          maxTarget = targetPos;
        }
        targetCoverage.set(targetPos);
      }
    }
    
    // Admissibility check on the target span
    final boolean gapExists = targetCoverage.nextClearBit(minTarget) <= maxTarget;
    List<SampledRule> ruleList = new ArrayList<>();
    if (maxTarget >= 0 && 
        maxTarget-minTarget < maxTargetPhrase &&
        !gapExists) {

      BitSet isAligned = s.sentence.getTargetAlignedCoverage();
      // Try to grow the left bound of the target
      for(int startTarget = minTarget; (startTarget >= 0 &&
              startTarget > maxTarget-maxTargetPhrase &&
              (startTarget == minTarget || ! isAligned.get(startTarget))); startTarget--) {

        // Try to grow the right bound of the target
        for (int endTarget=maxTarget; (endTarget < s.sentence.target.length &&
            endTarget < startTarget+maxTargetPhrase && 
            (endTarget==maxTarget || ! isAligned.get(endTarget))); endTarget++) {
          // TODO(spenceg) Need to fill this out for feature API.
          int[][] e2f = new int[maxTarget-minTarget+1][];
          SampledRule r = new SampledRule(startSource, endSource, startTarget, endTarget + 1, s, e2f);
          ruleList.add(r);
        }
      }
    }
    
    return ruleList;
  }

  @Override
  public RuleGrid<IString, FV> getRuleGrid(Sequence<IString> source,
      InputProperties sourceInputProperties, List<Sequence<IString>> targets,
      int sourceInputId, Scorer<FV> scorer) {
    throw new UnsupportedOperationException("Not yet implemented");
  }
  
  
  /**
   * Read an input file and extract rules from a model.
   * 
   * @param args
   */
  public static void main(String[] args) {
    String fileName = args[0];
    String inputFile = args[1];
    
    try {
      long startTime = System.nanoTime();
      DynamicTranslationModel<String> tm = IOTools.deserialize(fileName);
      tm.setSystemIndex(true);
      tm.setMaxSourcePhrase(DEFAULT_MAX_PHRASE_LEN);
      tm.setMaxTargetPhrase(DEFAULT_MAX_PHRASE_LEN);
      tm.setSampleSize(100);
      tm.setFeatureTemplate(FeatureTemplate.DENSE);
      long elapsedTime = System.nanoTime() - startTime;
      double numSecs = (double) elapsedTime / 1e9;
      System.out.printf("Loading time: %.3fs%n", numSecs);
      System.out.printf("Source cardinality: %d%n", tm.maxLengthSource());
      System.out.printf("Source cardinality: %d%n", tm.maxLengthTarget());
      
      // Read the source at once for accurate timing of queries
      List<Sequence<IString>> sourceFile = IStrings.tokenizeFile(inputFile);
      
      System.out.printf("#source segments: %d%n", sourceFile.size());
      
      startTime = System.nanoTime();
      for (Sequence<IString> source : sourceFile) {
        for (int len = 1; len <= DEFAULT_MAX_PHRASE_LEN; ++len) {
          for (int i = 0; i < source.size() - len; ++i) {
            final int j = i+len;
            List<ConcreteRule<IString, String>> ruleList = tm.getRules(source.subsequence(i, j),
                null, null, 0, null);
          }
        }
      }
      elapsedTime = System.nanoTime() - startTime;
      numSecs = (double) elapsedTime / 1e9;
      double timePerSegment = numSecs / (double) sourceFile.size();
      System.out.printf("Sample time:\t%.3fs%n", numSecs);
      System.out.printf("Time/segment:\t%.3fs%n", timePerSegment);
      
    } catch (ClassNotFoundException | IOException e) {
      e.printStackTrace();
    }
  }
  
}
