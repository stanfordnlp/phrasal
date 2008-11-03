package mt.translationtreebank;

import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.stats.*;
import java.io.*;
import java.util.*;

class FullInformationWangRevisedExperiment {
  public static void main(String args[]) throws IOException {
    List<TreePair> treepairs = ExperimentUtils.readAnnotatedTreePairs();

    TwoDimensionalCounter<String,String> cc = new TwoDimensionalCounter<String,String>();
    ClassicCounter<String> deTypeCounter = new ClassicCounter<String>();

    TwoDimensionalCounter<String, String> confusionMatrix = new TwoDimensionalCounter<String,String>();

    for(TreePair validSent : treepairs) {
      for(Map.Entry<Pair<Integer,Integer>, String> labeledNPs : validSent.NPwithDEs_categories.entrySet()) {
        String np = validSent.chNPwithDE(labeledNPs.getKey());
        np = np.trim();
        String label = labeledNPs.getValue();
        String predictedType = "";

        Tree chTree = validSent.chTrees.get(0);
        Pair<Integer,Integer> chNPrange = labeledNPs.getKey();
        Tree chNPTree = TranslationAlignment.getTreeWithEdges(chTree,chNPrange.first, chNPrange.second+1);

        if (label.equals("no B") || label.equals("other") || label.equals("multi-DEs")) {
          continue;
        }

        boolean hasDEC = ExperimentUtils.hasDEC(chNPTree);
        boolean hasDEG = ExperimentUtils.hasDEG(chNPTree);
        if (hasDEC && hasDEG) {
          throw new RuntimeException("error: both");
        } else if (hasDEC && !hasDEG) {
          if (ExperimentUtils.hasVApattern(chNPTree)) {
            predictedType = "ordered";
          } else {
            predictedType = "swapped";
          }
        } else if (hasDEG && !hasDEC) {
          if (ExperimentUtils.hasADJPpattern(chNPTree) ||
              ExperimentUtils.hasNPPNpattern(chNPTree)) {
            predictedType = "ordered";
          } else if (ExperimentUtils.hasQPpattern(chNPTree)) {
            predictedType = "swapped";
          } else
            predictedType = "swapped";
        } else {
          throw new RuntimeException("error: none");
        }
      
        if (label.startsWith("B") || label.equals("relative clause")) {
          label = "swapped";
        } else if (label.startsWith("A")) {
          label = "ordered";
        } else {
        }
        confusionMatrix.incrementCount(label, predictedType);
      }
    }
    System.out.println(confusionMatrix);

    ExperimentUtils.resultSummary(confusionMatrix);
  }
}
