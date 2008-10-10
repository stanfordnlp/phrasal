package mt.translationtreebank;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.parser.lexparser.*;
import edu.stanford.nlp.trees.international.pennchinese.*;
import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.*;

class ChineseTreeReader extends AbstractTreeReader {
  private ChineseEscaper ce_;

  public ChineseTreeReader() {
    trees_ = new ArrayList<Tree>();
    tlpp_ = new ChineseTreebankParserParams();
    treeprint_ = new TreePrint("words,penn,typedDependencies", "removeTopBracket,basicDependencies", tlpp_.treebankLanguagePack());
    tt_ = new DummyTreeTransformer();
    ce_ = new ChineseEscaper();
  }

  public ChineseTreeReader(String filename) throws IOException {
    this();
    readMoreTrees(filename);
  }

  public String normalizeSentence(String sent) {
    List<HasWord> words = new ArrayList<HasWord>();
    words.add(new Word(sent));
    words = ce_.apply(words);
    String output = words.get(0).word();
    output = output.replaceAll("―", "—");
    output = output.replaceAll("・", "·");
    return output;
  }

  public List<Tree> getTreesWithWords(String sentStr) {
    String normSent = normalizeSentence(sentStr);
    List<Tree> trees = new ArrayList<Tree>();
    // TODO: can be cached to make it faster
    for(Tree t : trees_) {
      StringBuilder sb = new StringBuilder();
      Sentence<HasWord> hws = getWords(t);
      //for (HasWord hw : hws) {
      for(int i = 0; i < hws.size(); i++) {
        HasWord hw = hws.get(i);
        sb.append(hw.word());
      }
      String normTreeStr = normalizeSentence(sb.toString());
      if (normSent.equals(normTreeStr)) {
        trees.add(t);
      }
    }
    return trees;
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
