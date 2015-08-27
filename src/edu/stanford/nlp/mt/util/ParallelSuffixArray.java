package edu.stanford.nlp.mt.util;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoSerializable;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import edu.stanford.nlp.mt.util.TimingUtils.TimeKeeper;

/**
 * An implementation of a parallel suffix array.
 * 
 * NOTE: The fields are protected, non-final for fast serialization/deserialization.
 * 
 * @author Spence Green
 *
 */
public class ParallelSuffixArray implements Serializable,KryoSerializable {

  private static final long serialVersionUID = -5403502473957235135L;

  private static final Logger logger = LogManager.getLogger(ParallelSuffixArray.class);
  
  protected int[] srcBitext;
  protected int[] f2e;
  protected int[] tgtBitext;
  protected int[] e2f;
  protected int[] srcSuffixArray; 
  protected int[] tgtSuffixArray;
  
  protected int numSentences;
  protected Vocabulary vocabulary;
  
  // Cache unigram positions in the target for the count() function.
  // The sample function already supports initialization with bounds, which
  // the calling method should maintain.
  protected transient int[] tgtCountLBCache;
  protected transient int[] tgtCountUBCache;
  
  /**
   * No-arg constructor for deserialization.
   */
  public ParallelSuffixArray() {}

  /**
   * Constructor. Careful. This constructor doubles peak memory.
   * 
   * @param corpus
   */
  public ParallelSuffixArray(ParallelCorpus corpus) {
    loadCorpus(corpus);
  }
  
  /**
   * Constructor. Memory-efficient for large files.
   * 
   * @param sourceFile
   * @param targetFile
   * @param alignFile
   * @param expectedSize
   * @throws IOException 
   */
  public ParallelSuffixArray(String sourceFile, String targetFile, String alignFile) throws IOException {
    loadCorpus(sourceFile, targetFile, alignFile);
  }
  

  @Override
  public void write(Kryo kryo, Output output) {
    writeArray(srcBitext, output);
    writeArray(tgtBitext, output);
    writeArray(e2f, output);
    writeArray(f2e, output);
    writeArray(srcSuffixArray, output);
    writeArray(tgtSuffixArray, output);
    output.writeInt(numSentences, true);
    kryo.writeObject(output, vocabulary);
  }

  private static void writeArray(int[] arr, Output output) {
    output.writeInt(arr.length, true);
    output.writeInts(arr, true);
  }

  @Override
  public void read(Kryo kryo, Input input) {
    srcBitext = readArray(input);
    tgtBitext = readArray(input);
    e2f = readArray(input);
    f2e = readArray(input);
    srcSuffixArray = readArray(input);
    tgtSuffixArray = readArray(input);
    numSentences = input.readInt(true);
    vocabulary = kryo.readObject(input, Vocabulary.class);
  }
  
  private static int[] readArray(Input input) {
    int len = input.readInt(true);
    return input.readInts(len, true);
  }

  /**
   * Get the index associated with this suffix array.
   * 
   * @return
   */
  public Vocabulary getVocabulary() { return vocabulary; }
  
  /**
   * Return a stream of the sentence pairs in this bitext.
   * 
   * @return
   */
  public Stream<SentencePair> stream() {
    return IntStream.range(0, srcBitext.length).mapToObj(i -> {
      if (srcBitext[i] < 0) {
        return new SentencePair(i-1);
      } else {
        return null;
      }
    }).filter(o -> o != null);
  }
  
  /**
   * Return a stream of the sentence pairs in this bitext.
   * 
   * @return
   */
  public Stream<SentencePair> parallelStream() {
    return IntStream.range(0, srcBitext.length).parallel().mapToObj(i -> {
      if (srcBitext[i] < 0) {
        return new SentencePair(i-1);
      } else {
        return null;
      }
    }).filter(o -> o != null);
  }
  
  /**
   * Streaming loader, which does not double peak memory like the loader
   * that creates a suffix array from a parallel corpus.
   * 
   * @param source
   * @param target
   * @param align
   * @throws IOException 
   */
  private void loadCorpus(String source, String target, String align) throws IOException {
    logger.info("Counting the number of corpus positions");
    TimeKeeper timer = TimingUtils.start();
    // Read in the files once to count the sentences and corpus positions
    int numSourcePositions = 0;
    int numTargetPositions = 0;
    numSentences = 0;
    ParallelCorpus corpus = new ParallelCorpus(1);
    try (LineNumberReader fReader = IOTools.getReaderFromFile(source)) {
      LineNumberReader eReader = IOTools.getReaderFromFile(target);
      LineNumberReader aReader = IOTools.getReaderFromFile(align);
      for (String fLine; (fLine = fReader.readLine()) != null; ) {
        String eLine = eReader.readLine();
        String aLine = aReader.readLine();
        AlignedSentence example = corpus.getSentence(fLine, eLine, aLine);
        if (example != null) {
          numSourcePositions += example.sourceLength();
          numTargetPositions += example.targetLength();
          ++numSentences;
        }
      }
    }
    final int initialVocabularySize = corpus.getVocabulary().size();
    timer.mark("Counting corpus positions");
    logger.info("Source positions: {}  Target positions: {}  Sentences: {}", numSourcePositions, 
        numTargetPositions, numSentences);
    
    // Create the arrays
    final int srcLength = numSourcePositions + numSentences;
    if (srcLength < 0) throw new RuntimeException("Maximum source bitext size exceeded");
    srcBitext = new int[srcLength];
    f2e = new int[srcLength];
    final int tgtLength = numTargetPositions + numSentences;
    if (tgtLength < 0) throw new RuntimeException("Maximum target bitext size exceeded");
    tgtBitext = new int[tgtLength];
    e2f = new int[tgtLength];
    
    // Create the arrays and read the files again
    try (LineNumberReader fReader = IOTools.getReaderFromFile(source)) {
      LineNumberReader eReader = IOTools.getReaderFromFile(target);
      LineNumberReader aReader = IOTools.getReaderFromFile(align);
      int srcOffset = 0;
      int tgtOffset = 0;
      for (String fLine; (fLine = fReader.readLine()) != null; ) {
        String eLine = eReader.readLine();
        String aLine = aReader.readLine();
        AlignedSentence sentence = corpus.getSentence(fLine, eLine, aLine);
        if (sentence == null) {
          logger.info("Discarding parallel example {}", fReader.getLineNumber());
        } else {
          System.arraycopy(sentence.source, 0, srcBitext, srcOffset, sentence.sourceLength());
          System.arraycopy(sentence.f2e, 0, f2e, srcOffset, sentence.f2e.length);
          System.arraycopy(sentence.target, 0, tgtBitext, tgtOffset, sentence.targetLength());
          System.arraycopy(sentence.e2f, 0, e2f, tgtOffset, sentence.e2f.length);
          srcOffset += sentence.sourceLength();
          tgtOffset += sentence.targetLength();
          // Source points to target
          srcBitext[srcOffset] = toSentenceOffset(tgtOffset);
          // Target points to source
          tgtBitext[tgtOffset] = toSentenceOffset(srcOffset);
          ++srcOffset;
          ++tgtOffset;
        }        
      }
    }
    this.vocabulary = corpus.getVocabulary();
    assert initialVocabularySize == vocabulary.size();
    timer.mark("Loading corpus");
    logger.info("Done loading corpus: {}", timer);
  }
  
  /**
   * Load the parallel corpus into a contiguous block of memory.
   * Set the corpus reference to null after this call to free memory.
   * 
   * @param corpus
   */
  private void loadCorpus(ParallelCorpus corpus) {
    logger.info("Flattening parallel corpus");
    TimeKeeper timer = TimingUtils.start();
    numSentences = corpus.size();
    int numSourcePositions = corpus.numSourcePositions();
    int numTargetPositions = corpus.numTargetPositions();
    int srcLength = numSourcePositions + numSentences;
    srcBitext = new int[srcLength];
    f2e = new int[srcLength];
    int tgtLength = numTargetPositions + numSentences;
    tgtBitext = new int[tgtLength];
    e2f = new int[tgtLength];
    int srcOffset = 0;
    int tgtOffset = 0;
    for (AlignedSentence sentence : corpus) {
      System.arraycopy(sentence.source, 0, srcBitext, srcOffset, sentence.sourceLength());
      System.arraycopy(sentence.f2e, 0, f2e, srcOffset, sentence.f2e.length);
      System.arraycopy(sentence.target, 0, tgtBitext, tgtOffset, sentence.targetLength());
      System.arraycopy(sentence.e2f, 0, e2f, tgtOffset, sentence.e2f.length);
      srcOffset += sentence.sourceLength();
      tgtOffset += sentence.targetLength();
      // Source points to target
      srcBitext[srcOffset] = toSentenceOffset(tgtOffset);
      // Target points to source
      tgtBitext[tgtOffset] = toSentenceOffset(srcOffset);
      ++srcOffset;
      ++tgtOffset;
    }
    vocabulary = corpus.getVocabulary();
    timer.mark("Corpus loading");
    logger.info("Done loading corpus: {}", timer);
  }

  /**
   * Encoding of bitext pointers.
   * 
   * @param corpusPosition
   * @return
   */
  private static int toSentenceOffset(int corpusPosition) {
    return -1 * (corpusPosition + 1);
  }
  
  /**
   * Decoding of bitext pointers.
   * 
   * @param offset
   * @return
   */
  private static int fromSentenceOffset(int offset) {
    return (-1 * offset) - 1;
  }
  
  /**
   * Create suffix arrays for the parallel corpus.
   */
  public void build() {
    logger.info("Building suffix arrays...");
    TimeKeeper timer = TimingUtils.start();
    int numSourcePositions = srcBitext.length - numSentences;
    srcSuffixArray = build(srcBitext, numSourcePositions);
    if (srcSuffixArray.length != numSourcePositions) throw new RuntimeException();
    timer.mark("Source array");
    int numTargetPositions = tgtBitext.length - numSentences;
    tgtSuffixArray = build(tgtBitext, numTargetPositions);
    if (tgtSuffixArray.length != numTargetPositions) throw new RuntimeException();
    timer.mark("Target array");
    logger.info("Done constructing suffix arrays: {}", timer);
  }
  
  /**
   * Sort the bitext in parallel.
   * 
   * @param bitext
   * @param numPositions
   * @return
   */
  private int[] build(final int[] bitext, int numPositions) {
    return IntStream.range(0, bitext.length).parallel().boxed().sorted((x,y) -> {
      // Compare suffixes
      int xPos = x, yPos = y, xId = bitext[x], yId = bitext[y];
      
      // Check to see if these points are sentence boundaries
      if (xId < 0 && yId < 0) {
        return 0;
      } else if (xId < 0) {
        // Say that sentence boundaries are longer than everything else.
        // They will be pushed to the end of the stream so that limit() can filter them.
        return 1;
      } else if (yId < 0) {
        return -1;
      }
            
      while(xId >= 0 && yId >= 0) {
        if (xId == yId) {
          xId = bitext[++xPos];
          yId = bitext[++yPos];
        } else {
          // Lexicographic sort
          return vocabulary.get(xId).compareTo(vocabulary.get(yId));
        }
      }
      
      // Compare lengths
      int xLength = xPos - x + (xId < 0 ? 0 : 1);
      int yLength = yPos - y + (yId < 0 ? 0 : 1);
      return xLength - yLength;
      
    }).limit(numPositions).mapToInt(i -> i).toArray();
  }

  /**
   * Print the suffix array.
   * 
   * @param isSource
   * @param out
   */
  public void print(boolean isSource, PrintWriter out) {
    int[] sa = isSource ? this.srcSuffixArray : this.tgtSuffixArray;
    int[] bitext = isSource ? this.srcBitext : this.tgtBitext;
    for (int i = 0; i < sa.length; ++i) {
      StringBuilder sb = new StringBuilder();
      sb.append(i).append(": ");
      for (int corpusPos = sa[i]; bitext[corpusPos] >= 0; ++corpusPos) {
        if (corpusPos != sa[i]) sb.append(" ");
        sb.append(vocabulary.get(bitext[corpusPos]));
      }
      out.println(sb.toString());
    }
    out.flush();
  }
  
  /**
   * Find all source spans up to dimension == 3.
   * 
   * TODO(spenceg) Lopez reports finding a few order=5 n-grams of high frequency
   * so maybe generalize this looku
   * 
   * @param sampleSize
   * @param minOccurrences
   */
  public Map<Span,SuffixArraySample> lookupFrequentSourceNgrams(int sampleSize, int minOccurrences) {
    if (sampleSize >= minOccurrences) throw new IllegalArgumentException();
    if (srcSuffixArray.length == 0) return Collections.emptyMap();
    logger.info("Building query cache with threshold {}", minOccurrences);
    Map<Span,SuffixArraySample> queryCache = new HashMap<>(1000);
    int nCnt = 1, nnCnt = 1, nnnCnt = 1;
    int nStart = 0, nnStart = 0, nnnStart = 0;
    Suffix firstSuffix = new Suffix(srcSuffixArray[0], true);
    Span nSpan = new Span(firstSuffix, 1), 
        nnSpan = new Span(firstSuffix, 2), 
        nnnSpan = new Span(firstSuffix, 3);
    for (int i = 1; i < srcSuffixArray.length; ++i) {
      Suffix suffix = new Suffix(srcSuffixArray[i], true);
      Span nSpanThis = new Span(suffix, 1);
      Span nnSpanThis = new Span(suffix, 2);
      Span nnnSpanThis = new Span(suffix, 3);
      nCnt = checkSpan(nSpan, nSpanThis, nStart, i, nCnt, minOccurrences, sampleSize, queryCache);
      if (nCnt == 1) {
        nStart = i;
        nSpan = nSpanThis;
      }
      nnCnt = checkSpan(nnSpan, nnSpanThis, nnStart, i, nnCnt, minOccurrences, sampleSize, queryCache);
      if (nnCnt == 1) {
        nnStart = i;
        nnSpan = nnSpanThis;
      }
      nnnCnt = checkSpan(nnnSpan, nnnSpanThis, nnnStart, i, nnnCnt, minOccurrences, sampleSize, queryCache);
      if (nnnCnt == 1) {
        nnnStart = i;
        nnnSpan = nnnSpanThis;
      }
    };
    logger.info("Query cache size: {}", queryCache.size());
    
    logger.info("Creating target unigram caches for the count() function...");
    this.tgtCountLBCache = new int[vocabulary.size()];
    Arrays.fill(tgtCountLBCache, -1);
    this.tgtCountUBCache = new int[vocabulary.size()];
    Arrays.fill(tgtCountUBCache, -1);
    int lastId = tgtBitext[tgtSuffixArray[0]];
    
    for (int i = 0; i < tgtSuffixArray.length; ++i) {
      int tgtId = tgtBitext[tgtSuffixArray[i]];
      assert tgtId >= 0;
      if (tgtCountLBCache[tgtId] < 0) {
        tgtCountLBCache[tgtId] = i;
      }
      if (lastId != tgtId) {
        tgtCountUBCache[lastId] = i-1;
        assert tgtCountUBCache[lastId] >= tgtCountLBCache[lastId] : String.format("%d %d %d", i, lastId, tgtId);
      }
      lastId = tgtId;
    }
    
    // final update
    tgtCountUBCache[lastId] = tgtSuffixArray.length;
    assert tgtCountUBCache[lastId] >= tgtCountLBCache[lastId] : String.format("%d %d final", tgtSuffixArray.length, lastId);
    
    logger.info("Finished building count() cache.");
    
    return queryCache;
  }
    
  private int checkSpan(Span currentSpan, Span nextSpan, int startSa, int endSa, int cnt, 
      int ruleCacheThreshold, int sampleSize, Map<Span, SuffixArraySample> queryCache) {
    if (currentSpan != null && currentSpan.equals(nextSpan)) {
      return cnt + 1;
      
    } else if (cnt > ruleCacheThreshold) {
      int maxHits = 10*sampleSize;
      int numHits = endSa - startSa;
      int stepSize = (numHits < maxHits) ? 1 : numHits / maxHits;
      assert stepSize > 0;
      List<SentencePair> hits = new ArrayList<>(maxHits);
      for (int i = startSa; i < endSa && hits.size() < maxHits; i += stepSize) {
        int corpusPosition = srcSuffixArray[i];
        assert srcBitext[corpusPosition] >= 0;
        hits.add(new SentencePair(corpusPosition));
      }
      queryCache.put(currentSpan, new SuffixArraySample(hits, startSa, endSa-1));
    }
    return 1;
  }

  /**
   * Identifies a span for caching.
   * 
   * @author Spence Green
   *
   */
  public class Span {
    public final int[] tokens;
    private final int hashCode;
    private Span(Suffix suffix, int order) {
      int[] tokens = new int[order];
      for (int i = 0; i < order; ++i) {
        try {
          tokens[i] = suffix.get(i);
        } catch(Exception e) {
          tokens = new int[0];
        }
      }
      this.tokens = tokens;
      this.hashCode = MurmurHash2.hash32(tokens, tokens.length, 1);
    }
    @Override
    public int hashCode() {
      return hashCode;
    }
    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else if (! (o instanceof Span)) {
        return false;
      } else {
        Span otherSpan = (Span) o;
        if (this.tokens.length == otherSpan.tokens.length) {
          for (int i = 0; i < tokens.length; ++i) {
            if (tokens[i] != otherSpan.tokens[i]) {
              return false;
            }
          }
          return true;
        }
        return false;
      }
    }
    @Override
    public String toString() {
      return Arrays.stream(tokens).mapToObj(tokenId -> vocabulary.get(tokenId))
          .collect(Collectors.joining(" "));
    }
  }

  /**
   * The number of segments in the underlying corpus.
   * 
   * @return
   */
  public int numSentences() { return numSentences; }

  /**
   * Find a lower or upper bound in the suffix array.
   * 
   * @param query
   * @param isSource
   * @param lowerBound
   * @param startFrom
   * @return
   */
  private int findBound(final int[] query, boolean isSource, boolean lowerBound, int startFrom) {
    int[] sa = isSource ? this.srcSuffixArray : this.tgtSuffixArray;
    return findBound(query, isSource, lowerBound, startFrom, sa.length - 1);
  }
  
  private int findBound(final int[] query, boolean isSource, boolean lowerBound, int lo, int hi) {
    int[] sa = isSource ? this.srcSuffixArray : this.tgtSuffixArray;
    int low = lo;
    int high = hi;
    while(low <= high) {
      final int mid = (low + high) >>> 1;
      assert mid < sa.length;
      final int corpusPos = sa[mid];
      assert corpusPos >= 0;
      final Suffix midSuffix = new Suffix(corpusPos, isSource);
      final int cmp = midSuffix.compare(query);

      if (cmp < 0) {
        // Search left
        high = mid - 1;

      } else if (cmp > 0) {
        // Search right
        low = mid + 1;

      } else {
        // Check to see if this is the bound, then search
        if (lowerBound) {
          if (mid == 0) return 0;
          Suffix leftSuffix = new Suffix(sa[mid-1], isSource);
          int cmp2 = leftSuffix.compare(query);
          if (cmp2 > 0) return mid;
          // Search left
          assert cmp2 == 0;
          high = mid - 1;

        } else {
          if (mid == sa.length - 1) return mid;
          Suffix rightSuffix = new Suffix(sa[mid+1], isSource);
          int cmp2 = rightSuffix.compare(query);
          if (cmp2 < 0) return mid;
          // Search right
          assert cmp2 == 0;
          low = mid + 1;
        }
      }
    }
    // Key not found
    return -1;
  }

  /**
   * Wrapper object for suffix queries.
   * 
   * @author Spence Green
   *
   */
  private class Suffix {
    private final int pos;
    private final boolean isSource;
    public Suffix(int corpusPosition, boolean isSource) {
      this.pos = corpusPosition;
      this.isSource = isSource;
    }
    
    public int get(int i) {
      int[] bitext = isSource ? srcBitext : tgtBitext;
      int bitextPos = this.pos + i;
      if (bitextPos < 0 || bitextPos >= bitext.length || bitext[bitextPos] < 0) throw new ArrayIndexOutOfBoundsException();
      return bitext[bitextPos];
    }

    public int compare(int[] query) {
      int[] bitext = isSource ? srcBitext : tgtBitext;
      int j = pos;
      boolean consumedQuery = false;
      for (int i = 0; 
          i < query.length && bitext[j] >= 0; 
          i++, j++) {
        consumedQuery = (i == query.length-1);
        int xId = query[i];
        int yId = bitext[j];
        if (xId != yId) {
          return vocabulary.get(xId).compareTo(vocabulary.get(yId));
        }
      }

      // If query has been consumed, then this query is a prefix of this suffix, and this is a 
      // match. Otherwise, the query is longer than the suffix.
      return consumedQuery ? 0 : 1;
    }
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      boolean seenEnd = false;
      for (int i = 0; ! seenEnd ; ++i) {
        int vocabId = get(i);
        if (vocabId >= 0) {
          if (i > 0) sb.append(" ");
          sb.append(vocabulary.get(vocabId));
        } else {
          seenEnd = true;
        }
      }
      return sb.toString();
    }
  }

  /**
   * Count of this sequence in either the source or target bitext.
   * 
   * @param tokens
   * @param isSource
   * @return
   */
  public int count(final int[] query, boolean isSource) {
    if (query.length == 0) return 0;
    if (!isSource && this.tgtCountLBCache != null && this.tgtCountUBCache != null) {
      // Use caches for fast target lookup
      final int tgtId = query[0];
      final int lo = tgtCountLBCache[tgtId];
      final int hi = tgtCountUBCache[tgtId];
      if (query.length == 1) {
        int count = hi - lo + 1;
        assert count > 0 : String.format("%d %d %d %d", tgtId, count, lo, hi);
        return count;
        
      } else {
        int lb = findBound(query, isSource, true, lo);
        if (lb >= 0) {
          int ub = findBound(query, isSource, false, lb, hi);
          assert ub >= 0 : String.format("%d %d %d %d %d", tgtId, lo, hi, lb, ub);
          return ub - lb + 1;
        }
      }
      
    } else {
      // Standard case
      int lb = findBound(query, isSource, true, 0);
      if (lb >= 0) {
        int ub = findBound(query, isSource, false, lb);
        assert ub >= 0;
        return ub - lb + 1;
      }
    }
    return 0;
  }
  
  /**
   * Return a sample of sentences from this suffix array.
   * 
   * @param sourceQuery
   * @param maxSamples
   * @return
   */
  public SuffixArraySample sample(final int[] sourceQuery, int maxSamples) {
    return sample(sourceQuery, maxSamples, 0, -1);
  }
  
  /**
   * Return a sample of sentences from the suffix array.
   * 
   * @param sourceQuery
   * @param maxSamples
   * @param minBound
   * @param maxBound
   * @return
   */
  public SuffixArraySample sample(final int[] sourceQuery, int maxSamples, int minBound, int maxBound) {
    if (sourceQuery.length == 0) return new SuffixArraySample(Collections.emptyList(), -1, -1);
    int lb = maxBound > minBound ? findBound(sourceQuery, true, true, minBound, maxBound) :
      findBound(sourceQuery, true, true, minBound);
    if (lb < 0) return new SuffixArraySample(Collections.emptyList(), -1, -1);
    int ub = maxBound > lb ? findBound(sourceQuery, true, false, lb, maxBound) :
      findBound(sourceQuery, true, false, lb);
    assert ub >= 0;
    int numHits = ub - lb + 1;
    int stepSize = (numHits < maxSamples) ? 1 : numHits / maxSamples;
    assert stepSize > 0;
    // Stratified sample through the list of positions
    List<SentencePair> samples = new ArrayList<>(maxSamples);
    for (int i = lb; i <= ub && samples.size() < maxSamples; i += stepSize) {
      samples.add(new SentencePair(srcSuffixArray[i]));
    }
    return new SuffixArraySample(samples, lb, ub);
  }
  
  /**
   * Return a sample from the target-side. Optimizations for pre-initializing the search
   * are not supported.
   * 
   * @param targetQuery
   * @param maxSamples
   * @param minBound
   * @param maxBound
   * @return
   */
  public SuffixArraySample sampleTarget(final int[] targetQuery, int maxSamples) {
    if (targetQuery.length == 0) return new SuffixArraySample(Collections.emptyList(), -1, -1);
    int lb = findBound(targetQuery, false, true, 0);
    if (lb < 0) return new SuffixArraySample(Collections.emptyList(), -1, -1);
    int ub = findBound(targetQuery, false, false, lb);
    assert ub >= 0;
    int numHits = ub - lb + 1;
    int stepSize = (numHits < maxSamples) ? 1 : numHits / maxSamples;
    assert stepSize > 0;
    // Stratified sample through the list of positions
    List<SentencePair> samples = new ArrayList<>(maxSamples);
    for (int i = lb; i <= ub && samples.size() < maxSamples; i += stepSize) {
      samples.add(new SentencePair(srcSuffixArray[i]));
    }
    return new SuffixArraySample(samples, lb, ub);
  }

  /**
   * A sampled sentence with an associated pointer to the left edge of
   * the query sequence.
   * 
   * @author Spence Green
   *
   */
  public class SentencePair {
    public final int wordPosition;
    
    // TODO(spenceg) Would need to encode this directly into the bitext as well
    // as a second negative number after the pointer into the other side of the
    // bitext.
//    public final int sentenceId;
    
    public final int srcStartInclusive;
    private final int srcEndExclusive;
    private final int tgtStartInclusive;
    private final int tgtEndExclusive;
    
    private SentencePair(int corpusPosition) {
      // Find source span
      int j = corpusPosition;
      assert srcBitext[j] >= 0;
      // Walk forward
      while (srcBitext[j] >= 0) j++;
      srcEndExclusive = j;
      // Walk backward
      j = corpusPosition - 1;
      while (j >= 0 && srcBitext[j] >= 0) j--;
      srcStartInclusive = j + 1;
      assert corpusPosition >= srcStartInclusive : String.format("%d %d", corpusPosition, srcStartInclusive);
      
      // Find the target span
      tgtStartInclusive = j == -1 ? 0 : fromSentenceOffset(srcBitext[j]) + 1;
      tgtEndExclusive = fromSentenceOffset(srcBitext[srcEndExclusive]);
      assert tgtStartInclusive < tgtEndExclusive : String.format("tgt: %d %d", tgtStartInclusive, 
          tgtEndExclusive);
      assert tgtEndExclusive > 0 : String.valueOf(tgtEndExclusive);
      assert fromSentenceOffset(tgtBitext[tgtEndExclusive]) == srcEndExclusive : String.format("%d %d", 
          fromSentenceOffset(tgtBitext[tgtEndExclusive]), srcEndExclusive);
      
      // Set the start of the query
      wordPosition = corpusPosition - srcStartInclusive;
    }
    
    public int sourceLength() {
      return srcEndExclusive - srcStartInclusive;
    }
    
    public int targetLength() {
      return tgtEndExclusive - tgtStartInclusive;
    }
    
    public int source(int i) {
      int bitextPos = srcStartInclusive + i;
      if (bitextPos < 0 || bitextPos >= srcEndExclusive) throw new ArrayIndexOutOfBoundsException();
      return srcBitext[bitextPos];
    }
    
    public int target(int i) {
      int bitextPos = tgtStartInclusive + i;
      if (bitextPos < tgtStartInclusive || bitextPos >= tgtEndExclusive) throw new ArrayIndexOutOfBoundsException();
      return tgtBitext[bitextPos];
    }
    
    public int[] f2e(int startInclusive, int endExclusive) {
      if (startInclusive >= endExclusive) throw new IllegalArgumentException();
      int bitextStartInclusive = srcStartInclusive + startInclusive;
      int bitextEndExclusive = srcStartInclusive + endExclusive;
      if (bitextStartInclusive < srcStartInclusive || bitextEndExclusive > srcEndExclusive) throw new ArrayIndexOutOfBoundsException();
      return Arrays.copyOfRange(f2e, bitextStartInclusive, bitextEndExclusive);
    }
    
    public int[] f2e(int i) {
      int bitextPos = srcStartInclusive + i;
      if (bitextPos < srcStartInclusive || bitextPos >= srcEndExclusive) throw new ArrayIndexOutOfBoundsException();
      return AlignedSentence.expand(f2e[bitextPos]);
    }
    
    public int[] e2f(int startInclusive, int endExclusive) {
      if (startInclusive >= endExclusive) throw new IllegalArgumentException();
      int bitextStartInclusive = tgtStartInclusive + startInclusive;
      int bitextEndExclusive = tgtStartInclusive + endExclusive;
      if (bitextStartInclusive < tgtStartInclusive || bitextEndExclusive > tgtEndExclusive) throw new ArrayIndexOutOfBoundsException();
      return Arrays.copyOfRange(e2f, bitextStartInclusive, bitextEndExclusive);
    }
    
    public int[] e2f(int i) {
      int bitextPos = tgtStartInclusive + i;
      if (bitextPos < tgtStartInclusive || bitextPos >= tgtEndExclusive) throw new ArrayIndexOutOfBoundsException();
      return AlignedSentence.expand(e2f[bitextPos]);
    }
    
    public boolean isSourceUnaligned(int i) {
      int bitextPos = srcStartInclusive + i;
      if (bitextPos < srcStartInclusive || bitextPos >= srcEndExclusive) throw new ArrayIndexOutOfBoundsException();
      return f2e[bitextPos] == 0;
    }
    
    public boolean isTargetUnaligned(int i) {
      int bitextPos = tgtStartInclusive + i;
      if (bitextPos < tgtStartInclusive || bitextPos >= tgtEndExclusive) throw new ArrayIndexOutOfBoundsException();
      return e2f[bitextPos] == 0;
    }
    
    public ParallelSuffixArrayEntry getParallelEntry() {
      return new ParallelSuffixArrayEntry(this, vocabulary);
    }
    
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      for (int i = 0, sz = sourceLength(); i < sz; ++i) {
        if (i > 0) sb.append(" ");
        sb.append(vocabulary.get(source(i)));
      }
      sb.append(" ||| ");
      for (int i = 0, sz = targetLength(); i < sz; ++i) {
        if (i > 0) sb.append(" ");
        sb.append(vocabulary.get(target(i)));
      }
      return sb.toString();
    }
  }
  
  /**
   * A struct to hold the result of a sample of a suffix array.
   * 
   * @author Spence Green
   *
   */
  public static class SuffixArraySample {
    public final List<SentencePair> samples;
    public final int lb;
    public final int ub;
    public SuffixArraySample(List<SentencePair> q, int lb, int ub) {
      this.samples = q;
      this.lb = lb;
      this.ub = ub;
    }
    public int size() { return samples.size(); }
    @Override
    public String toString() {
      return String.format("bounds: %d/%d size: %d", lb, ub, samples.size());
    }
  }
}
