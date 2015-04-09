package edu.stanford.nlp.mt.util;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

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
  
  /**
   * TODO(spenceg) Replace with kryo
   */
  private static final long serialVersionUID = -5403502473957235135L;

  private static transient final Logger logger = LogManager.getLogger(ParallelSuffixArray.class);
  
  public ParallelCorpus corpus;
  public int[] srcSuffixArray; 
  public int[] tgtSuffixArray;
  public int[] srcPosToSentenceId;
  public int[] tgtPosToSentenceId;

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
        int cmp = corpus.index.get(xId).compareTo(corpus.index.get(yId));
        if (cmp != 0) return cmp;
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
  
  public int numSentences() {
    return corpus.size();
  }

  private static int lowerBound(int[] query, int[] suffixArray) {
    return 0;
  }
  
  private static int upperBound(int[] query, int startPos, int[] suffixArray) {
    
    return 0;
  }
  
  /**
   * Count of this sequence in the suffix array.
   * 
   * @param sequence
   * @param isSource
   * @return
   */
  public int count(int[] query, boolean isSource) {
    int lb = lowerBound(query, isSource ? this.srcSuffixArray : this.tgtSuffixArray);
    int ub = upperBound(query, lb, isSource ? this.srcSuffixArray : this.tgtSuffixArray);
    return ub - lb + 1;
  }
  
  /**
   * Return a sample of sentences from this suffix array.
   * 
   * @param sequence
   * @param isSource
   * @param sampleSize
   * @return
   */
  public List<SentenceSample> sample(int[] query, boolean isSource, int sampleSize) {
    int[] sa = isSource ? this.srcSuffixArray : this.tgtSuffixArray;
    int[] posToSentence = isSource ? this.srcPosToSentenceId : this.tgtPosToSentenceId;
    int lb = lowerBound(query, sa);
    int ub = upperBound(query, lb, sa);
    List<SentenceSample> hits = new ArrayList<>(sampleSize);
    for (int i = lb; i <= ub && hits.size() <= sampleSize; ++i) {
      // TODO(spenceg) Sample with replacement if the number of hits exceeds
      // the sample size.
      // ThreadLocalRandom.current().nextInt(lb, ub + 1);
      int corpusPosition = sa[i];
      int sentenceId = positionToSentence(corpusPosition, posToSentence);
      int offset = sentenceId == 0 ? 0 : posToSentence[sentenceId - 1];
      int wordPosition = corpusPosition - offset + 1;
      hits.add(new SentenceSample(this.corpus.get(sentenceId), wordPosition));
    }
    return hits;
  }
  
  private int positionToSentence(int corpusPosition, int[] posToSentence) {
    // TODO Auto-generated method stub
    return 0;
  }

  /**
   * A sampled sentence with an associated pointer to the left edge of
   * the query sequence.
   * 
   * @author Spence Green
   *
   */
  public static class SentenceSample {
    public final AlignedSentence sentence;
    public final int wordPosition;
    public SentenceSample(AlignedSentence sentence, int wordPosition) {
      this.sentence = sentence;
      this.wordPosition = wordPosition;
    }
  }
}
