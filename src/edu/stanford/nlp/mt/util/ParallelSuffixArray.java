package edu.stanford.nlp.mt.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An implementation of a parallel suffix array.
 * 
 * NOTE: The fields are public, non-final for fast serialization/deserialization.
 * 
 * TODO(spenceg) Implement IStrings methods for processing IStrings with
 * a different underlying vocabulary.
 * 
 * @author Spence Green
 *
 */
public class ParallelSuffixArray implements Serializable {

  private static final long serialVersionUID = -5403502473957235135L;

  private static transient final Logger logger = LogManager.getLogger(ParallelSuffixArray.class);
  
  protected ParallelCorpus corpus;
  protected int[] srcSuffixArray; 
  protected int[] tgtSuffixArray;
  protected int[] srcPosToSentenceId;
  protected int[] tgtPosToSentenceId;
  
  // Sources of slowdown
  // String comparisons in startsWith (use LCP arrays)
  // No initialization of upper and lower bounds
  // The unnecessary sort in the sample procedure. Stratify instead.
  
  // TODO(caches)
  // Left and right source LCP arrays (He et al. 2013 p.326) using the algorithm of Kasai (2001)
  // Left and right target LCP arrays
  // Unigram caches -- source and target with associated functions
  // Source postings lists (target postings lists don't seem to help)
  
  // TODO(spenceg) Add option to pre-compute and serialize these caches (for the foreground arrays)
  // Otherwise compute them dynamically
  
  /**
   * No-arg constructor for deserialization.
   */
  public ParallelSuffixArray() {}
  
  /**
   * Constructor.
   * 
   * @param corpus
   */
  public ParallelSuffixArray(ParallelCorpus corpus) {
    this.corpus = corpus;

    if (corpus.numSourcePositions() > Integer.MAX_VALUE - 5 ||
        corpus.numTargetPositions() > Integer.MAX_VALUE - 5) {
      throw new RuntimeException("Number of corpus positions exceeds maximum suffix array size.");
    }
    logger.info("Corpus size: {}", corpus.size());
    logger.info("# corpus positions src: {} tgt: {}", corpus.numSourcePositions(), corpus.numTargetPositions());

    createArray(true);
    createArray(false);
  }

  /**
   * Constructor.
   * 
   * @param sourceFile
   * @param targetFile
   * @param alignmentFile
   * @param expectedSize
   * @param isDecoderLocal 
   * @throws IOException 
   */
  public ParallelSuffixArray(String sourceFile, String targetFile, String alignmentFile, int expectedSize) throws IOException {
    this(ParallelCorpus.loadCorpusFromFiles(sourceFile, targetFile, alignmentFile, expectedSize));
  }

  /**
   * Get the index associated with this suffix array.
   * 
   * @return
   */
  public Vocabulary getVocabulary() { return corpus.index; }

  /**
   * Get the underlying corpus.
   * 
   * @return
   */
  public ParallelCorpus getCorpus() { return corpus; }
  
  /**
   * Create the underlying suffix array from the parallel corpus in O(nlogn) time (naive algorithm...
   * but easy to parallelize).
   * 
   * @param isSource
   */
  private void createArray(boolean isSource) {
    int[] posToSentenceId = new int[corpus.size()];

    // Iterate over suffixes to build the tree
    logger.info("Enumerating {} corpus...", isSource ? "source" : "target");
    long startTime = System.nanoTime();
    int corpusPosition = 0;
    int i = 0;
    List<CorpusPosition> positions = new ArrayList<>(isSource ? corpus.numSourcePositions() : corpus.numTargetPositions());
    for (AlignedSentence example : corpus) {
      int len = isSource ? example.sourceLength() : example.targetLength();
      for (int j = 0; j < len; ++j) {
        positions.add(new CorpusPosition(corpusPosition++, i, j));
      }
      posToSentenceId[i++] = corpusPosition - 1;
    }

    long enumTime = System.nanoTime() - startTime;
    double enumSecs = (double) enumTime / 1e9;
    logger.info("Done enumerating {} corpus: {}s", isSource ? "source" : "target", enumSecs);

    // Create the suffix array (in parallel)
    startTime = System.nanoTime();
    logger.info("Creating {} suffix array...", isSource ? "source" : "target");
    final int[] sa = positions.parallelStream().sorted((x,y) ->
    {
      AlignedSentence exX = corpus.get(x.sentenceId);
      int[] xSeq = isSource ? exX.source : exX.target;
      AlignedSentence exY = corpus.get(y.sentenceId);
      int[] ySeq = isSource ? exY.source : exY.target;

      for (int yPos = y.sentPos, xPos = x.sentPos; 
          xPos < xSeq.length && yPos < ySeq.length; 
          xPos++, yPos++) {
        int xId = xSeq[xPos];
        int yId = ySeq[yPos];
        if (xId == yId) {
          continue;
        } else {
          // Lexicographic sort
          int cmp = corpus.index.get(xId).compareTo(corpus.index.get(yId));
          if (cmp != 0) return cmp;
        }
      }
      // Check the lengths
      int xLength = xSeq.length - x.sentPos;
      int yLength = ySeq.length - y.sentPos;
      return xLength - yLength;
    }).mapToInt(a -> a.corpusPos).toArray();

    long saTime = System.nanoTime() - startTime;
    double saSecs = (double) saTime / 1e9;
    logger.info("Done creating {} suffix array: {}s", isSource ? "source" : "target", saSecs);

    // Setup the arrays
    if (isSource) {
      srcPosToSentenceId = posToSentenceId;
      this.srcSuffixArray = sa;

    } else {
      tgtPosToSentenceId = posToSentenceId;
      this.tgtSuffixArray = sa;
    }
  }

  /**
   * Print the suffix array.
   * 
   * @param isSource
   * @param out
   */
  public void print(boolean isSource, PrintWriter out) {
    int[] sa = isSource ? this.srcSuffixArray : this.tgtSuffixArray;
    for (int i = 0; i < sa.length; ++i) {
      int corpusPos = sa[i];
      if (corpusPos == 322) {
        System.err.println();
      }
      Suffix suffix = getSuffix(corpusPos, isSource);
      int[] intArray = Arrays.copyOfRange(suffix.tokens, suffix.start, suffix.tokens.length);
      StringBuilder sb = new StringBuilder();
      for (int id : intArray) {
        if (sb.length() > 0) sb.append(" ");
        sb.append(corpus.index.get(id));
      }
      out.println(sb.toString());
    }
  }
  
  /**
   * Find all source spans up to dimension == 3.
   * 
   * @param sampleSize
   * @param minOccurrences
   */
  public Map<Span,SuffixArraySample> lookupFrequentSourceNgrams(int sampleSize, int minOccurrences) {
    if (sampleSize >= minOccurrences) throw new IllegalArgumentException();
    logger.info("Building query cache with threshold {}", minOccurrences);
    Map<Span,SuffixArraySample> queryCache = new HashMap<>(1000);
    int nCnt = 1, nnCnt = 1, nnnCnt = 1;
    Span nSpan = null, nnSpan = null, nnnSpan = null;
    for (int i = 0; i < srcSuffixArray.length; ++i) {
      Suffix suffix = this.getSuffix(srcSuffixArray[i], true);
      Span nSpanThis = Span.getSpan(suffix.tokens, suffix.start, 1, i);
      Span nnSpanThis = Span.getSpan(suffix.tokens, suffix.start, 2, i);
      Span nnnSpanThis = Span.getSpan(suffix.tokens, suffix.start, 3, i);
      nCnt = checkSpan(nSpan, nSpanThis, nCnt, minOccurrences, sampleSize, queryCache);
      if (nCnt == 1) {
        nSpan = nSpanThis;
      }
      nnCnt = checkSpan(nnSpan, nnSpanThis, nnCnt, minOccurrences, sampleSize, queryCache);
      if (nnCnt == 1) {
        nnSpan = nnSpanThis;
      }
      nnnCnt = checkSpan(nnnSpan, nnnSpanThis, nnnCnt, minOccurrences, sampleSize, queryCache);
      if (nnnCnt == 1) {
        nnnSpan = nnnSpanThis;
      }
    };
    logger.info("Query cache size: {}", queryCache.size());
    return queryCache;
  }
    
  private int checkSpan(Span currentSpan, Span nextSpan, int cnt, 
      int ruleCacheThreshold, int sampleSize, Map<Span, SuffixArraySample> queryCache) {
    if (currentSpan != null && currentSpan.equals(nextSpan)) {
      return cnt + 1;
      
    } else {
      if (cnt > ruleCacheThreshold) {
        int start = currentSpan.saIndex;
        int end = nextSpan.saIndex;
        int[] positions = Arrays.copyOfRange(srcSuffixArray, start, end);
        assert positions.length >= ruleCacheThreshold;
        List<QueryResult> hits = IntStream.of(positions).sorted().limit(sampleSize)
            .mapToObj(corpusPosition -> {
          int sentenceId = positionToSentence(corpusPosition, true);
          int offset = sentenceId == 0 ? 0 : srcPosToSentenceId[sentenceId - 1];
          int startIndex = sentenceId == 0 ? corpusPosition : corpusPosition - offset - 1;
          AlignedSentence sample = this.corpus.get(sentenceId);
          return new QueryResult(sample, startIndex, sentenceId);
        }).collect(Collectors.toList());
        queryCache.put(currentSpan, new SuffixArraySample(hits, positions.length, start, end-1));
      }
      return 1;
    }
  }

  /**
   * Identifies a span for caching.
   * 
   * @author Spence Green
   *
   */
  public static class Span {
    public int[] tokens;
    public int saIndex;
    public int hashCode;
    private Span(int[] suffix, int start, int end, int saIndex) {
      this.saIndex = saIndex;
      this.tokens = Arrays.copyOfRange(suffix, start, end);
      this.hashCode = MurmurHash.hash32(tokens, tokens.length, 1);
    }
    public static Span getSpan(int[] suffix, int start, int order, int saIndex) {
      int end = start+order;
      if (end > suffix.length) {
        return null;
      } else {
        return new Span(suffix, start, end, saIndex);
      }
    }
    @Override
    public String toString() { return Arrays.toString(tokens); }
    @Override
    public int hashCode() {
      return hashCode;
    }
    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else if (o == null || ! (o instanceof Span)) {
        return false;
      } else {
        Span otherSpan = (Span) o;
        if (tokens.length == otherSpan.tokens.length) {
          return Arrays.equals(tokens, otherSpan.tokens);
        }
        return false;
      }
    }
  }
  
  /**
   * Marks a corpus position for fast sorting.
   * 
   * @author Spence Green
   *
   */
  private static class CorpusPosition {
    public final int corpusPos;
    public final int sentenceId;
    public final int sentPos;
    public CorpusPosition(int c, int s, int sentPos) {
      this.corpusPos = c;
      this.sentenceId = s;
      this.sentPos = sentPos;
    }
  }

  /**
   * The number of segments in the underlying corpus.
   * 
   * @return
   */
  public int numSentences() {
    return corpus.size();
  }

  /**
   * Determine if a suffix starts with the query sequence.
   * 
   * TODO(spenceg) This should be an LCP comparison?
   * 
   * @param suffix
   * @param query
   * @param suffixStart
   * @return
   */
  private int startsWith(final int[] suffix, final int[] query, int suffixStart) {
    boolean consumedQuery = false;
    for (int i = 0, j = suffixStart; 
        i < query.length && j < suffix.length; 
        i++, j++) {
      consumedQuery = (i == query.length-1);
      int xId = query[i];
      int yId = suffix[j];
      if (xId == yId) {
        continue;
      } else {
        int cmp = corpus.index.get(xId).compareTo(corpus.index.get(yId));
        if (cmp != 0) return cmp;
      }
    }
    // Check the lengths
    int yLength = suffix.length - suffixStart;
    return consumedQuery ? 0 : query.length - yLength;
  }

  /**
   * Find a lower or upper bound in the suffix array.
   * 
   * TODO(spenceg) Runtime is dominated by the calls to getSuffix(), which in turn
   * call positionToSentence.
   * 
   * @param query
   * @param isSource
   * @param lowerBound
   * @param startFrom
   * @return
   */
  private int findBound(final int[] query, boolean isSource, boolean lowerBound, int startFrom) {
    int[] sa = isSource ? this.srcSuffixArray : this.tgtSuffixArray;
    int low = startFrom;
    int high = sa.length - 1;
    while(low <= high) {
      final int mid = (low + high) >>> 1;
      assert mid < sa.length;
      final int corpusPos = sa[mid];
      final Suffix suffix = getSuffix(corpusPos, isSource);
      final int cmp = startsWith(suffix.tokens, query, suffix.start);

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
          Suffix leftSuffix = getSuffix(sa[mid-1], isSource);
          int cmp2 = startsWith(leftSuffix.tokens, query, leftSuffix.start);
          if (cmp2 > 0) return mid;
          // Search left
          assert cmp2 == 0;
          high = mid - 1;

        } else {
          if (mid == sa.length - 1) return mid;
          Suffix rightSuffix = getSuffix(sa[mid+1], isSource);
          int cmp2 = startsWith(rightSuffix.tokens, query, rightSuffix.start);
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
   * Get a suffix from the suffix array.
   * 
   * @param corpusPos
   * @param isSource
   * @return
   */
  private Suffix getSuffix(final int corpusPos, boolean isSource) {
    int sentenceId = positionToSentence(corpusPos, isSource);
    AlignedSentence example = this.corpus.get(sentenceId);
    int[] suffix = isSource ? example.source : example.target;
    int offset;
    if (isSource) {
      offset = sentenceId == 0 ? 0 : this.srcPosToSentenceId[sentenceId-1];
    } else {
      offset = sentenceId == 0 ? 0 : this.tgtPosToSentenceId[sentenceId-1];
    }
    int start = sentenceId == 0 ? corpusPos : corpusPos - offset - 1;
    assert start >= 0;
    return new Suffix(suffix, start);
  }

  /**
   * Wrapper object for suffix queries.
   * 
   * @author Spence Green
   *
   */
  private static class Suffix {
    public final int[] tokens;
    public final int start;
    public Suffix(int[] tokens, int start) {
      this.tokens = tokens;
      this.start = start;
    }
  }

  /**
   * Count of this sequence in the suffix array.
   * 
   * @param tokens
   * @param isSource
   * @return
   */
  public int count(final int[] query, boolean isSource) {
    int lb = findBound(query, isSource, true, 0);
    if (lb >= 0) {
      int ub = findBound(query, isSource, false, lb);
      return ub - lb + 1;
    }
    return 0;
  }

  /**
   * Get all results for this query.
   * 
   * @param query
   * @param isSource
   * @return
   */
  public List<QueryResult> query(final int[] query, boolean isSource) {
    int[] sa = isSource ? this.srcSuffixArray : this.tgtSuffixArray;
    int[] posToSentence = isSource ? this.srcPosToSentenceId : this.tgtPosToSentenceId;
    int lb = findBound(query, isSource, true, 0);
    if (lb < 0) return new ArrayList<>(0);
    List<QueryResult> hits = new ArrayList<>();
    for (int i = lb, limit = sa.length; i < limit; ++i) {
      int corpusPosition = sa[i];
      int sentenceId = positionToSentence(corpusPosition, isSource);
      int offset = sentenceId == 0 ? 0 : posToSentence[sentenceId - 1];
      int start = sentenceId == 0 ? corpusPosition : corpusPosition - offset - 1;
      AlignedSentence sample = this.corpus.get(sentenceId);
      int[] suffix = isSource ? sample.source : sample.target;
      if (this.startsWith(suffix, query, start) == 0) {
        hits.add(new QueryResult(sample, start, sentenceId));
      } else {
        break;
      }
    }
    return hits;
  }
  
  /**
   * Return a sample of sentences from this suffix array.
   * 
   * @param tokens
   * @param isSource
   * @param sampleSize
   * @return
   */
  public SuffixArraySample sample(final int[] query, boolean isSource, int maxHits) {
    int[] sa = isSource ? this.srcSuffixArray : this.tgtSuffixArray;
    int[] posToSentence = isSource ? this.srcPosToSentenceId : this.tgtPosToSentenceId;
    int lb = findBound(query, isSource, true, 0);
    if (lb < 0) return new SuffixArraySample(new ArrayList<>(0), 0, -1, -1);
    int ub = findBound(query, isSource, false, lb);
    assert ub > 0;
    int numHits = ub - lb + 1;
    if(numHits < maxHits) {
      List<QueryResult> samples = new ArrayList<>(numHits);
      for (int i = lb; i <= ub; ++i) {
        int corpusPosition = sa[i];
        int sentenceId = positionToSentence(corpusPosition, isSource);
        int offset = sentenceId == 0 ? 0 : posToSentence[sentenceId - 1];
        int start = sentenceId == 0 ? corpusPosition : corpusPosition - offset - 1;
        AlignedSentence sample = this.corpus.get(sentenceId);
        samples.add(new QueryResult(sample, start, sentenceId));
      }
      return new SuffixArraySample(samples, numHits, lb, ub);

    } else {
      // Stratified sample through the list of positions
      int stepSize = numHits / maxHits;
      List<QueryResult> samples = new ArrayList<>(maxHits);
      for (int i = 0; i < maxHits; ++i) {
        int saIdx = lb + (i*stepSize);
        int corpusPosition = sa[saIdx];
        int sentenceId = positionToSentence(corpusPosition, isSource);
        int offset = sentenceId == 0 ? 0 : posToSentence[sentenceId - 1];
        int start = sentenceId == 0 ? corpusPosition : corpusPosition - offset - 1;
        AlignedSentence sample = this.corpus.get(sentenceId);
        assert query[0] == sample.source[start];
        assert start + query.length <= sample.sourceLength();
        samples.add(new QueryResult(sample, start, sentenceId));
      }
      return new SuffixArraySample(samples, numHits, lb, ub);
    }
  }

  /**
   * Return the sentence index for a given corpus position.
   * 
   * @param corpusPosition
   * @param isSource
   * @return
   */
  private int positionToSentence(final int corpusPosition, boolean isSource) {
    if (corpusPosition < 0) throw new IllegalArgumentException();
    int[] posToSentenceId = isSource ? this.srcPosToSentenceId : this.tgtPosToSentenceId;
    int low = 0;
    int high = posToSentenceId.length - 1;
    while(low <= high) {
      final int mid = (low + high) >>> 1;
      final int lastPosition = posToSentenceId[mid];
      final int cmp = corpusPosition - lastPosition;
      if (cmp < 0) {
        if (mid == 0) return mid;
        int left = posToSentenceId[mid-1];
        int cmp2 = corpusPosition - left;
        if (cmp2 > 0) return mid;
        high = mid - 1;

      } else if (cmp > 0) {
        if (mid == posToSentenceId.length - 1) return mid;
        int right = posToSentenceId[mid+1];
        int cmp2 = corpusPosition - right;
        if (cmp2 < 0) return mid + 1;
        low = mid + 1;

      } else {
        return mid;
      }
    }
    // Corpus position not found? 
    throw new IllegalArgumentException();
  }

  /**
   * A sampled sentence with an associated pointer to the left edge of
   * the query sequence.
   * 
   * @author Spence Green
   *
   */
  public static class QueryResult {
    public final AlignedSentence sentence;
    public final int wordPosition;
    public final int sentenceId;
    public QueryResult(AlignedSentence sentence, int wordPosition, int sentenceId) {
      this.sentence = sentence;
      this.wordPosition = wordPosition;
      this.sentenceId = sentenceId;
    }
  }
  
  /**
   * A struct to hold the result of a sample of a suffix array.
   * 
   * @author Spence Green
   *
   */
  public static class SuffixArraySample {
    public final List<QueryResult> samples;
    public final int numHits;
    public final int lb;
    public final int ub;
    public SuffixArraySample(List<QueryResult> q, int numHits, int lb, int ub) {
      this.samples = q;
      this.numHits = numHits;
      this.lb = lb;
      this.ub = ub;
    }
  }
}
