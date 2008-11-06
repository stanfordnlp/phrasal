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

  private static String[] readTrainDevTest() {
    String trainDevTestFile = "C:\\cygwin\\home\\Pichuan Chang\\javanlp\\projects\\mt\\src\\mt\\translationtreebank\\data\\TrainDevTest.txt";
    String content = StringUtils.slurpFileNoExceptions(trainDevTestFile);
    String[] lines = content.split("\\n");
    return lines;
  }

  public static void main(String args[]) throws IOException {
    Properties props =StringUtils.argsToProperties(args);
    String twofeatStr = props.getProperty("2feat", "false");
    String revisedStr = props.getProperty("revised", "false");
    String ngramStr   = props.getProperty("ngram", "false");
    String firstStr   = props.getProperty("1st", "false");
    String cpOrDnpStr   = props.getProperty("deConstituent", "false");
    Boolean twofeat = Boolean.parseBoolean(twofeatStr);
    Boolean revised = Boolean.parseBoolean(revisedStr);
    Boolean ngram = Boolean.parseBoolean(ngramStr);
    Boolean first = Boolean.parseBoolean(firstStr);
    Boolean cpOrDnp = Boolean.parseBoolean(cpOrDnpStr);

    // each level
    twofeat = twofeat || revised || ngram || first || cpOrDnp;
    revised = revised || ngram || first || cpOrDnp;
    ngram   = ngram || first || cpOrDnp;
    first   = first || cpOrDnp;
    cpOrDnp = cpOrDnp;

    List<TreePair> treepairs = ExperimentUtils.readAnnotatedTreePairs();
    String[] trainDevTest = readTrainDevTest();

    ClassicCounter<String> labelCounter = new ClassicCounter<String>();

    TwoDimensionalCounter<String, String> confusionMatrix = new TwoDimensionalCounter<String,String>();

    GeneralDataset trainDataset = new Dataset();
    GeneralDataset devDataset = new Dataset();
    GeneralDataset testDataset = new Dataset();
    List<Datum<String,String>> trainData = new ArrayList<Datum<String,String>>();
    List<Datum<String,String>> devData = new ArrayList<Datum<String,String>>();
    List<Datum<String,String>> testData = new ArrayList<Datum<String,String>>();

    int npid = 0;
    for(TreePair validSent : treepairs) {
      for(Map.Entry<Pair<Integer,Integer>, String> labeledNPs : validSent.NPwithDEs_categories.entrySet()) {
        String np = validSent.chNPwithDE(labeledNPs.getKey());
        np = np.trim();
        String label = labeledNPs.getValue();

        Tree chTree = validSent.chTrees.get(0);
        Pair<Integer,Integer> chNPrange = labeledNPs.getKey();
        Tree chNPTree = TranslationAlignment.getTreeWithEdges(chTree,chNPrange.first, chNPrange.second+1);

        if (label.equals("no B") || label.equals("other") || label.equals("multi-DEs")) {
          npid++;
          continue;
        }

        // (1) make feature list
        List<String> featureList = new ArrayList<String>();
        if (ExperimentUtils.hasDEC(chNPTree)) {
          if (twofeat) featureList.add("DEC");
          if (revised)
            if (ExperimentUtils.hasVApattern(chNPTree))
              featureList.add("hasVA");
        }
        if (ExperimentUtils.hasDEG(chNPTree)) {
          if (twofeat) featureList.add("DEG");
          if (revised) {
            if (ExperimentUtils.hasADJPpattern(chNPTree))
              featureList.add("hasADJP");
            if (ExperimentUtils.hasQPpattern(chNPTree))
              featureList.add("hasQP");
            if (ExperimentUtils.hasNPPNpattern(chNPTree))
              featureList.add("hasNPPN");
          }
        }

        // get deIndices
        Sentence<TaggedWord> sentence = chNPTree.taggedYield();
        int deIdx = -1;
        for (int i = 0; i < sentence.size(); i++) {
          TaggedWord w = sentence.get(i);
          if (w.value().equals("的")) {
            if (deIdx != -1) {
              throw new RuntimeException("multiple DE");
            }
            deIdx = i;
          }
        }
        
        if (deIdx == -1) {
          throw new RuntimeException("no DE");
        }

        if (ngram) {
          List<TaggedWord> beforeDE = new ArrayList<TaggedWord>();
          List<TaggedWord> afterDE  = new ArrayList<TaggedWord>();
          
          for (int i = 0; i < sentence.size(); i++) {
            TaggedWord w = sentence.get(i);
            if (i < deIdx) beforeDE.add(w);
            if (i > deIdx) afterDE.add(w);
          }
          
          featureList.addAll(posNgramFeatures(beforeDE, "beforeDE:"));
          featureList.addAll(posNgramFeatures(beforeDE, "afterDE:"));
        }
        
        // features from first layer
        if (first) {
          Tree[] children = chNPTree.children();
          List<String> firstLayer = new ArrayList<String>();
          for (Tree t : children) {
            firstLayer.add(t.label().value());
          }
          featureList.addAll(ngramFeatures(firstLayer, "1st:"));
        }

        if (cpOrDnp) {
          Tree[] children = chNPTree.children();
          Tree deTree = null;
          for (Tree t : children) {
            int l = Trees.leftEdge(t, chNPTree);
            int r = Trees.rightEdge(t, chNPTree);
            if (l <= deIdx && deIdx <= r) {
              deTree = t;
              break;
            }
          }
          if (deTree == null) throw new RuntimeException("deTree shouldn't be null");
          // analyze deTree
          while((deTree.numChildren() == 1 && !deTree.isLeaf()) ||
                (deTree.numChildren() == 2 && deTree.lastChild().nodeString().startsWith("DE"))) {
            deTree = deTree.firstChild();
          }
          System.err.println("TREE:");
          deTree.pennPrint(System.err);
        }

        // (2) make label

        // (3) Make Datum and add
        Datum<String, String> d = new BasicDatum(featureList, label);
        if ("train".equals(trainDevTest[npid])) {
          trainDataset.add(d);
          trainData.add(d);
        }
        else if ("dev".equals(trainDevTest[npid])) {
          devDataset.add(d);
          devData.add(d);
        }
        else if ("test".equals(trainDevTest[npid])) {
          testDataset.add(d);
          testData.add(d);
        }

        // (4) collect other statistics
        labelCounter.incrementCount(label);
        npid++;
      }
    }

    if (npid != trainDevTest.length) {
      throw new RuntimeException("#np doesn't match trainDevTest");
    }

    // train classifier

    LinearClassifierFactory<String, String> factory = new LinearClassifierFactory<String, String>();
    LinearClassifier<String, String> classifier 
      = (LinearClassifier<String, String>)factory.trainClassifier(trainDataset);
    

    // output information
    System.out.println("Overall label counter:");
    System.out.println(labelCounter);
    System.out.println();

    System.out.println("Training set stats:");
    System.out.println(((Dataset)trainDataset).toSummaryStatistics());
    System.out.println();

    System.out.println("Evaluate on Training set:");
    evaluateOnSet(trainData, classifier);
    System.out.println();

    System.out.println("Evaluate on Dev set:");
    evaluateOnSet(devData, classifier);
    System.out.println();

    System.out.println("Evaluate on Test set:");
    evaluateOnSet(testData, classifier);
    System.out.println();

  }

  private static void evaluateOnSet(List<Datum<String,String>> data, LinearClassifier<String, String> lc) {
    TwoDimensionalCounter<String, String> confusionMatrix = new TwoDimensionalCounter<String,String>();
    for (Datum<String,String> d : data) {
      String predictedClass = lc.classOf(d);
      confusionMatrix.incrementCount(d.label(), predictedClass);
    }
    System.out.println("==================Confusion Matrix==================");
    System.out.print("->real");
    for (String k : confusionMatrix.firstKeySet()) {
      if (k.equals("relative clause")) k = "relc";
      else k = k.replaceAll(" ","");
      System.out.printf("\t"+k);
    }
    System.out.println();
    for (String k2 : confusionMatrix.secondKeySet()) {
      String normK2 = k2;
      if (normK2.equals("relative clause")) normK2 = "relc";
      else normK2 = normK2.replaceAll(" ","");
      System.out.print(normK2+"\t");
      for (String k1 : confusionMatrix.firstKeySet()) {
        System.out.print((int)confusionMatrix.getCount(k1,k2)+"\t");
      }
      System.out.println();
    }
    System.out.println("----------------------------------------------------");
    System.out.print("total\t");
    for (String k1 : confusionMatrix.firstKeySet()) {
      System.out.print((int)confusionMatrix.totalCount(k1)+"\t");
    }
    System.out.println();
    System.out.println("====================================================");
    System.out.println();


    ExperimentUtils.resultSummary(confusionMatrix);
    System.out.println();
    ExperimentUtils.resultCoarseSummary(confusionMatrix);
  }

  static List<String> posNgramFeatures(List<TaggedWord> words, String prefix) {
    List<String> pos = new ArrayList<String>();
    for (TaggedWord tw : words) {
      pos.add(tw.tag());
    }
    return ngramFeatures(pos, prefix);
  }

  static List<String> ngramFeatures(List<String> grams, String prefix) {
    List<String> features = new ArrayList<String>();
    StringBuilder sb;
    for (int i = -1; i < grams.size(); i++) {
      sb = new StringBuilder();
      sb.append(prefix).append(":");
      if (i == -1) sb.append(""); else sb.append(grams.get(i));
      sb.append("-");
      if (i+1 == grams.size()) sb.append(""); else sb.append(grams.get(i+1));
      features.add(sb.toString());

      if (i != -1) {
        sb = new StringBuilder();
        sb.append(prefix).append(":");
        sb.append(grams.get(i));
        features.add(sb.toString());
      }
    }
    return features;
  }
}
