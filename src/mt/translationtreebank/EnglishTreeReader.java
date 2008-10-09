package mt.translationtreebank;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.parser.lexparser.*;
import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.*;

class EnglishTreeReader extends AbstractTreeReader {
  public EnglishTreeReader() {
    trees_ = new ArrayList<Tree>();
    tlpp_ = new EnglishTreebankParserParams();
    treeprint_ = new TreePrint("words,penn,typedDependencies", "removeTopBracket", tlpp_.treebankLanguagePack());
    tt_ = new NMLTreeTransformer();
  }

  public EnglishTreeReader(String filename) throws IOException {
    this();
    readMoreTrees(filename);
  }

  public static void main(String args[]) throws IOException {
    EnglishTreeReader etr = new EnglishTreeReader();
    for(int i = 1; i <= 325; i++) {
      String name =
        String.format("/u/nlp/scr/data/ldc/LDC2007T02-EnglishChineseTranslationTreebankV1.0/data/pennTB-style-trees/chtb_%03d.mrg.gz", i);
      System.err.println(name);
      etr.readMoreTrees(name);
      System.err.println("number of trees="+etr.size());
    }

    etr.printAllTrees();
  }
}

class NMLTreeTransformer implements TreeTransformer {
  public NMLTreeTransformer() {
  }

  public Tree transformTree(Tree tree) {
    tree = tree.deepCopy();
    for (Iterator<Tree> it = tree.iterator(); it.hasNext();) {
      Tree subtree = it.next();
      if (subtree.isPhrasal() && subtree.value().equals("NML")) {
        subtree.setValue("NX");
      }
    }
    return tree;
  }
}
