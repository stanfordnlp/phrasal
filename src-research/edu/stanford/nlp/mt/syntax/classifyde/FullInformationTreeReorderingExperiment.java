package edu.stanford.nlp.mt.syntax.classifyde;

import edu.stanford.nlp.mt.train.transtb.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.stats.*;
import java.io.*;
import java.util.*;

class FullInformationTreeReorderingExperiment {
  public static void main(String args[]) throws IOException {
    List<AnnotatedTreePair> atreepairs = ExperimentUtils
        .readAnnotatedTreePairs();

    TwoDimensionalCounter<String, String> cc = new TwoDimensionalCounter<String, String>();
    ClassicCounter<String> deTypeCounter = new ClassicCounter<String>();

    PrintWriter decPW = new PrintWriter(new BufferedWriter(new FileWriter(
        "DEC.txt")));
    PrintWriter degPW = new PrintWriter(new BufferedWriter(new FileWriter(
        "DEG.txt")));

    TregexPattern adjpdeg = TregexPattern.compile("DNP <, ADJP <- DEG");
    TregexPattern qpdeg = TregexPattern.compile("DNP <, QP <- DEG");
    TregexPattern nppndeg = TregexPattern.compile("DNP <, (NP < PN) <- DEG");

    int tpCount = 0;
    for (AnnotatedTreePair validSent : atreepairs) {
      String deType = "";

      tpCount++;
      for (int deIdxInSent : validSent.NPwithDEs_deIdx_set) {
        Pair<Integer, Integer> chNPrange = validSent.NPwithDEs_deIdx
            .get(deIdxInSent);
        String np = validSent.oracleChNPwithDE(deIdxInSent);
        np = np.trim();
        String type = validSent.NPwithDEs_categories.get(deIdxInSent);

        Tree chTree = validSent.chTrees().get(0);
        Tree chNPTree = AlignmentUtils.getTreeWithEdges(chTree,
            chNPrange.first, chNPrange.second + 1);

        int deCount = ExperimentUtils.countDE(chNPTree);

        boolean isDEC = ExperimentUtils.hasDEC(chNPTree, chTree, deIdxInSent);
        boolean isDEG = ExperimentUtils.hasDEG(chNPTree, chTree, deIdxInSent);

        if (deCount > 1) {
          deType = ">1DE";
        } else if (deCount == 1) {
          if (isDEC && !isDEG) {
            // chNPTree.pennPrint(decPW);
            deType = "DEC";
          } else if (isDEG && !isDEC) {
            // chNPTree.pennPrint(degPW);
            deType = "DEG";
          } else {
            chNPTree.pennPrint(System.err);
            throw new RuntimeException("both?");
          }
        } else {
          chNPTree.pennPrint(System.err);
          throw new RuntimeException("");
        }

        if (deType.equals("DEC")) {
          boolean hasVApattern = ExperimentUtils.hasVApattern(chNPTree);
          if (hasVApattern) {
            deType = "DEC-va";
            chNPTree.pennPrint(decPW);
            decPW.println("================================================");
          }
        }

        if (deType.equals("DEG")) {
          TregexMatcher adjpdegM = adjpdeg.matcher(chNPTree);
          TregexMatcher qpdegM = qpdeg.matcher(chNPTree);
          TregexMatcher nppndegM = nppndeg.matcher(chNPTree);
          if (adjpdegM.find() || nppndegM.find()) {
            deType = "DEG-noreorder";
            chNPTree.pennPrint(degPW);
            degPW.println("================================================");
          } else if (qpdegM.find()) {
            deType = "DEG-noreorder-qp";
            chNPTree.pennPrint(degPW);
            degPW.println("================================================");
          }
        }

        deTypeCounter.incrementCount(deType);
        cc.incrementCount(deType, type);
        String chNP = validSent.oracleChNPwithDE(deIdxInSent);
        System.out.printf("%s\t%s\t%s\n", deType, type, chNP);
      }
    }
    decPW.close();
    degPW.close();
    System.err.println(deTypeCounter);
    System.err.println(cc);
  }
}
