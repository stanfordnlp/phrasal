package edu.stanford.nlp.mt.util;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
   */
  public ParallelSuffixArray(String sourceFile, String targetFile, String alignmentFile, int expectedSize, 
      boolean isDecoderLocal) {
    try {
      this.corpus = ParallelCorpus.loadCorpusFromFiles(sourceFile, targetFile, alignmentFile, expectedSize, isDecoderLocal);
    } catch (IOException e) {
      logger.error("Unable to load from corpus.", e);
      throw new RuntimeException(e);
    }
    
    // Build source prefix array
    boolean isSource = true;
    createPrefixTree(isSource, corpus);
    
    // Build target suffix array
    isSource = false;
    createPrefixTree(isSource, corpus);
  }

  private void createPrefixTree(boolean isSource, ParallelCorpus corpus) {
    PrefixTreeNode root = new PrefixTreeNode(null);
    int[] posToSentenceId = new int[corpus.size()];

    // Iterate over suffixes to build the tree
    int corpusPosition = 0;
    int i = 0;
    for (AlignedSentence example : corpus) {
      Sequence<IString> source = example.getSource(corpus.index);
      for (int j = 0, sz = source.size(); j < sz; ++j) {
        Sequence<IString> suffix = source.subsequence(j, sz);
        add(root, suffix, corpusPosition++);
      }
      posToSentenceId[i++] = corpusPosition - 1;
    }
    
    if (isSource) {
      srcPosToSentenceId = posToSentenceId;
    } else {
      tgtPosToSentenceId = posToSentenceId;
    }
    
    // Create the suffix array
    createSuffixArray(root, isSource, corpusPosition);
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
  
  private void add(PrefixTreeNode root, Sequence<IString> suffix, int corpusPosition) {
    IString leftEdge = suffix.get(0);
    PrefixTreeNode child = root.children.getOrDefault(leftEdge, null);
    if (child == null) {
      child = new PrefixTreeNode(leftEdge);
      root.children.put(leftEdge, child);
    }
    addHelper(child, suffix, 1, corpusPosition);
  }

//  private void visitChildren(PrefixTreeNode node, )
  
  private void createSuffixArray(PrefixTreeNode root, boolean isSource, int numPositions) {
    int[] sa = new int[numPositions];
    root.children.keySet().stream().sorted().forEach(x -> 
    {
      
    });
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
