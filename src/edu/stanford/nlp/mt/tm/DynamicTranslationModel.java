package edu.stanford.nlp.mt.tm;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ConcurrentHashMultiset;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.util.RuleGrid;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.MurmurHash;
import edu.stanford.nlp.mt.util.ParallelSuffixArray;
import edu.stanford.nlp.mt.util.ParallelSuffixArray.QueryResult;
import edu.stanford.nlp.mt.util.ParallelSuffixArray.Span;
import edu.stanford.nlp.mt.util.ParallelSuffixArray.SuffixArraySample;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Vocabulary;

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
  private static final int MAX_RULE_FERTILITY = 5;
  
  /**
   * Feature specification:
   * TODO(spenceg) Add additional dense features from Lin (2015) paper. There are also features
   * in Lopez's thesis based on "coherence" (i.e., the extraction rate from the samples) that might
   * work.
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
  
  private static transient final Logger logger = LogManager.getLogger(DynamicTranslationModel.class);
  
  // Parameters
  protected transient int maxSourcePhrase;
  protected transient int maxTargetPhrase;
  protected transient FeatureTemplate featureTemplate;
  protected transient RuleFeaturizer<IString, FV> featurizer;
  protected transient int sampleSize;
  protected transient String[] featureNames;
  
  // Caches
  protected transient LexCoocTable coocTable;
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
    
    // Lex cache must be created before any rules can be scored.
    tm.createLexCoocTable();
    
    return tm;
  }
  
  /**
   * Create a query cache of frequent rules.
   * 
   * @param t
   */
  public void createQueryCache(FeatureTemplate t) {
    // Explicitly make the user specify the feature template.
    setFeatureTemplate(t);
    // Now that we have a lexical co-occurence table, build the rule cache.
    Map<Span,SuffixArraySample> queryCache = sa.lookupFrequentSourceNgrams(sampleSize, RULE_CACHE_THRESHOLD);
    ruleCache = new ConcurrentHashMap<>(queryCache.size());
    for (Entry<Span,SuffixArraySample> entry : queryCache.entrySet()) {
      Span span = entry.getKey();
      SuffixArraySample sample = entry.getValue();
      Sequence<IString> sourceSpan = SampledRule.toSystemSequence(span.tokens, tm2Sys);
      List<Rule<IString>> rules = samplesToRules(sample.samples, span.tokens.length, 1.0, sourceSpan);
      ruleCache.put(sourceSpan, rules);
    }
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
   * Setup cache for lexical translations by iterating over every alignment point
   * in the underlying corpus.
   */
  private void createLexCoocTable() {
    coocTable = new LexCoocTable();
    // Iterate over every alignment point in parallel
    sa.getCorpus().getSegments().parallelStream().forEach(s -> {
      for(int i = 0; i < s.source.length; ++i) {
        int[] tgtAlign = s.f2e(i);
        int srcId = s.source[i];
        if (tgtAlign.length > 0) {
          coocTable.incrementSrcMarginal(srcId, tgtAlign.length);
          for (int j : tgtAlign) {
            int tgtId = s.target[j];
            coocTable.addCooc(srcId, tgtId);
            coocTable.incrementTgtMarginal(tgtId, 1);
          }
        } else {
          coocTable.addCooc(srcId, LexCoocTable.NULL_ID);
          coocTable.incrementSrcMarginal(srcId, 1);
        }
      }
      // Look for unaligned target words
      for(int i = 0; i < s.target.length; ++i) {
        if (s.isTargetUnaligned(i)) {
          int tgtId = s.target[i];
          coocTable.addCooc(LexCoocTable.NULL_ID, tgtId);
          coocTable.incrementTgtMarginal(tgtId, 1);
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
    boolean[][] misses = new boolean[source.size()][source.size()+1];
    
    // Iterate over source span lengths
    for (int len = 1, longestSourcePhrase = Math.min(maxSourcePhrase, source.size()); 
        len <= longestSourcePhrase; len++) {
      // Filter higher-order ranges based on lower-order misses
      List<Range> ranges = new ArrayList<>();
      for (int i = 0, sz = source.size() - len; i <= sz; ++i) {
        final int j = i + len;
        // Check lower-order n-grams for misses
        boolean miss = (len == 1 && sourceInts[i] < 0);
        for(int a = i, b = i + len - 1; len > 1 && b <= j && ! miss; ++a, ++b) {
          miss = misses[a][b];
        }
        if (miss) {
          misses[i][j] = true;
        } else {
          ranges.add(new Range(i, j));
        }
      }
      
      // TODO(spenceg) Only sample each unique n-gram once.
      
      if (ranges.size() == 0) {
        // There can't be any higher order matches
        break;
      }
      
      // Only use a parallel stream if the overhead is justified
      try (Stream<Range> rangeStream = ranges.size() > 4 ? ranges.parallelStream()
          : ranges.stream()) {
        List<ConcreteRule<IString,FV>> ruleList = rangeStream.map(range -> {
          int i = range.i;
          int j = range.j;
          int order = j - i;

          // Generate rules for this span
          final Sequence<IString> sourceSpan = source.subsequence(i, j);
          final CoverageSet sourceCoverage = new CoverageSet(source.size());
          sourceCoverage.set(i, j);
          if (ruleCache != null && ruleCache.containsKey(sourceSpan)) {
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
              misses[i][j] = true;
              return null;
            }
            double sampleRate = s.samples.size() / (double) s.numHits;
            return samplesToRules(s.samples, order, sampleRate, sourceSpan).stream().map(r -> new ConcreteRule<IString,FV>(
                r, sourceCoverage, featurizer, scorer, source, NAME, sourceInputId, sourceInputProperties))
                .collect(Collectors.toList());
          }
        }).filter(l -> l != null).flatMap(l -> l.stream()).collect(Collectors.toList());

//        System.out.printf("%d\t%d %d%n", sourceInputId, len, ruleList.size());
        
        concreteRules.addAll(ruleList);
      }
    }

    return concreteRules;
  }
  
  // TODO(spenceg) SuffixArraySample returns the prefix bounds. Add those to the Range
  // to speed up the query.
  private static class Range {
    public final int i;
    public final int j;
    public Range(int i, int j) {
      this.i = i;
      this.j = j;
    }
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
      // TODO(spenceg) The array must be grown if material is added to the phrase table
      tmIds[i] = word.id < this.sys2TM.length ? sys2TM[word.id] : Vocabulary.UNKNOWN_ID;
    }
    return tmIds;
  }

  /**
   * Note that these are abstract rules, so be sure to avoid:
   * 
   *  1) double counting repeated rules extracted from the same sentence
   *  2) 
   * @param samples
   * @param order
   * @param sampleRate
   * @param sourceSpan
   * @return
   */
  private List<Rule<IString>> samplesToRules(List<QueryResult> samples, final int order, 
      double sampleRate, Sequence<IString> sourceSpan) {
    
    // Organize rules by candidate translation
    List<SampledRule> rules = samples.stream().flatMap(s -> extractRules(s, order).stream())
        .collect(Collectors.toList());
    Map<TargetSpan,Set<SampledRule>> tgtToRule = new HashMap<>();
    for (SampledRule rule : rules) {
      TargetSpan tgtSpan = new TargetSpan(rule.tgt);
      if ( ! tgtToRule.containsKey(tgtSpan)) {
        tgtToRule.put(tgtSpan, new HashSet<>());
      }
      tgtToRule.get(tgtSpan).add(rule);
    }

    // Collect phrase counts and choose the best alignment template
    // for each src => target rule.
    List<TargetSpan> tgtSpans = new ArrayList<>(tgtToRule.keySet());
    int[] histogram = new int[tgtSpans.size()];
    for (int i = 0; i < histogram.length; ++i) {
      TargetSpan tgt = tgtSpans.get(i);
      Set<SampledRule> uniqRules = tgtToRule.get(tgt);
      histogram[i] = uniqRules.size();
      double max_lex_f_e = 0.0;
      double max_lex_e_f = 0.0;
      SampledRule maxRule = null;
      for (SampledRule rule : uniqRules) {
        scoreLex(rule);
        if (rule.lex_e_f > max_lex_e_f && rule.lex_f_e > max_lex_f_e) {
          maxRule = rule;
          max_lex_f_e = rule.lex_f_e;
          max_lex_e_f = rule.lex_e_f;
        }
      }
      uniqRules.clear();
      uniqRules.add(maxRule);
    }
    
    // TODO(spenceg) Compute confidence intervals for phrase scores
    // MLE point estimates for now.
//    double[][] ci = ConfidenceIntervals.multinomialSison(histogram);
    int ef_denom = ArrayMath.sum(histogram);
    
    List<Rule<IString>> scoredRules = new ArrayList<>(histogram.length);
    for (int r = 0; r < histogram.length; ++r) {
      TargetSpan tgt = tgtSpans.get(r);
      SampledRule rule = tgtToRule.get(tgt).iterator().next();
      
      float[] scores;
      if (featureTemplate == FeatureTemplate.DENSE) {
        scores = new float[4];
        // U. Germann's approximation
        // TODO(spenceg) Update this approximation.
        int cnt = sa.count(rule.tgt, false);
        double num = cnt - histogram[r] * sampleRate;
        scores[0] = (float) (Math.log(histogram[r]) - Math.log(histogram[r] + num));
        scores[1] = (float) Math.log(rule.lex_f_e);
        scores[2] = (float) (Math.log(histogram[r]) -  Math.log(ef_denom));
        scores[3] = (float) Math.log(rule.lex_e_f);
        
      
      } else if (featureTemplate == FeatureTemplate.DENSE_EXT) {
        scores = new float[6];
        // U. Germann's approximation
        // TODO(spenceg) Should be histogram[r] / sampleRate / cnt ?
        int eCnt = sa.count(rule.tgt, false);
//        double num = eCnt - histogram[r] * sampleRate;
//        scores[2] = (float) (Math.log(histogram[r]) - Math.log(histogram[r] + num));
      
        int adjustedCount = (int) ((double) histogram[r] / sampleRate);
//        if(adjustedCount > eCnt) {
//          System.out.println(String.format("%d %d %d %s", histogram[r], adjustedCount, eCnt, rule.toString()));
//        }
        // Clip since we sometimes overestimate when we scale by the sample rate.
        adjustedCount = Math.min(adjustedCount, eCnt);
        scores[0] = (float) (Math.log(adjustedCount) - Math.log(eCnt));
        
        scores[1] = (float) Math.log(rule.lex_f_e);
        scores[2] = (float) (Math.log(histogram[r]) - Math.log(ef_denom));
        scores[3] = (float) Math.log(rule.lex_e_f);
        
        // See train.CountFeatureExtractor
//        System.out.printf("%d %.3f %d%n", histogram[r], sampleRate, adjustedCount);
        scores[4] = adjustedCount > 1 ? (float) Math.log(adjustedCount) : 0.0f;
        scores[5] = adjustedCount == 1 ? -1.0f : 0.0f;
        
      } else {
        throw new UnsupportedOperationException("Not yet implemented.");
      }
      Rule<IString> scoredRule = rule.getRule(scores, featureNames, sourceSpan, this.tm2Sys);
      scoredRules.add(scoredRule);
    }
    return scoredRules;
  }
  
  /**
   * Helper class for indexing rules.
   * 
   * @author Spence Green
   *
   */
  private static class TargetSpan {
    private final int[] tgt;
    private final int hashCode;
    public TargetSpan(int[] tgt) {
      this.tgt = tgt;
      this.hashCode = MurmurHash.hash32(tgt, tgt.length, 1);
    }
    @Override
    public int hashCode() { return hashCode; }
    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else if ( ! (o instanceof TargetSpan)) {
        return false;
      } else {
        TargetSpan other = (TargetSpan) o;
        return Arrays.equals(tgt, other.tgt);
      }
    }
    @Override
    public String toString() { return Arrays.toString(tgt); }
  }
  
  /**
   * Compute dense lexical probabilities from the table of global
   * co-occurrences.
   * 
   * @param rule
   */
  private void scoreLex(SampledRule rule) {
    final int[] source = rule.saEntry.sentence.source;
    final int[] target = rule.saEntry.sentence.target;
    
    // Forward score p(e|f)
    double lex_e_f = 1.0;
    for (int i = rule.srcStartInclusive; i < rule.srcEndExclusive; ++i) {
      final int srcId = source[i];
      int[] tgtAlign = rule.saEntry.sentence.f2e(i);
      double efSum = 0.0;
      if (tgtAlign.length > 0) {
        for (int j : tgtAlign) {
          int tgtId = target[j];
          int c_e_f = coocTable.getJointCount(srcId, tgtId);
          int c_f = coocTable.getSrcMarginal(srcId);
          assert c_f > 0 : String.format("%d", srcId);
          efSum += (c_e_f / (double) c_f);
        }
        efSum /= (double) tgtAlign.length;
        
      } else {
        int c_e_f = coocTable.getJointCount(srcId, LexCoocTable.NULL_ID);
        int c_f = coocTable.getSrcMarginal(srcId);
        assert c_f > 0;
        efSum = (c_e_f / (double) c_f);
      }
      if (efSum == 0.0) efSum = MIN_LEX_PROB;
      lex_e_f *= efSum;
    }
    assert lex_e_f >= 0 && lex_e_f <= 1.0;
    
    // Backward score p(f|e)
    double lex_f_e = 1.0;
    for (int i = rule.tgtStartInclusive; i < rule.tgtEndExclusive; ++i) {
      final int tgtId = target[i];
      int[] srcAlign = rule.saEntry.sentence.e2f(i);
      double feSum = 0.0;
      if (srcAlign.length > 0) {
        for (int j : srcAlign) {
          int srcId = source[j];
          int c_f_e = coocTable.getJointCount(srcId, tgtId);
          int c_e = coocTable.getTgtMarginal(tgtId);
          assert c_e > 0;
          feSum += (c_f_e / (double) c_e);
        }
        feSum /= (double) srcAlign.length;
        
      } else {
        int c_f_e = coocTable.getJointCount(LexCoocTable.NULL_ID, tgtId);
        int c_e = coocTable.getTgtMarginal(tgtId);
        assert c_e > 0;
        feSum = (c_f_e / (double) c_e);
      }
      if (feSum == 0.0) feSum = MIN_LEX_PROB;
      lex_f_e *= feSum;
    }
    assert lex_f_e >= 0.0 && lex_f_e <= 1.0;

    rule.lex_e_f = (float) lex_e_f;
    rule.lex_f_e = (float) lex_f_e;
  }

  /**
   * Extract admissible phrase pairs from the sampled sentence.
   * This is the "pattern matching" algorithm of Lopez (2008).
   * 
   * @param s
   * @return
   */
  private List<SampledRule> extractRules(QueryResult s, int length) {    
    // Find the target span
    int minTarget = Integer.MAX_VALUE;
    int maxTarget = -1;
//    CoverageSet targetCoverage = new CoverageSet(s.sentence.target.length);
    final int startSource = s.wordPosition;
    final int endSource = startSource + length;
    for(int sourcePos = startSource; sourcePos < endSource; sourcePos++) {
      assert sourcePos < s.sentence.source.length : String.format("[%d,%d) %d %d ", startSource, endSource, sourcePos, s.sentence.source.length);
      int[] alignedList = s.sentence.f2e(sourcePos);
      for(int targetPos : alignedList) {
        if (targetPos < minTarget) {
          minTarget = targetPos;
        }
        if (targetPos > maxTarget) {
          maxTarget = targetPos;
        }
//        targetCoverage.set(targetPos);
      }
    }
    
    List<SampledRule> ruleList = new ArrayList<>();
//    final boolean targetGapExists = targetCoverage.nextClearBit(minTarget) <= maxTarget;
    if (maxTarget < 0 || maxTarget-minTarget >= maxTargetPhrase) return ruleList;
    
    // Admissibility check
//    CoverageSet sourceCoverage = new CoverageSet(s.sentence.source.length);
    for (int i = minTarget; i <= maxTarget; ++i) {
      int[] srcAligned = s.sentence.e2f(i);
      for (int j : srcAligned) {
        if (j < startSource || j >= endSource) {
          // Failed check
          return ruleList;
        }
//        sourceCoverage.set(j);
      }
    }
    
//    final boolean sourceGapExists = sourceCoverage.nextSetBit(startSource) < endSource;
//    if (sourceGapExists) return ruleList;
    
    // "Loose" heuristic to grow the target
    // Try to grow the left bound of the target
    for(int startTarget = minTarget; (startTarget >= 0 &&
        startTarget > maxTarget-maxTargetPhrase &&
        (startTarget == minTarget || s.sentence.isTargetUnaligned(startTarget))); startTarget--) {

      // Try to grow the right bound of the target
      for (int endTarget=maxTarget; (endTarget < s.sentence.target.length &&
          endTarget < startTarget+maxTargetPhrase && 
          (endTarget==maxTarget || s.sentence.isTargetUnaligned(endTarget))); endTarget++) {

        // Filter out messed up alignments
        if (Math.abs((endSource-startSource) - (endTarget-startTarget+1)) <= MAX_RULE_FERTILITY) {
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
      tm.createQueryCache(FeatureTemplate.DENSE_EXT);
      
      long elapsedTime = System.nanoTime() - startTime;
      double numSecs = (double) elapsedTime / 1e9;
      System.out.printf("Loading time: %.3fs%n", numSecs);
      System.out.printf("Source cardinality: %d%n", tm.maxLengthSource());
      System.out.printf("Source cardinality: %d%n", tm.maxLengthTarget());
      
      // Read the source at once for accurate timing of queries
      List<Sequence<IString>> sourceFile = IStrings.tokenizeFile(inputFile);
      
      System.out.printf("#source segments: %d%n", sourceFile.size());
      
      startTime = System.nanoTime();
      int sourceId = 0;
      for (Sequence<IString> source : sourceFile) {
        tm.getRules(source, null, null, sourceId++, null);
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
