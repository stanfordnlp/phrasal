package edu.stanford.nlp.mt.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.stanford.nlp.mt.stats.Sampling;

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
  public ParallelSuffixArray(String sourceFile, String targetFile, String alignmentFile, int expectedSize, 
      boolean isDecoderLocal) throws IOException {
    this(ParallelCorpus.loadCorpusFromFiles(sourceFile, targetFile, alignmentFile, expectedSize, isDecoderLocal));
  }

  /**
   * Get the index associated with this suffix array.
   * 
   * @return
   */
  public TranslationModelIndex getIndex() { return this.corpus.index; }

  /**
   * Create the underlying suffix array from the parallel corpus.
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

    long treeTime = System.nanoTime() - startTime;
    double treeSecs = (double) treeTime / 1e9;
    logger.info("Done enumerating {} corpus: {}s", isSource ? "source" : "target", treeSecs);

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
      int[] intArray = Arrays.copyOfRange(suffix.sequence, suffix.start, suffix.sequence.length);
      StringBuilder sb = new StringBuilder();
      for (int id : intArray) {
        if (sb.length() > 0) sb.append(" ");
        sb.append(corpus.index.get(id));
      }
      out.println(sb.toString());
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
   * @param query
   * @param isSource
   * @param lowerBound
   * @param startFrom
   * @return
   */
  private int findBound(final int[] query, boolean isSource, boolean lowerBound, int startFrom) {
    int[] sa = isSource ? this.srcSuffixArray : this.tgtSuffixArray;
    int low = startFrom;
    int high = sa.length;
    while(low <= high) {
      final int mid = (low + high) >>> 1;
        final int corpusPos = sa[mid];
        final Suffix suffix = getSuffix(corpusPos, isSource);
        final int cmp = startsWith(suffix.sequence, query, suffix.start);

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
            int cmp2 = startsWith(leftSuffix.sequence, query, leftSuffix.start);
            if (cmp2 > 0) return mid;
            // Search left
            assert cmp2 == 0;
            high = mid - 1;

          } else {
            if (mid == sa.length - 1) return mid;
            Suffix rightSuffix = getSuffix(sa[mid+1], isSource);
            int cmp2 = startsWith(rightSuffix.sequence, query, rightSuffix.start);
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
    public final int[] sequence;
    public final int start;
    public Suffix(int[] sequence, int start) {
      this.sequence = sequence;
      this.start = start;
    }
  }

  /**
   * Count of this sequence in the suffix array.
   * 
   * @param sequence
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
   * @param sequence
   * @param isSource
   * @param sampleSize
   * @return
   */
  public SuffixArraySample sample(final int[] query, boolean isSource, int maxHits) {
    int[] sa = isSource ? this.srcSuffixArray : this.tgtSuffixArray;
    int[] posToSentence = isSource ? this.srcPosToSentenceId : this.tgtPosToSentenceId;
    int lb = findBound(query, isSource, true, 0);
    if (lb < 0) return new SuffixArraySample(new ArrayList<>(0), 0.0);
    int ub = findBound(query, isSource, false, lb);
    assert ub > 0;
    int numHits = ub - lb + 1;
    List<QueryResult> hits;
    try (IntStream indices = numHits > maxHits ? Sampling.sampleWithoutReplacement(lb, ub, maxHits) :
      IntStream.rangeClosed(lb, ub)) {
      hits = indices.mapToObj( i -> {
        int corpusPosition = sa[i];
        int sentenceId = positionToSentence(corpusPosition, isSource);
        int offset = sentenceId == 0 ? 0 : posToSentence[sentenceId - 1];
        int start = sentenceId == 0 ? corpusPosition : corpusPosition - offset - 1;
        AlignedSentence sample = this.corpus.get(sentenceId);
        return new QueryResult(sample, start, sentenceId);
      }).collect(Collectors.toList());
    }
    double sampleRate = maxHits / (double) numHits;
    return new SuffixArraySample(hits, sampleRate);
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
    int high = posToSentenceId.length;
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
    public final double sampleRate;
    public SuffixArraySample(List<QueryResult> q, double sampleRate) {
      this.samples = q;
      this.sampleRate = sampleRate;
    }
  }
}
