package edu.stanford.nlp.mt.train.transtb;

import edu.stanford.nlp.trees.*;
import java.util.*;
import java.io.*;

import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.mt.train.AbstractWordAlignment;

class DumpTreesAndAlignment {
  static void printTreesAndAlignment(TreePair treepair, 
                                     TranslationAlignment alignment) {
    treepair.printTreePair();
    AlignmentUtils.printAlignmentGrid(alignment);
  }

  /**
   * Dumps aligned trees. If no argument is provided, dump to an HTML page.
   * If file base name is provided, dump source-language treebank to base_name.f,
   * target-language treebank to base_name.e, and alignment to base_name.a.
   * @param args Optional argument
   * @throws Exception ??
   */
  public static void main(String args[]) throws Exception {

    boolean genHTML = true;
    PrintStream fWriter=null, eWriter=null, aWriter=null;
    
    if(args.length == 1) {
      genHTML = false;
      String out = args[0];
      fWriter = IOTools.getWriterFromFile(out+".zh.trees");
      eWriter = IOTools.getWriterFromFile(out+".en.trees");
      aWriter = IOTools.getWriterFromFile(out+".align");
    }

    List<TreePair> treepairs;
    Boolean reducedCategory = true;
    Boolean nonOracleTree = false;

    // This call reads in the data, including : 
    // CTB, E-C translation treebank, word alignment,
    // and also the "category" of the DEs under NPs.
    // Each TreePair is kinda like a sentence pair in the parallel text.
    // In the data you read in, every treepair must have one Chinese tree,
    // but could have more than one English trees.
    // Other than trees, the "alignment" data member is also useful 
    // for general purpose.
    treepairs = TransTBUtils.readAnnotatedTreePairs
         (nonOracleTree);

    if(genHTML)
      AlignmentUtils.printAlignmentGridHeader();

    for(TreePair validSent : treepairs) {
      // In our dataset, every TreePair actually only just have one 
      // Chinese sentence(tree). There were no cases when there are 
      // multiple Chinese trees aligned to English trees
      Tree chTree = validSent.chTrees().get(0);
      // English trees should be one or more
      List<Tree> enTrees = validSent.enTrees();
      // This is the alignment
      TranslationAlignment alignment = validSent.alignment();
      if(genHTML)
        printTreesAndAlignment(validSent, alignment);
      else {
        fWriter.println("( "+chTree.getChild(0).toString()+" )");
        StringBuffer buf = new StringBuffer();
        if(enTrees.size() > 1)
          buf.append("( ");
        for(int i=0; i<enTrees.size(); ++i) {
          if(i>0)
            buf.append(" ");
          buf.append(enTrees.get(i).toString());
        }
        if(enTrees.size() > 1)
          buf.append(" )");
        eWriter.println(buf.toString());
        AbstractWordAlignment al = new SymmetricalWordAlignment();
        al.init(alignment.matrix_);
        aWriter.println(al.toString());
      }
    }

    if(genHTML)
      AlignmentUtils.printAlignmentGridBottom();
    else {
      fWriter.close();
      eWriter.close();
      aWriter.close();
    }
  }
}
