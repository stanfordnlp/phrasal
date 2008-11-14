package mt.translationtreebank;

import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.ling.*;
import java.io.*;
import java.util.*;

class GaleClassifyingExperiment {
  public static void main(String[] args) throws IOException {
    ChineseTreeReader ctr = new ChineseTreeReader();
    String chNPfile = "C:\\cygwin\\home\\Pichuan Chang\\javanlp\\projects\\mt\\src\\mt\\translationtreebank\\data\\npList.sent.stp.oneline";
    ctr.readMoreTrees(chNPfile);
    for (int i = 0; i < ctr.size(); i++) {
      Tree t = ctr.getTree(i);
      System.out.println(t);
    }
  }
}
