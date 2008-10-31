package mt.translationtreebank;

import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.ling.*;
import java.io.*;
import java.util.*;

class FullInformationClassifyingExperiment {
  static double min = .1;
  static double max = 10.0;
  final static boolean accuracy = true;

  public static void main(String args[]) throws IOException {
    List<TreePair> treepairs = ExperimentUtils.readAnnotatedTreePairs();

    ClassicCounter<String> labelCounter = new ClassicCounter<String>();

    TwoDimensionalCounter<String, String> confusionMatrix = new TwoDimensionalCounter<String,String>();

    GeneralDataset dataSet = new Dataset();
    List<Datum<String,String>> tempData = new ArrayList<Datum<String,String>>();

    for(TreePair validSent : treepairs) {
      for(Map.Entry<Pair<Integer,Integer>, String> labeledNPs : validSent.NPwithDEs_categories.entrySet()) {
        String np = validSent.chNPwithDE(labeledNPs.getKey());
        np = np.trim();
        String label = labeledNPs.getValue();

        Tree chTree = validSent.chTrees.get(0);
        Pair<Integer,Integer> chNPrange = labeledNPs.getKey();
        Tree chNPTree = TranslationAlignment.getTreeWithEdges(chTree,chNPrange.first, chNPrange.second+1);

        if (label.equals("no B") || label.equals("other") || label.equals("multi-DEs")) {
          continue;
        }

        // (1) make feature list
        List<String> featureList = new ArrayList<String>();
        if (ExperimentUtils.hasDEC(chNPTree)) {
          featureList.add("DEC");
        }
        if (ExperimentUtils.hasDEG(chNPTree)) {
          featureList.add("DEG");
        }
        
        // (2) make label

        // (3) Make Datum and add
        Datum<String, String> d = new BasicDatum(featureList, label);
        dataSet.add(d);
        tempData.add(d);

        // (4) collect other statistics
        labelCounter.incrementCount(label);
      }
    }

    // train classifier

    LinearClassifierFactory<String, String> factory = new LinearClassifierFactory<String, String>();
    LinearClassifier<String, String> classifier 
      = (LinearClassifier<String, String>)factory.trainClassifierV(dataSet, min, max, accuracy);

    for (Datum<String,String> d : tempData) {
      String predictedClass = classifier.classOf(d);
      confusionMatrix.incrementCount(d.label(), predictedClass);
    }

    // output information
    dataSet.summaryStatistics();
    
    System.out.println();
    System.out.println(labelCounter);
    
    System.out.println();
    System.out.println(confusionMatrix);

  }
}
