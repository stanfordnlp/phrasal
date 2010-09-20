package edu.stanford.nlp.mt.train.transtb;

import edu.stanford.nlp.trees.*;
import java.util.*;

public class TreePair {
  public TranslationAlignment alignment;
  private List<Tree> enTrees;
  private List<Tree> chTrees;
  private List<Tree> chParsedTrees;

  private int fileid; // the original file ID in CTB and E-CTB

  public void setEnTrees(List<Tree> ts) {
    this.enTrees = ts;
  }

  public List<Tree> enTrees() {
    return enTrees;
  }

  public void setChTrees(List<Tree> ts) {
    this.chTrees = ts;
  }

  public List<Tree> chTrees() {
    return chTrees;
  }

  public void setChParsedTrees(List<Tree> ts) {
    this.chParsedTrees = ts;
  }

  public List<Tree> chParsedTrees() {
    return chParsedTrees;
  }

  public void setAlignment(TranslationAlignment a) {
    this.alignment = a;
  }

  public TranslationAlignment alignment() {
    return alignment;
  }

  public void setFileID(int fileid) {
    this.fileid = fileid;
  }

  public int getFileID() {
    return fileid;
  }

  public TreePair(TranslationAlignment alignment, List<Tree> enTrees,
      List<Tree> chTrees) {
    this.alignment = alignment;
    this.enTrees = enTrees;
    this.chTrees = chTrees;
  }

  public TreePair(TranslationAlignment alignment, List<Tree> enTrees,
      List<Tree> chTrees, List<Tree> chPT) {
    this.alignment = alignment;
    this.enTrees = enTrees;
    this.chTrees = chTrees;
    this.chParsedTrees = chPT;
  }

  public static void printTree(Tree t) {
    System.out.println("<pre>");
    t.pennPrint(System.out);
    System.out.println("</pre>");
  }

  public void printTreePair() {
    // (1.1) Chinese Tree
    System.out.println("<h3> Chinese Tree </h3>");
    printTree(chTrees.get(0));

    // (2) English Tree
    System.out.println("<h3> English Tree </h3>");
    for (Tree t : enTrees) {
      printTree(t);
    }

  }

  public void printAlignmentGrid() {
    System.out.println("<h3> Alignment Grid </h3>");
    AlignmentUtils.printAlignmentGrid(alignment);
  }
}
