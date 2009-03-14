package mt.discrimreorder;

import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.international.pennchinese.*;
import edu.stanford.nlp.trees.semgraph.*;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import mt.base.IOTools;

public class TreesToDepsDisplay {
  public static void main(String[] args) throws Exception {
    ChineseTreebankParserParams ctpp = new ChineseTreebankParserParams();
    TreeReaderFactory trf = ctpp.treeReaderFactory();
    LineNumberReader pReader = IOTools.getReaderFromFile(args[0]);
    String pLine;
    int lineno = 1;
    Counter<String> relns = new ClassicCounter<String>();
    while((pLine = pReader.readLine()) != null) {
      if (lineno % 100 == 0)
        System.err.println("l="+lineno);
      lineno++;
      pLine = ReorderingClassifier.fixCharsInParse(pLine);
      Tree t = Tree.valueOf("("+pLine+")", trf);
      Filter<String> puncWordFilter = Filters.acceptFilter();
      GrammaticalStructure gs = new ChineseGrammaticalStructure(t, puncWordFilter);
      Collection<TypedDependency> deps = gs.allTypedDependencies();
      System.out.println("Sent: "+StringUtils.join(t.yield(), " "));
      System.out.println("Tree: ");
      t.pennPrint(System.out);
      System.out.println("Deps: ");
      for(TypedDependency dep : deps) {
        System.out.println(dep);
        String name = dep.reln().getShortName();
        relns.incrementCount(name);
      }
      System.out.println("-------------------------------------------");
    }

    System.out.println("============ Stats of all deps ============");
    for(String k : relns.keySet()) {
      System.out.printf("%s\t%f\n", k, relns.getCount(k));
    }
  }
}