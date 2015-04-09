package edu.stanford.nlp.mt.util;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    
    // Build source suffix array
    boolean isSource = true;
    createPrefixTree(isSource, corpus);
    
    // Build target suffix array
    isSource = false;
    createPrefixTree(isSource, corpus);
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

  private void createPrefixTree(boolean isSource, ParallelCorpus corpus) {
    logger.info("Creating {} prefix tree", isSource ? "source" : "target");
    PrefixTreeNode root = new PrefixTreeNode(null);
    int[] posToSentenceId = new int[corpus.size()];

    // Iterate over suffixes to build the tree
    long startTime = System.nanoTime();
    int corpusPosition = 0;
    int i = 0;
    for (AlignedSentence example : corpus) {
      Sequence<IString> sequence = isSource ? example.getSource(corpus.index) : example.getTarget(corpus.index);
      for (int j = 0, sz = sequence.size(); j < sz; ++j) {
        Sequence<IString> suffix = sequence.subsequence(j, sz);
        addSuffix(root, suffix, corpusPosition++);
      }
      posToSentenceId[i++] = corpusPosition - 1;
      if ( (i+1) % 1000 == 0) System.err.printf("%d,", i);
    }
    
    long treeTime = System.nanoTime() - startTime;
    double treeSecs = (double) treeTime / 1e9;
    logger.info("Done creating {} prefix tree: {}s", isSource ? "source" : "target", treeSecs);

    // Create the sentence indices
    if (isSource) {
      srcPosToSentenceId = posToSentenceId;
    } else {
      tgtPosToSentenceId = posToSentenceId;
    }
    
    // Create the suffix array
    startTime = System.nanoTime();
    logger.info("Creating {} suffix array", isSource ? "source" : "target");
    createSuffixArray(root, isSource, corpusPosition);
    
    long saTime = System.nanoTime() - startTime;
    double saSecs = (double) saTime / 1e9;
    logger.info("Done creating {} suffix array: {}s", isSource ? "source" : "target", saSecs);
  }

  private void addHelper(PrefixTreeNode root, Sequence<IString> suffix, int index, int corpusPosition) {
    if (index == suffix.size()) {
      root.corpusPositions.add(corpusPosition);
      
    } else {
      IString focus = suffix.get(index);
      PrefixTreeNode child = root.children.getOrDefault(focus, null);
      if (child == null) {
        child = new PrefixTreeNode(focus);
        root.children.put(focus, child);
      }
      addHelper(child, suffix, ++index, corpusPosition);
    }
  }
  
  private void addSuffix(PrefixTreeNode root, Sequence<IString> suffix, int corpusPosition) {
    IString leftEdge = suffix.get(0);
    PrefixTreeNode child = root.children.getOrDefault(leftEdge, null);
    if (child == null) {
      child = new PrefixTreeNode(leftEdge);
      root.children.put(leftEdge, child);
    }
    addHelper(child, suffix, 1, corpusPosition);
  }

  private int visitChild(PrefixTreeNode node, int[] sa, int saIndex) {
    if (node.corpusPositions.size() > 0) {
      int[] nodePositions = node.corpusPositions.stream().mapToInt(i -> (int) i).toArray();
      System.arraycopy(nodePositions, 0, sa, saIndex, nodePositions.length);
      saIndex += nodePositions.length;
    }
    if (node.children.size() > 0) {
      List<IString> children = node.children.keySet().parallelStream().sorted().collect(Collectors.toList());
      for (IString childKey : children) {
        PrefixTreeNode child = node.children.get(childKey);
        saIndex = visitChild(child, sa, saIndex);
      }
    }
    System.err.println(saIndex);
    return saIndex;
  }
  
  private void createSuffixArray(PrefixTreeNode root, boolean isSource, int numPositions) {
    int[] sa = new int[numPositions];
    int saIndex = 0;
    System.err.println(root.children.size());
    List<IString> children = root.children.keySet().parallelStream().sorted().collect(Collectors.toList());
    for (IString childKey : children) {
      PrefixTreeNode child = root.children.get(childKey);
      saIndex = visitChild(child, sa, saIndex);
    }
    if (isSource) {
      this.srcSuffixArray = sa;
    } else {
      this.tgtSuffixArray = sa;
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
  
  // TODO(spenceg) Query functions: counts for words and n-grams
  // TODO(spenceg) 

  /**
   * For creating a prefix-tree for linear-time construction.
   * 
   * @author Spence Green
   *
   */
  private static class PrefixTreeNode implements Comparable<PrefixTreeNode> { 
    
    public IString token;
    public List<Integer> corpusPositions;
    public Map<IString,PrefixTreeNode> children;
    
    /**
     * Constructor.
     * 
     * @param token
     */
    public PrefixTreeNode(IString token) {
      this.token = token;
      children = new HashMap<>();
      corpusPositions = new ArrayList<>();
    }

    @Override
    public String toString() { 
      return String.format("[%s #children: %d #positions: %d]", token.toString(), 
          children.size(), corpusPositions.size()); 
    }
    
    @Override
    public int compareTo(PrefixTreeNode o) {
      return token.toString().compareTo(o.token.toString());
    }
  }
}
