package mt.translationtreebank;

import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.stats.*;
import java.io.*;
import java.util.*;

class FullInformationTreeReorderingExperiment {
  public static void main(String args[]) throws IOException {
    List<TreePair> treepairs = ExperimentUtils.readAnnotatedTreePairs();

    TwoDimensionalCounter<String,String> cc = new TwoDimensionalCounter<String,String>();
    ClassicCounter<String> deTypeCounter = new ClassicCounter<String>();

    PrintWriter decPW  = new PrintWriter(new BufferedWriter(new FileWriter("DEC.txt")));
    PrintWriter degPW  = new PrintWriter(new BufferedWriter(new FileWriter("DEG.txt")));

    TreePattern va1 = TreePattern.compile("CP <, (IP <- (VP <: VA)) <- DEC");
    TreePattern va2 = TreePattern.compile("CP <, (IP <- (VP <, (ADVP $+ (VP <: VA)))) <- DEC");
    TreePattern adjpdeg = TreePattern.compile("DNP <, ADJP <- DEG");
    TreePattern qpdeg = TreePattern.compile("DNP <, QP <- DEG");
    TreePattern nppndeg = TreePattern.compile("DNP <, (NP < PN) <- DEG");

    int tpCount = 0;
    for(TreePair validSent : treepairs) {
      String deType = "";

      tpCount++;
      for(Map.Entry<Pair<Integer,Integer>, String> labeledNPs : validSent.NPwithDEs_categories.entrySet()) {
        String np = validSent.chNPwithDE(labeledNPs.getKey());
        np = np.trim();
        String type = labeledNPs.getValue();

        Tree chTree = validSent.chTrees.get(0);
        Pair<Integer,Integer> chNPrange = labeledNPs.getKey();
        Tree chNPTree = TranslationAlignment.getTreeWithEdges(chTree,chNPrange.first, chNPrange.second+1);

        int deCount = ExperimentUtils.countDE(chNPTree);
        
        boolean isDEC = ExperimentUtils.hasDEC(chNPTree);
        boolean isDEG = ExperimentUtils.hasDEG(chNPTree);

        if (deCount > 1) {
          deType = ">1DE";
        } else if (deCount == 1) {
          if (isDEC && !isDEG) {
            //chNPTree.pennPrint(decPW);
            deType = "DEC";
          } else if (isDEG && !isDEC) {
            //chNPTree.pennPrint(degPW);
            deType = "DEG";
          } else {
            chNPTree.pennPrint(System.err);
            throw new RuntimeException("both?");
          }
        }
        else {
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
          TreeMatcher adjpdegM = adjpdeg.matcher(chNPTree);
          TreeMatcher qpdegM = qpdeg.matcher(chNPTree);
          TreeMatcher nppndegM = nppndeg.matcher(chNPTree);
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
        String chNP = validSent.chNPwithDE(labeledNPs.getKey());
        System.out.printf("%s\t%s\t%s\n", deType, type, chNP);
      }
    }
    decPW.close();
    degPW.close();
    System.err.println(deTypeCounter);
    System.err.println(cc);
  }
}
