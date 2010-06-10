package edu.stanford.nlp.mt.syntax.classifyde;

import edu.stanford.nlp.mt.train.transtb.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.*;
import java.util.*;

class OutputAllNPs {
  public static void main(String args[]) throws Exception {
    Boolean reducedCategory = true;
    Boolean nonOracleTree = true;
    List<AnnotatedTreePair> atreepairs = ExperimentUtils.readAnnotatedTreePairs(reducedCategory, nonOracleTree);
    for(AnnotatedTreePair validSent : atreepairs) {
      for (int deIdxInSent : validSent.NPwithDEs_deIdx_set) {
        Pair<Integer, Integer> chNPrange = validSent.NPwithDEs_deIdx.get(deIdxInSent);
        Tree chTree = validSent.chTrees().get(0);
        Tree chNPTree = AlignmentUtils.getTreeWithEdges(chTree,chNPrange.first, chNPrange.second+1);
        chNPTree.pennPrint(System.out);
        System.out.println();

        Pair<Integer, Integer> parsed_chNPrange = validSent.parsedNPwithDEs_deIdx.get(deIdxInSent);
        Tree parsed_chTree = validSent.chParsedTrees().get(0);
        Tree parsed_chNPTree = AlignmentUtils.getTreeWithEdges(parsed_chTree,parsed_chNPrange.first, parsed_chNPrange.second+1);
        parsed_chNPTree.pennPrint(System.err);
        System.err.println();
      }
    }
  }
}
