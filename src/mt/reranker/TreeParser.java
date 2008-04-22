package mt.reranker;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.*;

public class TreeParser implements Function {

  public Object apply (Object o) {
    String s = (String) o;
    Tree t = null;
    try {
      if (s==null) {
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
