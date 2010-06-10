package edu.stanford.nlp.mt.syntax.discrimreorder;

import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.international.pennchinese.*;
import edu.stanford.nlp.trees.semgraph.*;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import edu.stanford.nlp.mt.base.IOTools;

public class TreesToPaths {
  public static void main(String[] args) throws Exception {
    Properties prop = StringUtils.argsToProperties(args);
    boolean useSameDirPath  = Boolean.parseBoolean(prop.getProperty("useSameDirPath", "false"));

    System.err.println("useSameDirPath = " + useSameDirPath);

    ChineseTreebankParserParams ctpp = new ChineseTreebankParserParams();
    TreeReaderFactory trf = ctpp.treeReaderFactory();
    LineNumberReader pReader = IOTools.getReaderFromFile(args[0]);
    String pLine;
    String outFilename = args[1];
    PrintWriter pw = new PrintWriter(new GZIPOutputStream(new FileOutputStream(outFilename)));
    int lineno = 1;
    while((pLine = pReader.readLine()) != null) {
      if (lineno % 100 == 0)
        System.err.println("l="+lineno);
      lineno++;
      pLine = ReorderingClassifier.fixCharsInParse(pLine);
      Tree t = Tree.valueOf("("+pLine+")", trf);
      Filter<String> puncWordFilter = Filters.acceptFilter();
      GrammaticalStructure gs = new ChineseGrammaticalStructure(t, puncWordFilter);
      SemanticGraph chGraph = SemanticGraphFactory.makeFromTree(gs, "doc1", 0);
      List<IndexedWord> list = chGraph.vertexList();
      for (int i = 0; i < list.size(); i++) {
        for (int j = 0; j < list.size(); j++) {
          if (i!=j) {
            String path = DepUtils.getPathName(i, j, list, chGraph, useSameDirPath);
            if (path.length() > 0)
              pw.printf("%d:%d:%s ", i, j, path);
          }
        }
      }
      pw.println();
    }
    pw.close();
  }
}
