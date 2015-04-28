package edu.stanford.nlp.mt.tm;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.collect.ConcurrentHashMultiset;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.util.RuleGrid;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.util.AtomicBitSet;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.ParallelSuffixArray;
import edu.stanford.nlp.mt.util.ParallelSuffixArray.QueryResult;
import edu.stanford.nlp.mt.util.ParallelSuffixArray.Span;
import edu.stanford.nlp.mt.util.ParallelSuffixArray.SuffixArraySample;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Vocabulary;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

/**
 * A dynamic translation model backed by a suffix array.
 * 
 * See here for lex re-ordering: https://github.com/moses-smt/mosesdecoder/commit/51824355f9f469c186d5376218e3396b92652617
 * 
 * @author Spence Green
 *
 */
public class DynamicTranslationModel<FV> implements TranslationModel<IString,FV>,Serializable {

  private static final long serialVersionUID = 5876435802959430120L;
  
  private static final String FEATURE_PREFIX = "DYN";
  private static final String NAME = "dynamic-tm";
  public static final int DEFAULT_MAX_PHRASE_LEN = 7;
  public static final int DEFAULT_SAMPLE_SIZE = 100;
  private static final int RULE_CACHE_THRESHOLD = 1000;
  public static final double MIN_LEX_PROB = 1e-5;
  
  /**
   * Feature specification:
   * TODO(spenceg) Add additional dense features from Lin (2015) paper.
   *  
   *  [0] := phi_f_e
   *  [1] := lex_f_e
   *  [2] := phi_e_f
   *  [3] := lex_e_f
   *  [4] := log(count)
   *  [5] := 1 if count == 1 else 0
   *
   */
  public static enum FeatureTemplate {DENSE, DENSE_EXT, DENSE_EXT_LEX};
  
  protected ParallelSuffixArray sa;
  
  // Parameters
  protected transient int maxSourcePhrase;
  protected transient int maxTargetPhrase;
  protected transient FeatureTemplate featureTemplate;
  protected transient RuleFeaturizer<IString, FV> featurizer;
  protected transient int sampleSize;
  protected transient String[] featureNames;
  
  // Caches
  protected transient LexCoocTable coocCache;
  protected transient Map<Sequence<IString>,List<Rule<IString>>> ruleCache;
  
  // Vocabulary translation arrays
  protected transient int[] sys2TM;
  protected transient int[] tm2Sys;
  
  /**
   * No-arg constructor for deserialization. Creates caches
   */
  public DynamicTranslationModel() {}
  
  /**
   * Constructor.
   * 
   * NOTE: This constructor does *not* create caches.
   * 
   * @param suffixArray
   */
  public DynamicTranslationModel(ParallelSuffixArray suffixArray) {
    this.sa = suffixArray;
    this.maxSourcePhrase = DEFAULT_MAX_PHRASE_LEN;
    this.maxTargetPhrase = DEFAULT_MAX_PHRASE_LEN;
    this.sampleSize = DEFAULT_SAMPLE_SIZE;
    setFeatureTemplate(FeatureTemplate.DENSE);
  }
  
  
  /**
   * Load a model from file. This is the only supported method for loading the dynamic TM.
   * 
   * @param filename
   * @return
   * @throws IOException
   */
  @SuppressWarnings("unchecked")
  public static <FV> DynamicTranslationModel<FV> load(String filename, boolean initializeSystemVocabulary) throws IOException {
    DynamicTranslationModel<FV> tm = IOTools.deserialize(filename, DynamicTranslationModel.class);
    tm.maxSourcePhrase = DEFAULT_MAX_PHRASE_LEN;
    tm.maxTargetPhrase = DEFAULT_MAX_PHRASE_LEN;
    tm.sampleSize = DEFAULT_SAMPLE_SIZE;
    tm.setFeatureTemplate(FeatureTemplate.DENSE);
    
    if (initializeSystemVocabulary) tm.populateSystemVocabulary();
    // Id arrays must be created after any modification of the system vocabulary.
    tm.createIdArrays();
    
    // Lex cache must be created before the rule cache.
    tm.createLexCaches();
    // Now that we have a lexical co-occurence table, build the rule cache.
    Map<Span,SuffixArraySample> queryCache = tm.sa.lookupFrequentSourceNgrams(tm.sampleSize, RULE_CACHE_THRESHOLD);
    tm.ruleCache = new ConcurrentHashMap<>(queryCache.size());
    for (Entry<Span,SuffixArraySample> entry : queryCache.entrySet()) {
      Span span = entry.getKey();
      SuffixArraySample sample = entry.getValue();
      Sequence<IString> sourceSpan = SampledRule.toSystemSequence(span.tokens, tm.tm2Sys);
      List<Rule<IString>> rules = tm.samplesToRules(sample.samples, span.tokens.length, 1.0, sourceSpan);
      tm.ruleCache.put(sourceSpan, rules);
    }
    return tm;
  }
  
  /**
   * Create mappings between the system vocabulary and the translation model vocabulary.
   */
  private void createIdArrays() {
    final int sysSize = Vocabulary.systemSize();
    final Vocabulary tmVocab = sa.getVocabulary();
    sys2TM = new int[sysSize];
    IntStream.range(0, sysSize).parallel().forEach(i -> {
      sys2TM[i] = tmVocab.indexOf(Vocabulary.systemGet(i));
    });
    int tmSize = tmVocab.size();
    tm2Sys = new int[tmSize];
    IntStream.range(0, tmSize).parallel().forEach(i -> {
      tm2Sys[i] = Vocabulary.systemIndexOf(tmVocab.get(i));
    });
  }

  /**
   * Setup cache for lexical translations.
   */
  private void createLexCaches() {
    coocCache = new LexCoocTable();
    Vocabulary index = sa.getVocabulary();
    IntStream.range(0, index.size()).parallel().forEach(i -> {
      int[] query = new int[]{i};
      
      // Sample the source
      List<QueryResult> samples = sa.query(query, true);
      if (samples.size() > 0) {
        for (QueryResult q : samples) {
          int srcIndex = q.wordPosition;
          int srcId = q.sentence.source[srcIndex];
          assert srcId == i;
          int[] tgtAlign = q.sentence.f2e(srcIndex);
          if (tgtAlign.length > 0) {
            coocCache.incrementSrcMarginal(srcId, tgtAlign.length);
            for (int tgtIndex : tgtAlign) {
              int tgtId = q.sentence.target[tgtIndex];
              coocCache.addCooc(srcId, tgtId);
            }
          } else {
            coocCache.addCooc(srcId, LexCoocTable.NULL_ID);
            coocCache.incrementSrcMarginal(srcId, 1);
          }
        }
      }
      
      // Sample the target
      samples = sa.query(query, false);
      if (samples.size() > 0) {
        for (QueryResult q : samples) {
          int tgtIndex = q.wordPosition;
          int tgtId = q.sentence.target[tgtIndex];
          assert tgtId == i;
          int[] srcAlign = q.sentence.e2f(tgtIndex);
          if (srcAlign.length > 0) {
            coocCache.incrementTgtMarginal(tgtId, srcAlign.length);
            for (int srcIndex : srcAlign) {
              int srcId = q.sentence.source[srcIndex];
              coocCache.addCooc(tgtId, srcId);
            }
          } else {
            coocCache.addCooc(tgtId, LexCoocTable.NULL_ID);
            coocCache.incrementTgtMarginal(tgtId, 1);
          }
        }
      }
    });
  }

  /**
   * Set the type of dense rule features.
   * 
   * @param t
   */
  public void setFeatureTemplate(FeatureTemplate t) {
    this.featureTemplate = t;
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
  
  public void setSampleSize(int sz) {
    this.sampleSize = sz;
  }
  
  /**
   * Inject the TM vocabulary into the system vocabulary.
   */
  private void populateSystemVocabulary() {
    final Vocabulary tmVocab = sa.getVocabulary();
    int tmSize = tmVocab.size();
    IntStream.range(0, tmSize).parallel().forEach(i -> {
      String wordType = tmVocab.get(i);
      Vocabulary.systemAdd(wordType);
    });
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
    return Arrays.asList(featureNames);
  }

  @Override
  public String getName() {
    return NAME;
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
    final int[] sourceInts = toTMArray(source);
    
    // Zhang and Vogel (2005) trick -- prune higher-order queries using lower-order misses
    AtomicBitSet misses = new AtomicBitSet(source.size());
    // Mark OOV and unigram misses
    for (int i = 0; i < sourceInts.length; ++i) {
      if (sourceInts[i] < 0) misses.set(i);
      // Lookup unigrams in constant time here
    }
    final int longestSourcePhrase = Math.min(maxSourcePhrase, source.size());
    // Iterate over source span lengths
    for (int len = 1; len <= longestSourcePhrase; len++) {
      final AtomicBitSet newMisses = new AtomicBitSet(source.size());
      final AtomicBitSet currentMisses = misses;
      final int order = len;

      // TODO(spenceg) Move range check here and prune bad ranges
      // Also, save bounds of each prefix for initialization
      
      // Parallel extraction
      // TODO(spenceg) Lots of wasted threads here.
      List<ConcreteRule<IString,FV>> ruleList = IntStream.rangeClosed(0, source.size() - order)
          .parallel().mapToObj(i -> {
        final int j = i + order;

        // Check for lower-order misses
        int nextMiss = currentMisses.nextSetBit(i);
        if (nextMiss >= 0 && nextMiss < j) {
          newMisses.set(i, j-1);
          return null;
        }
        
        // Generate rules for this span
        final Sequence<IString> sourceSpan = source.subsequence(i, j);
        final CoverageSet sourceCoverage = new CoverageSet(source.size());
        sourceCoverage.set(i, j);
        if (ruleCache.containsKey(sourceSpan)) {
          // Get from the rule cache
          return ruleCache.get(sourceSpan).stream().map(r -> new ConcreteRule<IString,FV>(
              r, sourceCoverage, featurizer, scorer, source, NAME, sourceInputId, sourceInputProperties))
              .collect(Collectors.toList());
          
        } else {
          // Sample from the suffix array
          final int[] sourcePhrase = Arrays.copyOfRange(sourceInts, i, j);
          SuffixArraySample s = sa.sample(sourcePhrase, true, sampleSize);
          if (s.samples.size() == 0) {
            // This span is not present in the training data.
            newMisses.set(i, j-1);
            return null;
          }
          double sampleRate = s.samples.size() / (double) s.numHits;
          return samplesToRules(s.samples, order, sampleRate, sourceSpan).stream().map(r -> new ConcreteRule<IString,FV>(
              r, sourceCoverage, featurizer, scorer, source, NAME, sourceInputId, sourceInputProperties))
              .collect(Collectors.toList());
        }
      }).filter(l -> l != null).flatMap(l -> l.stream()).collect(Collectors.toList());
      misses = newMisses;
      concreteRules.addAll(ruleList);
    }

    return concreteRules;
  }

  /**
   * Convert the source span to translation model indices.
   * 
   * @param source
   * @return
   */
  private int[] toTMArray(Sequence<IString> source) {
    int sourceSize = source.size();
    int[] tmIds = new int[sourceSize];
    for (int i = 0; i < sourceSize; ++i) {
      IString word = source.get(i);
      // TODO(spenceg) The array should be lengthened for OOVs
      tmIds[i] = word.id < this.sys2TM.length ? sys2TM[word.id] : Vocabulary.UNKNOWN_ID;
    }
    return tmIds;
  }

  private List<Rule<IString>> samplesToRules(List<QueryResult> samples, final int order, 
      double sampleRate, Sequence<IString> sourceSpan) {
    // Extract rules and histograms
    final Counter<SampledRule> feCounts = new ClassicCounter<>();
    samples.stream().flatMap(s -> extractRules(s, order).stream()).forEach(rule -> {
      feCounts.incrementCount(rule);
      scoreLex(rule);
    });
    
    // Create histograms for the phrase feature values
    final List<SampledRule> sampledRules = new ArrayList<>(feCounts.keySet());
    double[] histogram = new double[sampledRules.size()];
    for (int r = 0; r < histogram.length; ++r) {
      histogram[r] = feCounts.getCount(sampledRules.get(r));
    }
    
    // TODO(spenceg) Compute confidence intervals for phrase scores
    // Count and divide for now
//    double[][] ci = ConfidenceIntervals.multinomialSison(histogram);
    double ef_denom = ArrayMath.sum(histogram);
    
    List<Rule<IString>> scoredRules = new ArrayList<>(histogram.length);
    for (int r = 0; r < histogram.length; ++r) {
      SampledRule rule = sampledRules.get(r);
      
      float[] scores;
      if (featureTemplate == FeatureTemplate.DENSE) {
        scores = new float[4];
        scores[0] = (float) (Math.log(histogram[r]) - Math.log(ef_denom));
        scores[1] = (float) Math.log(rule.lex_f_e);
        // U. Germann's approximation
        int cnt = sa.count(rule.tgt, false);
        double num = cnt - histogram[r] * sampleRate;
        scores[2] = (float) (Math.log(histogram[r]) - Math.log(histogram[r] + num));
        
        scores[3] = (float) Math.log(rule.lex_e_f);
        
      
      } else if (featureTemplate == FeatureTemplate.DENSE_EXT) {
        scores = new float[6];
        scores[0] = (float) (Math.log(histogram[r]) - Math.log(ef_denom));
        scores[1] = (float) Math.log(rule.lex_f_e);
        // U. Germann's approximation
        // TODO(spenceg) Should be histogram[r] / sampleRate / cnt ?
        int cnt = sa.count(rule.tgt, false);
        double num = cnt - histogram[r] * sampleRate;
        scores[2] = (float) (Math.log(histogram[r]) - Math.log(histogram[r] + num));
        
        scores[3] = (float) Math.log(rule.lex_e_f);
        scores[4] = (float) Math.log(histogram[r]);
        scores[5] = histogram[r] == 1.0 ? 1.0f : 0.0f;
        
      } else {
        throw new UnsupportedOperationException("Not yet implemented.");
      }
      Rule<IString> scoredRule = rule.getRule(scores, featureNames, sourceSpan, this.tm2Sys);
      scoredRules.add(scoredRule);
    }
    return scoredRules;
  }
  
  /**
   * Compute dense lexical probabilities.
   * 
   * @param rule
   */
  private void scoreLex(SampledRule rule) {
    final int[] source = rule.saEntry.sentence.source;
    final int[] target = rule.saEntry.sentence.target;
    
    // Forward score
    double lex_e_f = 1.0;
    for (int i = rule.srcStartInclusive; i < rule.srcEndExclusive; ++i) {
      final int srcId = source[i];
      int[] tgtAlign = rule.saEntry.sentence.f2e(i);
      double efSum = 0.0;
      if (tgtAlign.length > 0) {
        for (int j : tgtAlign) {
          int tgtId = target[j];
          int c_e_f = coocCache.getJointCount(srcId, tgtId);
          int c_f = coocCache.getSrcMarginal(srcId);
          assert c_f > 0 : String.format("%d", srcId);
          efSum += c_e_f / (double) c_f;
        }
        efSum /= tgtAlign.length;
        
      } else {
        int c_e_f = coocCache.getJointCount(srcId, LexCoocTable.NULL_ID);
        int c_f = coocCache.getSrcMarginal(srcId);
        assert c_f > 0;
        efSum = c_e_f / (double) c_f;
      }
      if (efSum == 0.0) efSum = MIN_LEX_PROB;
      lex_e_f *= efSum;
    }
    
    // Backward score
    double lex_f_e = 1.0;
    for (int i = rule.tgtStartInclusive; i < rule.tgtEndExclusive; ++i) {
      final int tgtId = target[i];
      int[] srcAlign = rule.saEntry.sentence.e2f(i);
      double feSum = 0.0;
      if (srcAlign.length > 0) {
        for (int j : srcAlign) {
          int srcId = source[j];
          int c_f_e = coocCache.getJointCount(tgtId, srcId);
          int c_e = coocCache.getTgtMarginal(tgtId);
          assert c_e > 0;
          feSum += c_f_e / (double) c_e;
        }
        feSum /= srcAlign.length;
        
      } else {
        int c_f_e = coocCache.getJointCount(tgtId, LexCoocTable.NULL_ID);
        int c_e = coocCache.getTgtMarginal(tgtId);
        assert c_e > 0;
        feSum = c_f_e / (double) c_e;
      }
      if (feSum == 0.0) feSum = MIN_LEX_PROB;
      lex_f_e *= feSum;
    }

    // Compare to the existing scores
    // TODO(spenceg) Should we take the argmax independently?
    if (rule.lex_e_f < lex_e_f && rule.lex_f_e < lex_f_e) {
      rule.lex_e_f = (float) lex_e_f;
      rule.lex_f_e = (float) lex_f_e;
    }
  }

  /**
   * Extract admissible phrase pairs from the sampled sentence.
   * This is the "pattern matching" algorithm of Lopez (2008).
   * 
   * @param s
   * @param i
   * @param j
   * @return
   */
  private List<SampledRule> extractRules(QueryResult s, int length) {    
    // Find the target span
    int minTarget = Integer.MAX_VALUE;
    int maxTarget = -1;
    CoverageSet targetCoverage = new CoverageSet();
    final int startSource = s.wordPosition;
    final int endSource = startSource + length;
    for(int sourcePos = startSource; sourcePos < endSource; sourcePos++) {
      assert sourcePos < s.sentence.source.length : String.format("[%d,%d) %d %d ", startSource, endSource, sourcePos, s.sentence.source.length);
      int[] alignedList = s.sentence.f2e(sourcePos);
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
          SampledRule r = new SampledRule(startSource, endSource, startTarget, endTarget + 1, s);
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
   * Create a lexical cooccurrance table for a source input.
   * 
   * @author Spence Green
   *
   */
  private class LexCoocTable {

    public static final int NULL_ID = -1;
    
    private final ConcurrentHashMultiset<Integer> srcMarginals = ConcurrentHashMultiset.create();
    private final ConcurrentHashMultiset<Integer> tgtMarginals = ConcurrentHashMultiset.create();
    private final ConcurrentHashMap<Integer,ConcurrentHashMultiset<Integer>> jointCooc = 
        new ConcurrentHashMap<>();
    
    public LexCoocTable() {}
    
    public boolean contains(int id) {
      return jointCooc.containsKey(id);
    }
    
    public void addCooc(int srcId, int tgtId) {
      jointCooc.putIfAbsent(srcId, ConcurrentHashMultiset.create());
      jointCooc.get(srcId).add(tgtId);
    }
    
    public void incrementSrcMarginal(int srcId, int count) {
      while (true) {
        int oldCount = srcMarginals.count(srcId);
        if (srcMarginals.setCount(srcId, oldCount, oldCount + count)) break;
      }
    }
    
    public void incrementTgtMarginal(int tgtId, int count) {
      while (true) {
        int oldCount = tgtMarginals.count(tgtId);
        if (tgtMarginals.setCount(tgtId, oldCount, oldCount + count)) break;
      }
    }
    
    public int getSrcMarginal(int srcId) { return srcMarginals.count(srcId); }
    
    public int getTgtMarginal(int tgtId) { return tgtMarginals.count(tgtId); }
    
    public int getJointCount(int srcId, int tgtId) {
      return jointCooc.containsKey(srcId) ? jointCooc.get(srcId).count(tgtId)
          : 0;
    }
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
      DynamicTranslationModel<String> tm = DynamicTranslationModel.load(fileName, true);
      tm.setFeatureTemplate(FeatureTemplate.DENSE_EXT);
      
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
            tm.getRules(source.subsequence(i, j), null, null, 0, null);
          }
        }
      }
      elapsedTime = System.nanoTime() - startTime;
      numSecs = (double) elapsedTime / 1e9;
      double timePerSegment = numSecs / (double) sourceFile.size();
      System.out.printf("Sample time:\t%.3fs%n", numSecs);
      System.out.printf("Time/segment:\t%.3fs%n", timePerSegment);
      
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
}
