package edu.stanford.nlp.mt.reranker;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.*;

public class TreeParser implements Function<String, Tree> {

  public Tree apply(String o) {
    String s = o;
    Tree t = null;
    try {
      if (s == null) {
        t = null;
      } else {
        t = Tree.valueOf(s);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return t;
  }

}
