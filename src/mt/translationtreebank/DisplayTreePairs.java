package mt.translationtreebank;

import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.ling.*;
import java.io.*;
import java.util.*;


class DisplayTreePairs {
  public static void main(String args[]) throws Exception {
    boolean reducedCategory = true;
    boolean nonOracleTree = true;
    List<TreePair> treepairs = ExperimentUtils.readAnnotatedTreePairs(reducedCategory, nonOracleTree);

    for(TreePair validSent : treepairs) {
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
