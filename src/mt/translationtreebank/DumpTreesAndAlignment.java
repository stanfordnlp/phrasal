package mt.translationtreebank;

import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.ling.*;
import java.util.*;
import java.io.*;

class DumpTreesAndAlignment {
  static void printTreesAndAlignment(TreePair treepair, 
                                     TranslationAlignment alignment) {
    treepair.printTreePair();
    TranslationAlignment.printAlignmentGrid(alignment);
  }

  public static void main(String args[]) throws Exception {
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
    treepairs = ExperimentUtils.readAnnotatedTreePairs(reducedCategory, 
                                                       nonOracleTree);

    int count = 0;

    TranslationAlignment.printAlignmentGridHeader();

    for(TreePair validSent : treepairs) {
      // In our dataset, every TreePair actually only just have one 
      // Chinese sentence(tree). There were no cases when there are 
      // multiple Chinese trees aligned to English trees
      Tree chTree = validSent.chTrees.get(0);
      // English trees should be one or more
      List<Tree> enTrees = validSent.enTrees;
      // This is the alignment
      TranslationAlignment alignment = validSent.alignment;
      printTreesAndAlignment(validSent, alignment);
    }

    TranslationAlignment.printAlignmentGridBottom();
  }
}
