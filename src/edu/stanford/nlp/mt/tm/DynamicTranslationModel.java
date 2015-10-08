package edu.stanford.nlp.mt.tm;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoSerializable;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.train.LexicalReorderingFeatureExtractor.ReorderingTypes;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.InputProperty;
import edu.stanford.nlp.mt.util.MurmurHash2;
import edu.stanford.nlp.mt.util.ParallelSuffixArrayEntry;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.ParallelSuffixArray;
import edu.stanford.nlp.mt.util.ParallelSuffixArray.SentencePair;
import edu.stanford.nlp.mt.util.ParallelSuffixArray.Span;
import edu.stanford.nlp.mt.util.ParallelSuffixArray.SuffixArraySample;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.util.ArraySequence;
import edu.stanford.nlp.mt.util.TimingUtils;
import edu.stanford.nlp.mt.util.TimingUtils.TimeKeeper;
import edu.stanford.nlp.mt.util.Vocabulary;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

/**
 * A dynamic translation model backed by a suffix array.
 * 
 * @author Spence Green
 *
 */
public class DynamicTranslationModel<FV> implements TranslationModel<IString,FV>,Serializable,KryoSerializable {

  private static final long serialVersionUID = 5876435802959430120L;
  
  public static final String FEATURE_PREFIX = "DYN";
  public static final String DEFAULT_NAME = "dynamic-tm";
  public static final int DEFAULT_SAMPLE_SIZE = 100;
  public static final int DEFAULT_MAX_PHRASE_LEN = 12;
  private static final int RULE_CACHE_THRESHOLD = 10000;
  private static final double MIN_LEX_PROB = 1e-5;
  
  /**
   * Parallelize TM queries. 
   */
  private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors();
  private static final ThreadPoolExecutor threadPool = (ThreadPoolExecutor) 
      Executors.newFixedThreadPool(NUM_THREADS, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
          Thread t = new Thread(r);
          t.setDaemon(true);
          return t;
        }
      });
  static {
    // Get ready for action.
    threadPool.prestartAllCoreThreads();
  }
  
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
  
  private static final Logger logger = LogManager.getLogger(DynamicTranslationModel.class);
  
  // Parameters
  protected transient boolean initialized;
  protected transient int maxSourcePhrase;
  protected transient int maxTargetPhrase;
  protected transient FeatureTemplate featureTemplate;
  protected transient RuleFeaturizer<IString, FV> featurizer;
  protected transient int sampleSize;
  protected transient String[] featureNames;
  protected transient String name;
  protected transient boolean reorderingEnabled;
  
  // Caches
  public transient LexCoocTable coocTable;
  protected transient Map<Sequence<IString>,List<Rule<IString>>> ruleCache;
  
  // Vocabulary translation arrays
  protected transient int[] sys2TM;
  protected transient int[] tm2Sys;
  
  /**
   * No-arg constructor for deserialization. Creates caches
   */
  public DynamicTranslationModel() {
    initialized = false;
  }
  
  /**
   * Constructor.
   * 
   * NOTE: This constructor does *not* create caches.
   * 
   * @param suffixArray
   */
  public DynamicTranslationModel(ParallelSuffixArray suffixArray) {
    this(suffixArray, DEFAULT_NAME);
  }
  
  /**
   * Constructor.
   * 
   * @param suffixArray
   * @param name
   */
  public DynamicTranslationModel(ParallelSuffixArray suffixArray, String name) {
    this.sa = suffixArray;
    this.initialized = false;
    this.maxSourcePhrase = DEFAULT_MAX_PHRASE_LEN;
    this.maxTargetPhrase = DEFAULT_MAX_PHRASE_LEN;
    this.sampleSize = DEFAULT_SAMPLE_SIZE;
    this.name = name;
    this.reorderingEnabled = false;
    setFeatureTemplate(FeatureTemplate.DENSE);
  }

  /**
   * Load a translation model from a serialized file.
   * 
   * @param filename
   * @param initializeSystemVocabulary
   * @return
   * @throws IOException
   */
  public static <FV> DynamicTranslationModel<FV> load(String filename, boolean initializeSystemVocabulary) throws IOException {
    return load(filename, initializeSystemVocabulary, filename);
  }

  /**
   * Load a translation model from a serialized file.
   * 
   * @param filename
   * @param initializeSystemVocabulary
   * @param name
   * @return
   * @throws IOException
   */
  @SuppressWarnings("unchecked")
  public static <FV> DynamicTranslationModel<FV> load(String filename, boolean initializeSystemVocabulary,
      String name) throws IOException {
    TimeKeeper timer = TimingUtils.start();
    DynamicTranslationModel<FV> tm = IOTools.deserialize(filename, DynamicTranslationModel.class);
    timer.mark("Deserialization");
    tm.maxSourcePhrase = DEFAULT_MAX_PHRASE_LEN;
    tm.maxTargetPhrase = DEFAULT_MAX_PHRASE_LEN;
    tm.sampleSize = DEFAULT_SAMPLE_SIZE;
    tm.name = name;
    tm.setFeatureTemplate(FeatureTemplate.DENSE);
    
    if (initializeSystemVocabulary) tm.populateSystemVocabulary();
    // Id arrays must be created after any modification of the system vocabulary.
    tm.createIdArrays();
    timer.mark("Vocabulary setup");
    
    // Lex cache must be created before any rules can be scored.
    tm.createLexCoocTable(tm.sa.getVocabulary().size());
    timer.mark("Cooc table");

    logger.info("Timing: {}", timer);
    return tm;
  }
  

  @Override
  public void write(Kryo kryo, Output output) {
    kryo.writeObject(output, sa);
  }

  @Override
  public void read(Kryo kryo, Input input) {
    sa = kryo.readObject(input, ParallelSuffixArray.class);
  }
  
  /**
   * Configure this TM as a foreground translation model.
   * 
   * @param name
   */
  public synchronized void configureAsForegroundTM(FeatureTemplate t) {
    TimeKeeper timer = TimingUtils.start();
    maxSourcePhrase = DEFAULT_MAX_PHRASE_LEN;
    maxTargetPhrase = DEFAULT_MAX_PHRASE_LEN;
    sampleSize = DEFAULT_SAMPLE_SIZE;
    this.name = Phrasal.TM_FOREGROUND_NAME;
    setFeatureTemplate(t);
    
    createIdArrays();
    timer.mark("Id arrays");
    
    // Lex cache must be created before any rules can be scored.
    createLexCoocTable(sa.getVocabulary().size());
    timer.mark("Cooc table");
    
    createQueryCache(t);
    timer.mark("Query cache");
    logger.info("Timing results: {}", timer);
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
      Sequence<IString> sourceSpan = toSequence(span.tokens);
      int numHits = sample.ub - sample.lb + 1;
      double sampleRate = sample.samples.size() / (double) numHits;
      List<Rule<IString>> rules = samplesToRules(sample.samples, span.tokens.length, sampleRate, sourceSpan);
      ruleCache.put(sourceSpan, rules);
    });
  }
  
  /**
   * Create mappings between the system vocabulary and the translation model vocabulary. 
   * 
   * IMPORTANT: This method must add any new word types from the TM to the
   * system vocabulary.
   */
  private void createIdArrays() {
    final Vocabulary tmVocab = sa.getVocabulary();
    // Augment the system vocabulary
    int tmSize = tmVocab.size();
    tm2Sys = new int[tmSize];
    IntStream.range(0, tmSize).parallel().forEach(i -> {
      String word = tmVocab.get(i);
      int wordIndex = Vocabulary.systemIndexOf(word);
      if (wordIndex < 0) wordIndex = Vocabulary.systemAdd(word);
      tm2Sys[i] = wordIndex;
    });
    // Now create the mapping from the (augmented) system vocabulary
    final int sysSize = Vocabulary.systemSize();
    sys2TM = new int[sysSize];
    IntStream.range(0, sysSize).parallel().forEach(i -> {
      sys2TM[i] = tmVocab.indexOf(Vocabulary.systemGet(i));
    });
  }

  /**
   * Get the underlying suffix array.
   * 
   * @return
   */
  public ParallelSuffixArray getSuffixArray() { return sa; }
    
  /**
   * Setup cache for lexical translations by iterating over every alignment point
   * in the underlying corpus.
   * @param vocabSize 
   * @param vocabSize 
   */
  private void createLexCoocTable(int vocabSize) {
    logger.info("Creating lexical cooc table");
    // Constant chosen empirically
    coocTable = new LexCoocTable(10*vocabSize);
    // Iterate over every (symmetric) alignment point in parallel
    sa.stream().forEach(s -> {
      for(int i = 0, sz = s.sourceLength(); i < sz; ++i) {
        final int srcId = s.source(i);
        if (s.isSourceUnaligned(i)) {
          coocTable.addCooc(srcId, LexCoocTable.NULL_ID);
        } else {
          // Don't discriminate among alignment links.
          // See {@link edu.stanford.nlp.mt.train.MosesPharoahFeatureExtractor#FeaturizeSentence}
          // TODO(spenceg) Maybe we should discriminate? Will greatly increase the size of the
          // of the cooc table.
          int[] tgtAlign = s.f2e(i);
          for (int j : tgtAlign) {
            int tgtId = s.target(j);
            coocTable.addCooc(srcId, tgtId);
          }
        }
      }
      // Look for unaligned target words that were skipped in the loop
      // above.
      for(int i = 0, sz = s.targetLength(); i < sz; ++i) {
        if (s.isTargetUnaligned(i)) {
          int tgtId = s.target(i);
          coocTable.addCooc(LexCoocTable.NULL_ID, tgtId);
        }
      }
    });
  }

  /**
   * Print out the full bitext.
   * 
   * @param writer
   */
  public void printBitext(PrintWriter writer) {
    sa.stream().forEach(s -> {
      writer.println(s.toString());
    });
  }
  
  public int bitextSize() {
    return sa.numSentences();
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

  /**
   * Turn on the reordering features.
   */
  public void setReorderingScores() {
    this.reorderingEnabled = true;
  }
  
  /**
   * Set the maximum source phrase length.
   * 
   * @param dim
   */
  public void setMaxSourcePhrase(int dim) {
    maxSourcePhrase = dim;
  }
  
  /**
   * Set the maximum target phrase length.
   * 
   * @param dim
   */
  public void setMaxTargetPhrase(int dim) {
    maxTargetPhrase = dim;
  }
  
  /**
   * Set the sample size.
   * 
   * @param sz
   */
  public void setSampleSize(int sz) {
    this.sampleSize = sz;
  }
  
  /**
   * Inject the TM vocabulary into the system vocabulary.
   */
  private void populateSystemVocabulary() {
    final Vocabulary tmVocab = sa.getVocabulary();
    IntStream.range(0, tmVocab.size()).parallel().forEach(i -> {
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

  /**
   * Get the name of this translation model.
   */
  @Override
  public String getName() {
    return name;
  }
  
  /**
   * Set the name of the TM.
   */
  @Override
  public void setName(String name) { this.name = name; }
  
  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
  
  @Override
  public String toString() {
    return String.format("bitext_size: %d  phraselen: %d/%d",
        sa.numSentences(), maxSourcePhrase, maxTargetPhrase);
  }
  
  @SuppressWarnings({ "unchecked", "rawtypes" })
  @Override
  public List<ConcreteRule<IString, FV>> getRules(Sequence<IString> source,
      InputProperties sourceInputProperties, int sourceInputId,
      Scorer<FV> scorer) {
    if (source == null || source.size() == 0) return Collections.emptyList();
    
    final List<ConcreteRule<IString,FV>> concreteRules = new ArrayList<>(source.size() * source.size() * 100);
    
    final int[] sourceArray = toTMArray(source);
    
    // Zhang and Vogel (2005) trick -- prune higher-order queries using lower-order misses
    final boolean[][] misses = new boolean[source.size()][source.size()+1];
    
    // Speed up higher-order queries with bounds from lower-order queries
    final int[][][] searchBounds = new int[source.size()][source.size()+1][];
    
    final ExecutorCompletionService<QueryResult<FV>> workQueue = 
        new ExecutorCompletionService<>(threadPool);
    
    // Iterate over source span lengths
    for (int len = 1, longestSourcePhrase = Math.min(maxSourcePhrase, source.size()); 
        len <= longestSourcePhrase; len++) {
      // Filter higher-order ranges based on lower-order misses
      int numTasks = 0;
      for (int i = 0, sz = source.size() - len; i <= sz; ++i) {
        final int j = i + len;
        
        // Check lower-order n-grams for misses
        boolean miss = (len == 1 && sourceArray[i] < 0);
        for(int a = i, b = i + len - 1; len > 1 && b <= j && ! miss; ++a, ++b) {
          miss = misses[a][b];
        }
        if (miss) {
          misses[i][j] = true;
        } else {
          final int[] prefixBounds = (len > 1 && searchBounds[i][j-1] != null) ? searchBounds[i][j-1] : null;
          workQueue.submit(new ExtractionTask(i, j, source, sourceInputProperties, 
              sourceInputId, scorer, sourceArray, prefixBounds));
          ++numTasks;
        }
      }
      
      if (numTasks == 0) {
        // There can't be any higher order matches
        break;
      } 
      
      // Wait for results
      try {
        for (int k = 0; k < numTasks; ++k) {
          QueryResult<FV> result = workQueue.take().get();
          if (result != null) {
            int i = result.i;
            int j = result.j;
            misses[i][j] = result.miss;
            searchBounds[i][j] = result.searchBounds;
            concreteRules.addAll(result.ruleList);
          }
        }
      } catch (InterruptedException | ExecutionException e) {
        logger.error("input {}: rule extraction failed for order {}", sourceInputId, len);
        e.printStackTrace();
        return Collections.emptyList();
      }
    }
    
    // Concatenate foreground model rules
    if (sourceInputProperties.containsKey(InputProperty.ForegroundTM)) {
      DynamicTranslationModel<FV> foregroundTM = 
          (DynamicTranslationModel) sourceInputProperties.get(InputProperty.ForegroundTM);
      final InputProperties fgProperties = new InputProperties(sourceInputProperties);
      fgProperties.remove(InputProperty.ForegroundTM);
      int bgSize = concreteRules.size();
      concreteRules.addAll(foregroundTM.getRules(source, fgProperties, sourceInputId, scorer));
      logger.info("input {}: adding {} rules from foreground model", sourceInputId, concreteRules.size() - bgSize);
    }
    
    return concreteRules;
  }
  
  /**
   * Extract rules from suffix array.
   * 
   * @author Spence Green
   *
   */
  private class ExtractionTask implements Callable<QueryResult<FV>> {
    private final int i;
    private final int j;
    private final Sequence<IString> source;
    private InputProperties sourceInputProperties;
    private int sourceInputId;
    private Scorer<FV> scorer;
    private int[] sourceArray;
    private int[] prefixBounds;

    public ExtractionTask(int i, int j, Sequence<IString> source, InputProperties sourceInputProperties, 
        int sourceInputId, Scorer<FV> scorer, int[] sourceArray, int[] prefixBounds) {
      this.i = i;
      this.j = j;
      this.source = source;
      this.sourceInputProperties = sourceInputProperties;
      this.sourceInputId = sourceInputId;
      this.scorer = scorer;
      this.sourceArray = sourceArray;
      this.prefixBounds = prefixBounds;
    }

    @Override
    public QueryResult<FV> call() throws Exception {
      final int order = j - i;
      final QueryResult<FV> result = new QueryResult<>(i, j);

      // Generate rules for this span
      final Sequence<IString> sourceSpan = source.subsequence(i, j);
      final CoverageSet sourceCoverage = new CoverageSet(source.size());
      sourceCoverage.set(i, j);
      List<Rule<IString>> rules = ruleCache == null ? null : ruleCache.get(sourceSpan);
      if (rules == null) {
        // Sample from the suffix array
        final int[] sourcePhrase = Arrays.copyOfRange(sourceArray, i, j);
        final SuffixArraySample corpusSample = prefixBounds == null ? sa.sample(sourcePhrase, sampleSize)
            : sa.sample(sourcePhrase, sampleSize, prefixBounds[0], prefixBounds[1]);
        if (corpusSample.size() == 0) {
          // This span is not present in the training data.
          rules = Collections.emptyList();
          result.miss = true;
          
        } else {
          result.searchBounds = new int[]{corpusSample.lb, corpusSample.ub};
          final int numHits = corpusSample.ub - corpusSample.lb + 1;
          final double sampleRate = corpusSample.size() / (double) numHits;
          rules = samplesToRules(corpusSample.samples, order, sampleRate, sourceSpan);
        }
      }
      // Extract rules
      result.ruleList = new ArrayList<>(rules.size());
      for (Rule<IString> r : rules) {
        result.ruleList.add(new ConcreteRule<>(
            r, sourceCoverage, featurizer, scorer, source, sourceInputId, sourceInputProperties));
      }
      return result;
    }
  }
  
  private static class QueryResult<FV> {
    public final int i;
    public final int j;
    public List<ConcreteRule<IString,FV>> ruleList;
    public int[] searchBounds;
    public boolean miss = false;
    public QueryResult(int i, int j) {
      this.i = i;
      this.j = j;
    }
  }
  
  
  /**
   * Perform a source lookup into the underlying suffix array. Performs whitespace tokenization
   * of the input.
   * 
   * @param sourceQuery
   * @param numResults
   * @return
   */
  public List<ParallelSuffixArrayEntry> lookupSource(String sourceQuery, int numResults) {
    final int[] sourcePhrase = toTMArray(IStrings.tokenize(sourceQuery));
    for (int id : sourcePhrase) {
      if (id < 0) return Collections.emptyList();
    }
    SuffixArraySample sample = sa.sample(sourcePhrase, numResults);
    return sample.samples.stream().map(s -> s.getParallelEntry()).collect(Collectors.toList());
  }
  
  /**
   * Perform a target lookup into the underlying suffix array. Performs whitespace tokenization
   * of the input.
   * 
   * @param targetQuery
   * @param numResults
   * @return
   */
  public List<ParallelSuffixArrayEntry> lookupTarget(String targetQuery, int numResults) {
    final int[] targetPhrase = toTMArray(IStrings.tokenize(targetQuery));
    for (int id : targetPhrase) {
      if (id < 0) return Collections.emptyList();
    }
    SuffixArraySample sample = sa.sampleTarget(targetPhrase, numResults);
    return sample.samples.stream().map(s -> s.getParallelEntry()).collect(Collectors.toList());
  }
  
  /**
   * 
   * @param source
   * @return
   */
  public int getSourceLexCount(IString source) {
    int id = toTMId(source);
    return id >= 0 ? coocTable.getSrcMarginal(id) : 0;
  }
  
  public int getSourceUnalignedCount(IString source) {
    int id = toTMId(source);
    return id >= 0 ? coocTable.getJointCount(id, LexCoocTable.NULL_ID) : 0;    
  }
  
  /**
   * 
   * @param target
   * @return
   */
  public int getTargetLexCount(IString target) {
    int id = toTMId(target);
    return id >= 0 ? coocTable.getTgtMarginal(id) : 0;
  }
  
  public int getTargetUnalignedCount(IString target) {
    int id = toTMId(target);
    return id >= 0 ? coocTable.getJointCount(LexCoocTable.NULL_ID, id) : 0;    
  }
  
  /**
   * 
   * @param source
   * @param target
   * @return
   */
  public int getJointLexCount(IString source, IString target) {
    int srcId = toTMId(source);
    int tgtId = toTMId(target);
    return srcId >= 0 && tgtId >= 0 ? coocTable.getJointCount(srcId, tgtId) : 0;
  }
  
  /**
   * Returns the TM vocabulary id of the item if it is in the TM vocabulary
   * and -1 otherwise.
   * 
   * @param word
   * @return
   */
  private int toTMId(IString word) {
    return word.id < sys2TM.length ? sys2TM[word.id] : Vocabulary.UNKNOWN_ID;
  }
  
  /**
   * Convert a sequence to translation model indices.
   * 
   * @param sequence
   * @return
   */
  private int[] toTMArray(Sequence<IString> sequence) {
    final int sourceSize = sequence.size();
    int[] tmIds = new int[sourceSize];
    for (int i = 0; i < sourceSize; ++i) {
      // TODO(spenceg) The array must be grown if material is added to the underlying suffix array
      tmIds[i] = toTMId(sequence.get(i));
    }
    return tmIds;
  }
  
  /**
   * Convert translation model indices to a sequence.
   * 
   * @param tmTokens
   * @return
   */
  private Sequence<IString> toSequence(int[] tmTokens) {
    final IString[] tokens = new IString[tmTokens.length];
    for (int i = 0; i < tmTokens.length; ++i) {
      assert tmTokens[i] < tm2Sys.length;
      int systemId = tm2Sys[tmTokens[i]];
      tokens[i] = new IString(systemId);
    }
    return new ArraySequence<>(true, tokens);
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
  private List<Rule<IString>> samplesToRules(List<SentencePair> samples, final int order, 
      double sampleRate, Sequence<IString> sourceSpan) {
    // Extract rules from sentence pairs
    final List<SampledRule> rules = new ArrayList<>(2*samples.size());
    for (SentencePair sample : samples) rules.addAll(extractRules(sample, order, maxTargetPhrase));
    
    // Collect counts
    Map<TargetSpan,Counter<AlignmentTemplate>> tgtToTemplate = new HashMap<>(rules.size());
    Map<SampledRule,ReorderingCounts> reorderingCounts = reorderingEnabled ? new HashMap<>(rules.size()) : null;
    for (SampledRule rule : rules) {
      TargetSpan tgtSpan = new TargetSpan(rule.tgt);
      Counter<AlignmentTemplate> alTemps = tgtToTemplate.get(tgtSpan);
      if (alTemps == null) {
        alTemps = new ClassicCounter<>();
        tgtToTemplate.put(tgtSpan, alTemps);
      }
      alTemps.incrementCount(new AlignmentTemplate(rule));
      
      if (reorderingCounts != null) {
        ReorderingCounts counts = reorderingCounts.get(rule);
        if (counts == null) {
          counts = new ReorderingCounts();
          reorderingCounts.put(rule, counts);
        }
        counts.incrementForward(rule.forwardOrientation());
        counts.incrementBackward(rule.backwardOrientation());
      }
    }

    // Choose the best alignment template
    // for each src => target rule.
    List<TargetSpan> keys = new ArrayList<>(tgtToTemplate.keySet());
    List<SampledRule> ruleList = new ArrayList<>(tgtToTemplate.size());
    int[] histogram = new int[keys.size()];
    final int ef_denom = rules.size();
    for (int i = 0; i < histogram.length; ++i) {
      TargetSpan tgtSpan = keys.get(i);
      Counter<AlignmentTemplate> alTemps = tgtToTemplate.get(tgtSpan);
      // Note that the argmax alignment is chosen independent of the model.
      AlignmentTemplate maxAlignment = Counters.argmax(alTemps);
      SampledRule maxRule = maxAlignment.rule;
      scoreLex(maxRule);
      ruleList.add(maxRule);
      histogram[i] = (int) alTemps.totalCount();
    }
    
    List<Rule<IString>> scoredRules = new ArrayList<>(ruleList.size());
    for (int r = 0, sz = ruleList.size(); r < sz; ++r) {
      final SampledRule rule = ruleList.get(r);
      
      float[] scores;
      if (featureTemplate == FeatureTemplate.DENSE) {
        scores = new float[4];        
        int eCnt = sa.count(rule.tgt, false);
        assert eCnt > 0 : Arrays.toString(rule.tgt);
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
        assert eCnt > 0 : Arrays.toString(rule.tgt);
        int adjustedCount = (int) (histogram[r] / sampleRate);
        // Clip if the adjustedCount overshoots the number of occurrences of the target string in the
        // bitext.
        adjustedCount = Math.min(adjustedCount, eCnt);
        
        scores[0] = (float) (Math.log(adjustedCount) - Math.log(eCnt));
        scores[1] = (float) Math.log(rule.lex_f_e);
        scores[2] = (float) (Math.log(histogram[r]) - Math.log(ef_denom));
        scores[3] = (float) Math.log(rule.lex_e_f);
        scores[4] = adjustedCount > 1 ? (float) Math.log(adjustedCount) : 0.0f;
        scores[5] = adjustedCount == 1 ? -1.0f : 0.0f;
                
      } else {
        throw new UnsupportedOperationException("Not yet implemented.");
      }

      Rule<IString> scoredRule = convertRule(rule, scores, featureNames, sourceSpan, this.tm2Sys);
      if (this.reorderingEnabled) {
        scoredRule.reoderingScores = reorderingCounts.get(rule).getFeatureVector();
        scoredRule.forwardOrientation = rule.forwardOrientation();
        scoredRule.backwardOrientation = rule.backwardOrientation();
      }
      scoredRules.add(scoredRule);
    }
    return scoredRules;
  }
  
  /**
   * Convert a SampledRule to a Rule.
   * 
   * @param rule
   * @param scores
   * @param featureNames
   * @param sourceSpan
   * @param tm2Sys
   * @return
   */
  private Rule<IString> convertRule(SampledRule rule, float[] scores, String[] featureNames,
      Sequence<IString> sourceSpan, int[] tm2Sys) {
    PhraseAlignment alignment = new PhraseAlignment(rule.e2f());
    Sequence<IString> tgtSeq = toSequence(rule.tgt);
    return new Rule<IString>(scores, featureNames, tgtSeq, sourceSpan, alignment, name);
  }
  
  /**
   * Counter for reordering orientations.
   * 
   * @author Spence Green
   *
   */
  private static class ReorderingCounts {
    // TODO(spenceg) For now, replicating LexicalReorderingFeatureExtractor, which uses add-alpha
    // smoothing.
    private static float ALPHA = 0.5f;
    private static final int MODEL_SIZE = 3;
    private final int[] forwardCounts = new int[MODEL_SIZE];
    private final int[] backwardCounts = new int[MODEL_SIZE];
    int forwardDenom = 0;
    int backwardDenom = 0;
    
    public void incrementForward(ReorderingTypes type) {
      assert type.ordinal() < forwardCounts.length;
      forwardCounts[type.ordinal()]++;
      ++forwardDenom;
    }
    
    public void incrementBackward(ReorderingTypes type) {
      assert type.ordinal() < backwardCounts.length;
      backwardCounts[type.ordinal()]++;
      ++backwardDenom;
    }
    
    /**
     * The order of these feature values must correspond to {@link LexicalReorderingTable#msdBidirectionalPositionMapping}.
     * 
     * @return
     */
    public float[] getFeatureVector() {
      float[] values = new float[6];
      for (int i = 0; i < backwardCounts.length; ++i)
        values[i] = (float) Math.log((backwardCounts[i]+ALPHA) / ((float) backwardDenom + (MODEL_SIZE * ALPHA)));
      for (int i = 0; i < forwardCounts.length; ++i)
        values[MODEL_SIZE + i] = (float) Math.log((forwardCounts[i]+ALPHA) / ((float) forwardDenom + (MODEL_SIZE * ALPHA)));
      return values;
    }
  }
  
  /**
   * A wrapper around a rule to indicate its alignment template.
   * 
   * @author Spence Green
   *
   */
  private class AlignmentTemplate {
    public final SampledRule rule;
    private final int hashCode;
    public AlignmentTemplate(SampledRule rule) {
      this.rule = rule;
      this.hashCode = MurmurHash2.hash32(rule.f2eAll(), rule.sourceLength(), 1) ^ 
          MurmurHash2.hash32(rule.e2fAll(), rule.targetLength(), 1);
    }
    @Override
    public String toString() { return rule.toString(); }
    @Override
    public int hashCode() { return hashCode; }
    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else {
        AlignmentTemplate other = (AlignmentTemplate) o;
        return rule.targetLength() == other.rule.targetLength() &&
            Arrays.equals(rule.e2fAll(), other.rule.e2fAll()) &&
            Arrays.equals(rule.f2eAll(), other.rule.f2eAll());
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
      this.hashCode = MurmurHash2.hash32(tgt, tgt.length, 1);
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
      return Arrays.stream(tgt).mapToObj(i -> sa.getVocabulary().get(i))
          .collect(Collectors.joining(" "));
    }
  }
  
  /**
   * Compute dense lexical probabilities from the table of global
   * co-occurrences.
   * 
   * @param rule
   */
  private void scoreLex(SampledRule rule) {
    // Backward score p(f|e) -- Iterate over source
    double lex_f_e = 1.0;
    for (int i = rule.srcStartInclusive; i < rule.srcEndExclusive; ++i) {
      final int srcId = rule.sentencePair.source(i);
      double feSum = 0.0;
      if (rule.sentencePair.isSourceUnaligned(i)) {
        int c_f_e = coocTable.getJointCount(srcId, LexCoocTable.NULL_ID);
        int c_e = coocTable.getTgtMarginal(LexCoocTable.NULL_ID);
        feSum = c_f_e / (double) c_e;
        
      } else {
        int[] tgtAlign = rule.sentencePair.f2e(i);
        for (int j : tgtAlign) {
          int tgtId = rule.sentencePair.target(j);
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
      final int tgtId = rule.sentencePair.target(i);
      double efSum = 0.0;
      if (rule.sentencePair.isTargetUnaligned(i)) {
        int c_e_f = coocTable.getJointCount(LexCoocTable.NULL_ID, tgtId);
        int c_f = coocTable.getSrcMarginal(LexCoocTable.NULL_ID);
        efSum = c_e_f / (double) c_f;
        
      } else {
        int[] srcAlign = rule.sentencePair.e2f(i);
        for (int j : srcAlign) {
          final int srcId = rule.sentencePair.source(j);
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
   * A hash-based lexical co-occurrence table.
   * 
   * NOTE: This class is not threadsafe.
   * 
   * @author Spence Green
   *
   */
  private class LexCoocTable {

    public static final int NULL_ID = Integer.MIN_VALUE + 1;
    private static final int MARGINALIZE = Integer.MIN_VALUE;
    
    // Use primitive long->int map to avoid boxing/unboxing costs.
    private final Long2IntMap counts;
    
    /**
     * Constructor.
     * 
     * @param initialCapacity
     */
    public LexCoocTable(int initialCapacity) {
      counts = new Long2IntOpenHashMap(initialCapacity);
      counts.defaultReturnValue(0);
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
      counts.put(key, counts.get(key) + 1);
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
      return counts.get(pack(srcId, tgtId));
    }
    
    /**
     * Number of entries in the table.
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
   * Extract admissible phrase pairs from the sampled sentence.
   * This is the "pattern matching" algorithm of Lopez (2008).
   * 
   * @param sentencePair
   * @param length
   * @param maxTargetPhrase
   * @return
   */
  public static List<SampledRule> extractRules(SentencePair sentencePair, int length, int maxTargetPhrase) {
    // Find the target span
    int minTarget = Integer.MAX_VALUE;
    int maxTarget = -1;
    final int startSource = sentencePair.wordPosition;
    final int endSource = startSource + length;
    for(int sourcePos = startSource; sourcePos < endSource; sourcePos++) {
      assert sourcePos < sentencePair.sourceLength() : String.format("[%d,%d) %d %d ", startSource, endSource, sourcePos, sentencePair.sourceLength());
      if ( ! sentencePair.isSourceUnaligned(sourcePos)) {
        int[] targetPositions = sentencePair.f2e(sourcePos);
        for(int targetPos : targetPositions) {
          if (targetPos < minTarget) {
            minTarget = targetPos;
          }
          if (targetPos > maxTarget) {
            maxTarget = targetPos;
          }
        }
      }
    }
    
    if (maxTarget < 0 || maxTarget-minTarget >= maxTargetPhrase) return Collections.emptyList();
    
    // Admissibility check
    for (int i = minTarget; i <= maxTarget; ++i) {
      if ( ! sentencePair.isTargetUnaligned(i)) {
        int[] srcPositions = sentencePair.e2f(i);
        for (int sourcePos : srcPositions) {
          if (sourcePos < startSource || sourcePos >= endSource) {
            // Failed check
            return Collections.emptyList();
          }
        }
      }
    }
    
    // "Loose" heuristic to grow the target
    // Try to grow the left bound of the target
    List<SampledRule> ruleList = new ArrayList<>();
    for(int startTarget = minTarget; (startTarget >= 0 &&
        startTarget > maxTarget-maxTargetPhrase &&
        (startTarget == minTarget || sentencePair.isTargetUnaligned(startTarget))); startTarget--) {

      // Try to grow the right bound of the target
      for (int endTarget=maxTarget; (endTarget < sentencePair.targetLength() &&
          endTarget < startTarget+maxTargetPhrase && 
          (endTarget==maxTarget || sentencePair.isTargetUnaligned(endTarget))); endTarget++) {
          SampledRule r = new SampledRule(startSource, endSource, startTarget, endTarget + 1, sentencePair);
          ruleList.add(r);
      }
    }
    return ruleList;
  }
  
  /**
   * Read an input file and extract rules from a model.
   * 
   * @param args
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 2) {
      System.err.printf("Usage: java %s tm_file source_file%n", DynamicTranslationModel.class.getName());
      System.exit(-1);
    }
    String fileName = args[0];
    String inputFile = args[1];
    TimeKeeper timer = TimingUtils.start();
    DynamicTranslationModel<String> tm = DynamicTranslationModel.load(fileName, true, DEFAULT_NAME);
    tm.setReorderingScores();
    timer.mark("Load");
    tm.createQueryCache(FeatureTemplate.DENSE_EXT);
    timer.mark("Cache creation");

    //      tm.sa.print(true, new PrintWriter(System.out));

    // NOTE: Requires classmexer in the local directory.
    //      System.out.printf("In-memory size: %d bytes%n", MemoryUtil.deepMemoryUsageOf(tm));
    //      System.out.printf("In-memory size sa: %d bytes%n", MemoryUtil.deepMemoryUsageOf(tm.sa));
    //      System.out.printf("In-memory size cooc: %d bytes%n", MemoryUtil.deepMemoryUsageOf(tm.coocTable));
    //      System.out.printf("In-memory size rule cache: %d bytes%n", MemoryUtil.deepMemoryUsageOf(tm.ruleCache));

    // Read the source at once for accurate timing of queries
    List<Sequence<IString>> sourceFile = IStrings.tokenizeFile(inputFile);
    timer.mark("Source file loading");

    long startTime = TimingUtils.startTime();
    int sourceId = 0, numRules = 0;
    InputProperties inProps = new InputProperties();
    for (Sequence<IString> source : sourceFile) {
      numRules += tm.getRules(source, inProps, sourceId++, null).size();
    }
    double queryTimeMillis = TimingUtils.elapsedMillis(startTime);
    timer.mark("Query");
    
    startTime = TimingUtils.startTime();
    int numSAQueries = 0;
    for (Sequence<IString> source : sourceFile) {
      for (Sequence<IString> ngram : Sequences.ngrams(source, 2)) {
        int[] query = new int[ngram.size()];
        query[0] = tm.toTMId(ngram.get(0));
        if (ngram.size() == 2) query[1] = tm.toTMId(ngram.get(1));
        boolean doQuery = Arrays.stream(query).allMatch(q -> q >= 0);
        if (doQuery) tm.sa.count(query, true);
        ++numSAQueries;
      }
    }
    double saTime = TimingUtils.elapsedSeconds(startTime);
    timer.mark("SA Query");
    
    System.out.printf("SA src size:        %d%n", tm.sa.sourceSASize());
    System.out.printf("SA tgt size:        %d%n", tm.sa.targetSASize());
    System.out.printf("SA num sentences:   %d%n", tm.sa.numSentences());
    System.out.printf("SA vocab size:      %d%n", tm.sa.getVocabulary().size());
    System.out.printf("TM src cardinality: %d%n", tm.maxLengthSource());
    System.out.printf("TM tgt cardinality: %d%n", tm.maxLengthTarget());
    System.out.printf("TM Cooc table size: %d%n", tm.coocTable.size());
    System.out.println("===========");
    System.out.printf("#source segments:   %d%n", sourceFile.size());
    System.out.printf("Timing: %s%n", timer);
    System.out.printf("Time/segment: %.2fms%n", queryTimeMillis / (double) sourceFile.size());
    System.out.printf("#rules: %d%n", numRules);
    System.out.printf("#segments: %d%n", sourceFile.size());
    System.out.printf("#sa queries: %d%n", numSAQueries);
    System.out.printf("Time/sa query: %.5fs%n", saTime);
  }
}
