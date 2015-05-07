package edu.stanford.nlp.mt.tm;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
import edu.stanford.nlp.mt.util.TimingUtils;
import edu.stanford.nlp.mt.util.TimingUtils.TimeKeeper;
import edu.stanford.nlp.mt.util.Vocabulary;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

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
    TimeKeeper timer = TimingUtils.start();
    DynamicTranslationModel<FV> tm = IOTools.deserialize(filename, DynamicTranslationModel.class);
    tm.maxSourcePhrase = DEFAULT_MAX_PHRASE_LEN;
    tm.maxTargetPhrase = DEFAULT_MAX_PHRASE_LEN;
    tm.sampleSize = DEFAULT_SAMPLE_SIZE;
    tm.setFeatureTemplate(FeatureTemplate.DENSE);
    timer.mark("Deserialization");
    
    if (initializeSystemVocabulary) tm.populateSystemVocabulary();
    // Id arrays must be created after any modification of the system vocabulary.
    tm.createIdArrays();
    timer.mark("Vocabulary setup");
    
    // Lex cache must be created before any rules can be scored.
    tm.createLexCoocTable(tm.sa.getVocabulary().size());
    timer.mark("Lexical cooc table");
    
    logger.info("Timing: {}", timer);
    return tm;
  }
  
  /**
   * Create a query cache of frequent rules. Extract rules from
   * the cache in parallel.
   * 
   * @param t
   */
  public void createQueryCache(FeatureTemplate t) {
    // Explicitly make the user specify the feature template.
    setFeatureTemplate(t);
    // Now that we have a lexical co-occurence table, build the rule cache.
    Map<Span,SuffixArraySample> queryCache = sa.lookupFrequentSourceNgrams(sampleSize, RULE_CACHE_THRESHOLD);
    ruleCache = new ConcurrentHashMap<>(queryCache.size());
    logger.info("Extracting rules from query cache of size {}", queryCache.size());
    queryCache.entrySet().parallelStream().forEach(entry -> {
      Span span = entry.getKey();
      SuffixArraySample sample = entry.getValue();
      Sequence<IString> sourceSpan = SampledRule.toSystemSequence(span.tokens, tm2Sys);
      int numHits = sample.ub - sample.lb + 1;
      double sampleRate = sample.samples.size() / (double) numHits;
      List<Rule<IString>> rules = samplesToRules(sample.samples, span.tokens.length, sampleRate, sourceSpan);
      ruleCache.put(sourceSpan, rules);
    });
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
   * @param vocabSize 
   * @param vocabSize 
   */
  private void createLexCoocTable(int vocabSize) {
    logger.info("Creating lexical cooc table");
    // Constant chosen empirically
    coocTable = new LexCoocTable(7*vocabSize);
    // Iterate over every (symmetric) alignment point in parallel
    sa.getCorpus().getSegments().parallelStream().forEach(s -> {
      for(int i = 0; i < s.source.length; ++i) {
        final int srcId = s.source[i];
        if (s.isSourceUnaligned(i)) {
          coocTable.addCooc(srcId, LexCoocTable.NULL_ID);
        } else {
          int[] tgtAlign = s.f2e(i);
          for (int j : tgtAlign) {
            int tgtId = s.target[j];
            coocTable.addCooc(srcId, tgtId);
          }
        }
      }
      // Look for unaligned target words that were skipped in the loop
      // above.
      for(int i = 0; i < s.target.length; ++i) {
        if (s.isTargetUnaligned(i)) {
          int tgtId = s.target[i];
          coocTable.addCooc(LexCoocTable.NULL_ID, tgtId);
        }
      }
    });
    logger.info("");
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
    
    // Speed up higher-order queries with bounds from lower-order queries
    int[][][] searchBounds = new int[source.size()][source.size()+1][];
    
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
            int[] prefixBounds = (order > 1 && searchBounds[i][j-1] != null) ? searchBounds[i][j-1] : null;
            SuffixArraySample s = prefixBounds == null ? sa.sample(sourcePhrase, true, sampleSize)
                : sa.sample(sourcePhrase, true, sampleSize, prefixBounds[0], prefixBounds[1]);
            if (s.samples.size() == 0) {
              // This span is not present in the training data.
              misses[i][j] = true;
              return null;
            }
            searchBounds[i][j] = new int[]{s.lb, s.ub};
            int numHits = s.ub - s.lb + 1;
            double sampleRate = s.samples.size() / (double) numHits;
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
    
    // Organize rules by candidate translation and compute lexical scores
    // Choose the alignment template that occurs most often for each span.
    List<SampledRule> rules = samples.stream().flatMap(s -> extractRules(s, order).stream())
        .collect(Collectors.toList());
    Map<TargetSpan,Counter<SampledAlignment>> tgtToTemplate = new HashMap<>(rules.size());
    for (SampledRule rule : rules) {
      TargetSpan tgtSpan = new TargetSpan(rule.tgt);
      Counter<SampledAlignment> alTemps = tgtToTemplate.get(tgtSpan);
      if (alTemps == null) {
        alTemps = new ClassicCounter<>();
        tgtToTemplate.put(tgtSpan, alTemps);
      }
      alTemps.incrementCount(new SampledAlignment(rule));
    }

    // Collect phrase counts and choose the best alignment template
    // for each src => target rule.
    List<SampledRule> ruleList = new ArrayList<>(tgtToTemplate.size());
    List<Integer> histoGram = new ArrayList<>(tgtToTemplate.size());
    int ef_denom = rules.size();
    for (TargetSpan tgtSpan : tgtToTemplate.keySet()) {
      Counter<SampledAlignment> alTemps = tgtToTemplate.get(tgtSpan);
      SampledAlignment maxAlignment = Counters.argmax(alTemps);
      SampledRule maxRule = maxAlignment.rule;
      scoreLex(maxRule);
      ruleList.add(maxRule);
      histoGram.add((int) alTemps.totalCount());
    }
    int[] histogram = histoGram.stream().mapToInt(i -> i).toArray();
    
    // TODO(spenceg) Compute confidence intervals for phrase scores
    // MLE point estimates for now.
//    double[][] ci = ConfidenceIntervals.multinomialSison(histogram);
    
    List<Rule<IString>> scoredRules = new ArrayList<>(histogram.length);
    for (int r = 0; r < histogram.length; ++r) {
      SampledRule rule = ruleList.get(r);
      
      float[] scores;
      if (featureTemplate == FeatureTemplate.DENSE) {
        scores = new float[4];
        int eCnt = sa.count(rule.tgt, false);
        int adjustedCount = (int) (histogram[r] / sampleRate);
        
        // Clip if the adjustedCount overshoots the number of occurrences of the target string in the
        // bitext.
        adjustedCount = Math.min(adjustedCount, eCnt);
        
        scores[0] = (float) (Math.log(adjustedCount) - Math.log(eCnt));
        scores[1] = (float) Math.log(rule.lex_f_e);
        scores[2] = (float) (Math.log(histogram[r]) -  Math.log(ef_denom));
        scores[3] = (float) Math.log(rule.lex_e_f);
        
      
      } else if (featureTemplate == FeatureTemplate.DENSE_EXT) {
        scores = new float[6];
        int eCnt = sa.count(rule.tgt, false);
        int adjustedCount = (int) (histogram[r] / sampleRate);
        // Clip if the adjustedCount overshoots the number of occurrences of the target string in the
        // bitext.
        adjustedCount = Math.min(adjustedCount, eCnt);
        
        scores[0] = (float) (Math.log(adjustedCount) - Math.log(eCnt));
        // U. Germann's approximation
//        double num = eCnt - histogram[r] * sampleRate;
//        scores[0] = (float) (Math.log(histogram[r]) - Math.log(histogram[r] + num));
        
        scores[1] = (float) Math.log(rule.lex_f_e);
        scores[2] = (float) (Math.log(histogram[r]) - Math.log(ef_denom));
        scores[3] = (float) Math.log(rule.lex_e_f);
        
        // See train.CountFeatureExtractor -- the sampled count seems to work
        // better than the adjustedCount, which is an approximation of the global count.
        // In the offline extractor, this feature is tied to the filter corpus, so it
        // isn't really a global count either. Maybe tying the counts with the
        // specific sentence in question is what really matters. Almost like
        // segment-level domain adaptation.
//        scores[4] = histogram[r] > 1 ? (float) Math.log(histogram[r]) : 0.0f;
//        scores[5] = histogram[r] == 1 ? -1.0f : 0.0f;
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
  
  private static class SampledAlignment {
    public final SampledRule rule;
    private final int hashCode;
    public SampledAlignment(SampledRule rule) {
      this.rule = rule;
      this.hashCode = rule.getAlignmentKey();
    }
    @Override
    public String toString() { return this.rule.toString(); }
    @Override
    public int hashCode() { return hashCode; }
    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else if ( ! (o instanceof SampledAlignment)) {
        return false;
      } else {
        SampledAlignment other = (SampledAlignment) o;
        int[][] f2e = rule.f2e();
        int[][] f2eOther = other.rule.f2e();
        if (f2e.length != f2eOther.length) return false;
        for (int i = 0; i < f2e.length; ++i) {
          if ( ! Arrays.equals(f2e[i], f2eOther[i])) {
            return false;
          }
        }
        return true;
      }
    }
  }
  
  /**
   * Helper class for indexing rules.
   * 
   * @author Spence Green
   *
   */
  private class TargetSpan {
    private final int[] tgt;
    private final int hashCode;
    public TargetSpan(int[] tgt) {
      this.tgt = tgt;
      this.hashCode = MurmurHash.hash32(tgt, tgt.length, 1);
    }
    @Override
    public int hashCode() { return hashCode; }
    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else {
        TargetSpan other = (TargetSpan) o;
        return Arrays.equals(this.tgt, other.tgt);
      }
    }
    @Override
    public String toString() { 
      StringBuilder sb = new StringBuilder();
      for (int tgtId : tgt) {
        if (sb.length() > 0) sb.append(" ");
        sb.append(sa.getVocabulary().get(tgtId));
      }
      return sb.toString();
    }
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
    
    // Backward score p(f|e) -- Iterate over source
    double lex_f_e = 1.0;
    for (int i = rule.srcStartInclusive; i < rule.srcEndExclusive; ++i) {
      final int srcId = source[i];
      double feSum = 0.0;
      if (rule.saEntry.sentence.isSourceUnaligned(i)) {
        int c_f_e = coocTable.getJointCount(srcId, LexCoocTable.NULL_ID);
        int c_e = coocTable.getTgtMarginal(LexCoocTable.NULL_ID);
        feSum = c_f_e / (double) c_e;
        
      } else {
        int[] tgtAlign = rule.saEntry.sentence.f2e(i);
        for (int j : tgtAlign) {
          int tgtId = target[j];
          int c_f_e = coocTable.getJointCount(srcId, tgtId);
          int c_e = coocTable.getTgtMarginal(tgtId);
          feSum += (c_f_e / (double) c_e);
        }
        feSum /= (double) tgtAlign.length;
      }
      if (feSum == 0.0) feSum = MIN_LEX_PROB;
      lex_f_e *= feSum;
    }
    assert lex_f_e >= 0 && lex_f_e <= 1.0;
    
    // Backward score p(e|f) -- Iterate over target
    double lex_e_f = 1.0;
    for (int i = rule.tgtStartInclusive; i < rule.tgtEndExclusive; ++i) {
      final int tgtId = target[i];
      double efSum = 0.0;
      if (rule.saEntry.sentence.isTargetUnaligned(i)) {
        int c_e_f = coocTable.getJointCount(LexCoocTable.NULL_ID, tgtId);
        int c_f = coocTable.getSrcMarginal(LexCoocTable.NULL_ID);
        efSum = c_e_f / (double) c_f;
        
      } else {
        int[] srcAlign = rule.saEntry.sentence.e2f(i);
        for (int j : srcAlign) {
          final int srcId = source[j];
          int c_e_f = coocTable.getJointCount(srcId, tgtId);
          int c_f = coocTable.getSrcMarginal(srcId);
          efSum += (c_e_f / (double) c_f);
        }
        efSum /= (double) srcAlign.length;
        
      }
      if (efSum == 0.0) efSum = MIN_LEX_PROB;
      lex_e_f *= efSum;
    }
    assert lex_e_f >= 0.0 && lex_e_f <= 1.0;

    rule.lex_e_f = lex_e_f;
    rule.lex_f_e = lex_f_e;
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
      }
    }
    
    List<SampledRule> ruleList = new ArrayList<>();
    if (maxTarget < 0 || maxTarget-minTarget >= maxTargetPhrase) return ruleList;
    
    // Admissibility check
    for (int i = minTarget; i <= maxTarget; ++i) {
      int[] srcAligned = s.sentence.e2f(i);
      for (int j : srcAligned) {
        if (j < startSource || j >= endSource) {
          // Failed check
          return ruleList;
        }
      }
    }
    
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
    throw new UnsupportedOperationException("Not yet implemented. Use getRules().");
  }
  
  /**
   * A lexical co-occurrence table. This object is threadsafe.
   * 
   * @author Spence Green
   *
   */
  public static class LexCoocTable {

    public static final int NULL_ID = -1;
    private static final int MARGINALIZE = Integer.MIN_VALUE;
    
    private final ConcurrentHashMap<Long,AtomicInteger> counts;
    
    /**
     * Constructor.
     * 
     * @param initialCapacity
     */
    public LexCoocTable(int initialCapacity) {
      counts = new ConcurrentHashMap<>(initialCapacity);
    }
    
    /**
     * Add a word-word cooccurrence.
     * 
     * @param srcId
     * @param tgtId
     */
    public void addCooc(int srcId, int tgtId) {
      increment(pack(srcId, tgtId));
      increment(pack(MARGINALIZE, tgtId));
      increment(pack(srcId, MARGINALIZE));
    }
    
    private void increment(long key) {
      AtomicInteger counter = counts.get(key);
      if (counter == null) {
        counts.putIfAbsent(key, new AtomicInteger());
        counter = counts.get(key);
      }
      counter.incrementAndGet();
    }

    /**
     * Source marginal count.
     * 
     * @param srcId
     * @return
     */
    public int getSrcMarginal(int srcId) { return getJointCount(srcId, MARGINALIZE); }
    
    /**
     * Target marginal count.
     * 
     * @param tgtId
     * @return
     */
    public int getTgtMarginal(int tgtId) { return getJointCount(MARGINALIZE, tgtId); }
    
    /**
     * Joint count.
     * 
     * @param srcId
     * @param tgtId
     * @return
     */
    public int getJointCount(int srcId, int tgtId) { 
      AtomicInteger counter = counts.get(pack(srcId, tgtId));
      return counter == null ? 0 : counter.get();
    }
    
    /**
     * Number of entries in the table.
     * 
     * NOTE: This method is extremely slow. 
     * 
     * @return
     */
    public int size() { return counts.size(); }
    
    /**
     * Merge two interger ids into an unsigned long value. This is two unwrapped calls
     * to Integer.toUnsignedLong().
     * 
     * @param srcId
     * @param tgtId
     * @return
     */
    public long pack(int srcId, int tgtId) {
      return ((((long) srcId) & 0xffffffffL) << 32) | ((long) tgtId) & 0xffffffffL;
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
      DynamicTranslationModel<String> tm = DynamicTranslationModel.load(fileName, true);
      tm.createQueryCache(FeatureTemplate.DENSE_EXT);
      System.out.printf("Source cardinality: %d%n", tm.maxLengthSource());
      System.out.printf("Source cardinality: %d%n", tm.maxLengthTarget());
      System.out.printf("Cooc table size:    %d%n", tm.coocTable.size());
      System.out.printf("Vocab size:         %d%n", tm.sa.getVocabulary().size());
      
      // TODO(spenceg) Requires classmexer in the local directory.
//      System.out.printf("In-memory size: %d bytes%n", MemoryUtil.deepMemoryUsageOf(tm));
//      System.out.printf("In-memory size sa: %d bytes%n", MemoryUtil.deepMemoryUsageOf(tm.sa));
//      System.out.printf("In-memory size cooc: %d bytes%n", MemoryUtil.deepMemoryUsageOf(tm.coocTable));
//      System.out.printf("In-memory size rule cache: %d bytes%n", MemoryUtil.deepMemoryUsageOf(tm.ruleCache));
      
      // Read the source at once for accurate timing of queries
      List<Sequence<IString>> sourceFile = IStrings.tokenizeFile(inputFile);
      
      System.out.printf("#source segments: %d%n", sourceFile.size());
      
      long startTime = TimingUtils.startTime();
      int sourceId = 0;
      for (Sequence<IString> source : sourceFile) {
        tm.getRules(source, null, null, sourceId++, null);
      }
      double numSecs = TimingUtils.elapsedSeconds(startTime);
      System.out.printf("Sample time:\t%.3fs%n", numSecs);
      System.out.printf("Time/segment:\t%.3fs%n", numSecs / (double) sourceFile.size());
      
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
