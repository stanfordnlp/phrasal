package mt.discrimreorder;

import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.international.pennchinese.*;
import edu.stanford.nlp.trees.semgraph.*;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams;
import java.util.*;
import java.io.*;
import mt.base.IOTools;

public class TreesToDeps {
  public static void main(String[] args) throws Exception {
    ChineseTreebankParserParams ctpp = new ChineseTreebankParserParams();
    TreeReaderFactory trf = ctpp.treeReaderFactory();
    LineNumberReader pReader = IOTools.getReaderFromFile(args[0]);
    String pLine;
    while((pLine = pReader.readLine()) != null) {
      pLine = ReorderingClassifier.fixChars(pLine);
      Tree t = Tree.valueOf(pLine, trf);
      Filter<String> puncWordFilter = Filters.acceptFilter();
      GrammaticalStructure gs = new ChineseGrammaticalStructure(t, puncWordFilter);
      Collection<TypedDependency> typedDeps = gs.allTypedDependencies();
      System.err.println("TREE="+t);
      for (TypedDependency dep : typedDeps) {
        System.err.println("DEP="+dep);
        System.err.println("DEP="+dep.gov().index()+"/"+dep.dep().index()+"/"+dep.reln().getShortName());
      }
    }
  }
}