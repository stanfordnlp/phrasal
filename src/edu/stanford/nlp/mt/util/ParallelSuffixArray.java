package edu.stanford.nlp.mt.util;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    // Build source suffix array
    boolean isSource = true;
    PrefixTreeNode prefixTree = createPrefixTree(isSource, corpus);
    createSuffixArray(prefixTree, isSource);
    
    // Build target suffix array
    isSource = false;
    prefixTree = createPrefixTree(isSource, corpus);
    createSuffixArray(prefixTree, isSource);
  }

  /**
   * Constructor.
   * 
   * @param sourceFile
   * @param targetFile
   * @param alignmentFile
   * @param expectedSize
   */
  public ParallelSuffixArray(String sourceFile, String targetFile, String alignmentFile, int expectedSize) {
    try {
      this.corpus = ParallelCorpus.loadCorpusFromFiles(sourceFile, targetFile, alignmentFile, expectedSize);
    } catch (IOException e) {
      logger.error("Unable to load from corpus.", e);
      throw new RuntimeException(e);
    }
    
    // Build source prefix array
    boolean isSource = true;
    PrefixTreeNode prefixTree = createPrefixTree(isSource, corpus);
    createSuffixArray(prefixTree, isSource);
    
    // Build target suffix array
    isSource = false;
    prefixTree = createPrefixTree(isSource, corpus);
    createSuffixArray(prefixTree, isSource);
  }

  private static PrefixTreeNode createPrefixTree(boolean isSource, ParallelCorpus corpus) {
    // TODO Auto-generated method stub
    return null;
  }

  private void createSuffixArray(PrefixTreeNode root, boolean isSource) {
    // TODO Auto-generated method stub
    
  }
  
  public int numSentences() {
    return corpus.size();
  }
  
  // TODO(spenceg) Query functions: counts for words and n-grams
  // TODO(spenceg) 

  /**
   * For creating a prefix-tree for linear-time construction.
   * @author rayder441
   *
   */
  private static class PrefixTreeNode implements Comparable<PrefixTreeNode> { 
    public IString token;
    Set<PrefixTreeNode> children;
    public List<Integer> sentenceIds;
    public List<Integer> wordPositions;
    
    /**
     * Constructor.
     * 
     * @param token
     */
    public PrefixTreeNode(IString token) {
      this.token = token;
      children = new HashSet<>();
      sentenceIds = new ArrayList<>();
      wordPositions = new ArrayList<>();
    }

    @Override
    public String toString() { 
      return String.format("[%s #children: %d #positions: %d]", token.toString(), 
          children.size(), wordPositions.size()); 
    }
    
    @Override
    public int compareTo(PrefixTreeNode o) {
      // TODO(spenceg) WARNING This may not be the correct underlying vocabulary.
      return token.toString().compareTo(o.token.toString());
    }
  }
}
