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
    List<TreePair> treepairs = ExperimentUtils.readAnnotatedTreePairs(reducedCategory, "projects/mt/src/mt/translationtreebank/data/ctb_parsed/bracketed/");
    List<Map<Pair<Integer,Integer>, Integer>> maps = new ArrayList<Map<Pair<Integer,Integer>, Integer>>();


    for (TreePair validSent : treepairs) {
      for(int deIdxInSent : validSent.NPwithDEs_deIdx_set) {
        Pair<Integer, Integer> chNPrange = validSent.NPwithDEs_deIdx.get(deIdxInSent);
        Pair<Integer, Integer> parsedChNPrange = validSent.parsedNPwithDEs_deIdx.get(deIdxInSent);

        Tree npTree = TranslationAlignment.getTreeWithEdges(validSent.chTrees.get(0), chNPrange.first, chNPrange.second+1);
        if (validSent.chParsedTrees.get(0) == null) {
          throw new RuntimeException("here");
        }
        if ( parsedChNPrange==null) {
          throw new RuntimeException("here");
        }
        Tree t = validSent.chParsedTrees.get(0);
        System.err.println(parsedChNPrange);
        int f = parsedChNPrange.first;
        int s = parsedChNPrange.second+1;
        Tree parsedNpTree = 
          TranslationAlignment.getTreeWithEdges(t,f,s);

        /*
        npTree.pennPrint(System.out);
        System.out.println();
        parsedNpTree.pennPrint(System.err);
        System.err.println();
        */
      }
    }
  }
}
