package mt.translationtreebank;

import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.ling.*;
import java.io.*;
import java.util.*;

class ParsedClassifyingExperiment {
  public static void main(String[] args) throws IOException {
    Boolean reducedCategory = true;
    List<TreePair> treepairs = ExperimentUtils.readAnnotatedTreePairs(reducedCategory);
    List<Map<Pair<Integer,Integer>, Integer>> maps = new ArrayList<Map<Pair<Integer,Integer>, Integer>>();


    for (TreePair validSent : treepairs) {
      for(int deIdxInSent : validSent.NPwithDEs_deIdx_set) {
        Pair<Integer, Integer> chNPrange = validSent.NPwithDEs_deIdx.get(deIdxInSent);
        String np = validSent.oracleChNPwithDE(deIdxInSent);
        np = np.trim();
        String label = validSent.NPwithDEs_categories.get(deIdxInSent);
        String predictedType = "";
      }
    }
  }
}
