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

    for (TreePair tp : treepairs) {
      for(Map.Entry<Pair<Integer,Integer>, String> labeledNPs : tp.NPwithDEs_categories.entrySet()) {
        System.out.println(labeledNPs.getKey());
      }
      for(int i : tp.NPwithDEs_deIdx_set) {
        System.err.println(tp.NPwithDEs_deIdx.get(i));
      }
    }
  }
}
