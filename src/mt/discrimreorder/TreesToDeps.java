package mt.discrimreorder;

import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.international.pennchinese.*;
import edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams;
import java.util.*;
import java.io.*;

public class TreesToDeps {
  public static void main(String[] args) throws Exception {
    ChineseTreebankParserParams ctpp = new ChineseTreebankParserParams();
    TreeReaderFactory trf = ctpp.treeReaderFactory();
    TreeReader pReader = trf.newTreeReader(new InputStreamReader(new FileInputStream(args[0]), "UTF-8"));
    Tree t = null;
    while((t=pReader.readTree())!=null) {
      GrammaticalStructure gs = new ChineseGrammaticalStructure(t);
      Collection<TypedDependency> typedDeps = gs.allTypedDependencies();
      System.err.println("TREE="+t);
      for (TypedDependency dep : typedDeps) {
        System.err.println("DEP="+dep);
        System.err.println("DEP="+dep.gov().index()+"/"+dep.dep().index()+"/"+dep.reln().getShortName());
      }
    }
  }
}