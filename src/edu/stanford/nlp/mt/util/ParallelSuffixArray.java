package edu.stanford.nlp.mt.util;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.stanford.nlp.mt.util.TimingUtils.TimeKeeper;

/**
 * An implementation of a parallel suffix array.
 * 
 * NOTE: The fields are protected, non-final for fast serialization/deserialization.
 * 
 * @author Spence Green
 *
 */
public class ParallelSuffixArray implements Serializable {

  private static final long serialVersionUID = -5403502473957235135L;

  private static transient final Logger logger = LogManager.getLogger(ParallelSuffixArray.class);
  
  protected Vocabulary vocabulary;
  protected int[] srcBitext;
  protected int numSourcePositions;
  protected int[] f2e;
  protected int[] tgtBitext;
  protected int numTargetPositions;
  protected int[] e2f;
  protected int numSentences;

  protected int[] srcSuffixArray; 
  protected int[] tgtSuffixArray;
  
  // Sources of slowdown
  // No initialization of upper and lower bounds (with unigram positions)
  
  // Ideas
  // Left and right source LCP arrays (He et al. 2013 p.326) using the algorithm of Kasai (2001)
  // Left and right target LCP arrays
  
  /**
   * No-arg constructor for deserialization.
   */
  public ParallelSuffixArray() {}
  

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
   * Load the parallel corpus into a contiguous block of memory.
   * Set the corpus reference to null after this call to free memory.
   * 
   * @param corpus
   */
  public void loadCorpus(ParallelCorpus corpus) {
    logger.info("Flattening parallel corpus");
    TimeKeeper timer = TimingUtils.start();
    numSentences = corpus.size();
    numSourcePositions = corpus.numSourcePositions();
    numTargetPositions = corpus.numTargetPositions();
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
    vocabulary = corpus.index;
    timer.mark("Corpus loading");
    logger.info("Done loading corpus: {}", timer);
  }

  private static int toSentenceOffset(int corpusPosition) {
    return -1 * (corpusPosition + 1);
  }
  
  private static int fromSentenceOffset(int offset) {
    return (-1 * offset) - 1;
  }
  
  /**
   * Create suffix arrays for the parallel corpus.
   */
  public void build() {
    logger.info("Building suffix arrays...");
    TimeKeeper timer = TimingUtils.start();
    srcSuffixArray = build(srcBitext, numSourcePositions);
    if (srcSuffixArray.length != numSourcePositions) throw new RuntimeException();
    timer.mark("Source array");
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
      } else if (xId < 0 ) {
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
      int xLength = xPos - x + (xId >= 0 ? 1 : 0);
      int yLength = yPos - y + (yId >= 0 ? 1 : 0);
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
      this.hashCode = MurmurHash.hash32(tokens, tokens.length, 1);
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
      StringBuilder sb = new StringBuilder();
      for (int tokenId : tokens) {
        if (sb.length() > 0) sb.append(" ");
        sb.append(vocabulary.get(tokenId));
      }
      return sb.toString();
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

      // Check the lengths
      return consumedQuery ? 0 : 1;
    }
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      int i = 0;
      try {
        while(true) {
          if (i > 0) sb.append(" ");
          sb.append(vocabulary.get(get(i)));
          ++i;
        }
      } catch(Exception e) {}
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
    int lb = findBound(query, isSource, true, 0);
    if (lb >= 0) {
      int ub = findBound(query, isSource, false, lb);
      assert ub > 0;
      return ub - lb + 1;
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
    int lb = maxBound > minBound ? findBound(sourceQuery, true, true, minBound, maxBound) :
      findBound(sourceQuery, true, true, minBound);
    if (lb < 0) return new SuffixArraySample(new ArrayList<>(0), -1, -1);
    int ub = maxBound > lb ? findBound(sourceQuery, true, false, lb, maxBound) :
      findBound(sourceQuery, true, false, lb);
    assert ub > 0;
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
      if (bitextPos < 0 || bitextPos >= tgtEndExclusive) throw new ArrayIndexOutOfBoundsException();
      return tgtBitext[bitextPos];
    }
    
    public int[] f2e(int i) {
      int bitextPos = srcStartInclusive + i;
      if (bitextPos < 0 || bitextPos >= srcEndExclusive) throw new ArrayIndexOutOfBoundsException();
      return AlignedSentence.expand(f2e[bitextPos]);
    }
    
    public int[] e2f(int i) {
      int bitextPos = tgtStartInclusive + i;
      if (bitextPos < 0 || bitextPos >= tgtEndExclusive) throw new ArrayIndexOutOfBoundsException();
      return AlignedSentence.expand(e2f[bitextPos]);
    }
    
    public boolean isSourceUnaligned(int i) {
      int bitextPos = srcStartInclusive + i;
      if (bitextPos < 0 || bitextPos >= srcEndExclusive) throw new ArrayIndexOutOfBoundsException();
      return f2e[bitextPos] == 0;
    }
    
    public boolean isTargetUnaligned(int i) {
      int bitextPos = tgtStartInclusive + i;
      if (bitextPos < 0 || bitextPos >= tgtEndExclusive) throw new ArrayIndexOutOfBoundsException();
      return e2f[bitextPos] == 0;
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
  }
}
