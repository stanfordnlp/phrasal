package mt.translationtreebank;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.parser.lexparser.*;
import edu.stanford.nlp.trees.international.pennchinese.*;
import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.*;

class ChineseTreeReader extends AbstractTreeReader {
  public ChineseTreeReader() {
    trees_ = new ArrayList<Tree>();
    tlpp_ = new ChineseTreebankParserParams();
    treeprint_ = new TreePrint("words,penn,typedDependencies", "removeTopBracket,basicDependencies", tlpp_.treebankLanguagePack());
    tt_ = new DummyTreeTransformer();
  }

  public ChineseTreeReader(String filename) throws IOException {
    this();
    readMoreTrees(filename);
  }
  
  public static void main(String args[]) throws IOException {
    
    ChineseTreeReader ctr = new ChineseTreeReader();
    for(int i = 1; i <= 325; i++) {
      String name = 
        String.format("/afs/ir/data/linguistic-data/Chinese-Treebank/6/data/utf8/bracketed/chtb_%04d.fid", i);
      System.err.println(name);
      ctr.readMoreTrees(name);
      System.err.println("number of trees="+ctr.size());
    }

    ctr.printAllTrees();
  }
}
