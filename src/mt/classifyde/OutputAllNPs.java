package mt.translationtreebank;

import mt.train.transtb.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.ling.*;
import java.util.*;
import java.io.*;

class OutputAllTrees {
  public static void main(String args[]) throws Exception {
    List<TreePair> treepairs;
    Boolean reducedCategory = true;
    Boolean nonOracleTree = true;
    treepairs = TransTBUtils.readAnnotatedTreePairs(reducedCategory, nonOracleTree);
    for(TreePair validSent : treepairs) {
      for (int deIdxInSent : validSent.NPwithDEs_deIdx_set) {
        Pair<Integer, Integer> chNPrange = validSent.NPwithDEs_deIdx.get(deIdxInSent);
        Tree chTree = validSent.chTrees.get(0);
        Tree chNPTree = TranslationAlignment.getTreeWithEdges(chTree,chNPrange.first, chNPrange.second+1);
        chNPTree.pennPrint(System.out);
        System.out.println();

        Pair<Integer, Integer> parsed_chNPrange = validSent.parsedNPwithDEs_deIdx.get(deIdxInSent);
        Tree parsed_chTree = validSent.chParsedTrees.get(0);
        Tree parsed_chNPTree = TranslationAlignment.getTreeWithEdges(parsed_chTree,parsed_chNPrange.first, parsed_chNPrange.second+1);
        parsed_chNPTree.pennPrint(System.err);
        System.err.println();
      }
    }
  }
}
