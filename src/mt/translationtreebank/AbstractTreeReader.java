package mt.translationtreebank;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.parser.lexparser.*;
import edu.stanford.nlp.trees.international.pennchinese.*;
import edu.stanford.nlp.ling.*;
import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.*;

abstract class AbstractTreeReader {
  List<Tree> trees_;
  TreebankLangParserParams tlpp_;
  TreePrint treeprint_;
  TreeTransformer tt_;

  private PrintWriter pw = new PrintWriter(System.out, true);

  public int readMoreTrees(String filename) throws IOException {
    Reader reader;
    if (filename.endsWith(".gz")) {
      reader = new InputStreamReader(new GZIPInputStream(new FileInputStream(filename)));
    } else {
      reader = new FileReader(filename);
    }
    Iterator<Tree> i = tlpp_.treeTokenizerFactory().getTokenizer(new BufferedReader(reader));

    while(i.hasNext()) {
      Tree t = i.next();
      trees_.add(tt_.transformTree(t));
    }

    return trees_.size();
  }

  public static Sentence<HasWord> getWords(Tree t) {
    return t.yield();
  }

  abstract String normalizeSentence(String s);
  
  public void printAllTrees() {
    for(Tree t : trees_) {
      treeprint_.printTree(t);
    }
  }

  public Tree getTree(int index) {
    return trees_.get(index);
  }

  public int size() {
    return trees_.size();
  }
}
