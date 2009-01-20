package mt.classifyde;

import mt.train.transtb.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.stats.*;
import java.io.*;
import java.util.*;

class FullInformationWangExperiment {
  public static void main(String args[]) throws IOException {
    Properties props = StringUtils.argsToProperties(args);
    String nonOracleTreeStr= props.getProperty("nonOracleTree", "false");
    String sixclassStr= props.getProperty("6class", "false");
    Boolean nonOracleTree = Boolean.parseBoolean(nonOracleTreeStr);
    Boolean sixclass = Boolean.parseBoolean(sixclassStr);

    List<TreePair> treepairs = TransTBUtils.readAnnotatedTreePairs(true, nonOracleTree);


    TwoDimensionalCounter<String,String> cc = new TwoDimensionalCounter<String,String>();
    ClassicCounter<String> deTypeCounter = new ClassicCounter<String>();

    TwoDimensionalCounter<String, String> confusionMatrix = new TwoDimensionalCounter<String,String>();

    for(TreePair validSent : treepairs) {
      for(int deIdxInSent : validSent.NPwithDEs_deIdx_set) {
        Pair<Integer, Integer> chNPrange = validSent.parsedNPwithDEs_deIdx.get(deIdxInSent);
        String np = validSent.oracleChNPwithDE(deIdxInSent);
        np = np.trim();
        String label = validSent.NPwithDEs_categories.get(deIdxInSent);
        String predictedType = "";

        Tree chTree = validSent.chParsedTrees.get(0);
        Tree chNPTree = TranslationAlignment.getTreeWithEdges(chTree,chNPrange.first, chNPrange.second+1);

        //if (label.equals("no B") || label.equals("other") || label.equals("multi-DEs")) {
        if ((sixclass && !ExperimentUtils.is6class(label)) ||
            (!sixclass && !ExperimentUtils.is5class(label))) {
          continue;
        }

        boolean hasDEC = ExperimentUtils.hasDEC(chNPTree, chTree, deIdxInSent);
        boolean hasDEG = ExperimentUtils.hasDEG(chNPTree, chTree, deIdxInSent);
        if (hasDEC && hasDEG) {
          throw new RuntimeException("error: both");
        } else if (hasDEC && !hasDEG) {
          predictedType = "swapped";
        } else if (hasDEG && !hasDEC) {
          if (ExperimentUtils.hasADJPpattern(chNPTree) || 
              ExperimentUtils.hasNPPNpattern(chNPTree) || 
              ExperimentUtils.hasQPpattern(chNPTree))
            predictedType = "ordered";
          else 
            predictedType = "swapped";
        } else {
          System.err.println("no DEC or DEG");
          chNPTree.pennPrint(System.err);
          predictedType = "swapped";
        }
      
        label = ExperimentUtils.coarseCategory(label);
        /*
        if (label.startsWith("B") || label.equals("relative clause")) {
          label = "swapped";
        } else if (label.startsWith("A")) {
          label = "ordered";
        } else {
        }
        */
        confusionMatrix.incrementCount(label, predictedType);
      }
    }
    System.out.println(confusionMatrix);

    ExperimentUtils.resultSummary(confusionMatrix);
  }
}
