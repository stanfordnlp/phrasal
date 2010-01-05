package edu.stanford.nlp.mt.classifyde;

import edu.stanford.nlp.util.*;
import java.util.*;


class DisplayTreePairs {
  public static void main(String args[]) throws Exception {
    boolean reducedCategory = true;
    boolean nonOracleTree = true;
    List<AnnotatedTreePair> atreepairs = ExperimentUtils.readAnnotatedTreePairs(reducedCategory, nonOracleTree);

    for(AnnotatedTreePair validSent : atreepairs) {
      for (int deIdxInSent : validSent.NPwithDEs_deIdx_set) {
        Pair<Integer,Integer> npRange = validSent.NPwithDEs_deIdx.get(deIdxInSent);
        String np = validSent.oracleChNPwithDE(deIdxInSent);
        np = np.trim();
        String cat = validSent.NPwithDEs_categories.get(deIdxInSent);
        int deIdx = deIdxInSent - npRange.first;
        System.out.println(np+"\t"+deIdx+"\t"+cat);
      }
    }
  }
}
