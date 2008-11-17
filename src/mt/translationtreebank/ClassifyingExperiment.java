package mt.translationtreebank;

import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.ling.*;
import java.io.*;
import java.util.*;

class ClassifyingExperiment {
  private static String[] readTrainDevTest() {
    //String trainDevTestFile = "C:\\cygwin\\home\\Pichuan Chang\\javanlp\\projects\\mt\\src\\mt\\translationtreebank\\data\\TrainDevTest.txt";
    String trainDevTestFile = "projects/mt/src/mt/translationtreebank/data/TrainDevTest.txt";
    String content = StringUtils.slurpFileNoExceptions(trainDevTestFile);
    String[] lines = content.split("\\n");
    return lines;
  }

  public static List<String> extractFeatures(int deIdxInSent, TreePair validSent, Properties props) {
    throw new RuntimeException("Not implemented");
    //return new ArrayList<String>();
  }

  public static void main(String args[]) throws Exception {
    Properties props = StringUtils.argsToProperties(args);

    String featurizerStr= props.getProperty("featurizer", "mt.translationtreebank.FullInformationFeaturizer");
    Featurizer featurizer = (Featurizer)Class.forName(featurizerStr).newInstance();

    String reducedCatStr= props.getProperty("useReducedCategory", "true");
    String nonOracleTreeStr= props.getProperty("nonOracleTree", "false");
    String twofeatStr   = props.getProperty("2feat", "false");
    String revisedStr   = props.getProperty("revised", "false");
    String ngramStr     = props.getProperty("ngram", "false");
    String firstStr   = props.getProperty("1st", "false");
    String lastcharNStr = props.getProperty("lastcharN", "false");
    String lastcharNgramStr = props.getProperty("lastcharNgram", "false");
    String pwordStr     = props.getProperty("pword", "false");
    String pathStr      = props.getProperty("path", "false");
    String percentageStr= props.getProperty("percentage", "false");

    Boolean reducedCategory = Boolean.parseBoolean(reducedCatStr);
    Boolean nonOracleTree = Boolean.parseBoolean(nonOracleTreeStr);
    Boolean twofeat = Boolean.parseBoolean(twofeatStr);
    Boolean revised = Boolean.parseBoolean(revisedStr);
    Boolean ngram = Boolean.parseBoolean(ngramStr);
    Boolean first = Boolean.parseBoolean(firstStr);
    Boolean lastcharN = Boolean.parseBoolean(lastcharNStr);
    Boolean lastcharNgram = Boolean.parseBoolean(lastcharNgramStr);
    Boolean pword = Boolean.parseBoolean(pwordStr);
    Boolean path = Boolean.parseBoolean(pathStr);
    Boolean percentage = Boolean.parseBoolean(percentageStr);

    // each level

    /*
    twofeat = twofeat || revised || ngram || first || lastcharN || lastcharNgram || pword || path || percentage;
    revised = revised || ngram || first || lastcharN || lastcharNgram || pword || path || percentage;
    ngram   = ngram || first || lastcharN || lastcharNgram || pword || path || percentage;
    first   = first || lastcharN || lastcharNgram || pword || path || percentage;
    lastcharN   = lastcharN || lastcharNgram || pword || path || percentage;
    lastcharNgram = lastcharNgram || pword || path || percentage;
    pword   = pword || path || percentage;
    path = path || percentage;
    percentage = percentage;
    */

    List<TreePair> treepairs;
    treepairs = ExperimentUtils.readAnnotatedTreePairs(reducedCategory, nonOracleTree);

    String[] trainDevTest = readTrainDevTest();

    ClassicCounter<String> labelCounter = new ClassicCounter<String>();

    TwoDimensionalCounter<String, String> confusionMatrix = new TwoDimensionalCounter<String,String>();

    GeneralDataset trainDataset = new Dataset();
    GeneralDataset devDataset = new Dataset();
    GeneralDataset testDataset = new Dataset();
    GeneralDataset otherDataset = new Dataset();
    List<Datum<String,String>> trainData = new ArrayList<Datum<String,String>>();
    List<Datum<String,String>> devData = new ArrayList<Datum<String,String>>();
    List<Datum<String,String>> testData = new ArrayList<Datum<String,String>>();
    List<Datum<String,String>> otherData = new ArrayList<Datum<String,String>>();

    int npid = 0;
    for(TreePair validSent : treepairs) {
      for (int deIdxInSent : validSent.NPwithDEs_deIdx_set) {
        List<String> featureList = featurizer.extractFeatures(deIdxInSent, validSent, props);
        String label = validSent.NPwithDEs_categories.get(deIdxInSent);

        // (2) make label

        // (3) Make Datum and add
        Datum<String, String> d = new BasicDatum(featureList, label);
        //if ("train".equals(trainDevTest[npid])) {
        if (trainDevTest[npid].endsWith("train")) {
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
        } else if ("n/a".equals(trainDevTest[npid])) {
          otherDataset.add(d);
          otherData.add(d);
        } else {
          throw new RuntimeException("trainDevTest error, line: "+trainDevTest[npid]);
        }
   

        // (4) collect other statistics
        labelCounter.incrementCount(label);
        npid++;
      }
    }

    if (npid != trainDevTest.length) {
      //throw new RuntimeException("#np doesn't match trainDevTest");
      System.err.println("#np doesn't match trainDevTest. Is this partial test?");
    }


    // train classifier

    LinearClassifierFactory<String, String> factory = new LinearClassifierFactory<String, String>();
    LinearClassifier<String, String> classifier 
      = (LinearClassifier<String, String>)factory.trainClassifier(trainDataset);


    String allWeights = classifier.toAllWeightsString();
    System.err.println("-------------------------------------------");
    System.err.println(allWeights);
    System.err.println("-------------------------------------------");
    System.err.println(classifier.toHistogramString());
    System.err.println("-------------------------------------------");

    // output information
    System.out.println("Overall label counter:");
    System.out.println(labelCounter);
    System.out.println();

    System.out.println("Training set stats:");
    System.out.println(((Dataset)trainDataset).toSummaryStatistics());
    System.out.println();

    PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter("train")));
    System.out.println("Evaluate on Training set:");
    evaluateOnSet(trainData, classifier, pw);
    pw.close();
    System.out.println();

    System.out.println("Evaluate on Dev set:");
    pw = new PrintWriter(new BufferedWriter(new FileWriter("dev")));
    evaluateOnSet(devData, classifier, pw);
    pw.close();
    System.out.println();

    System.out.println("Evaluate on Test set:");
    pw = new PrintWriter(new BufferedWriter(new FileWriter("test")));
    evaluateOnSet(testData, classifier, pw);
    pw.close();
    System.out.println();
    
    System.out.println("Evaluate on Other set:");
    pw = new PrintWriter(new BufferedWriter(new FileWriter("other")));
    evaluateOnSet(otherData, classifier, pw);
    pw.close();
    System.out.println();
  }

  private static void evaluateOnSet(List<Datum<String,String>> data, LinearClassifier<String, String> lc) {
    evaluateOnSet(data, lc, null);
  }

  private static void evaluateOnSet(List<Datum<String,String>> data, LinearClassifier<String, String> lc, PrintWriter pw) {
    TwoDimensionalCounter<String, String> confusionMatrix = new TwoDimensionalCounter<String,String>();
    for (Datum<String,String> d : data) {
      String predictedClass = lc.classOf(d);
      confusionMatrix.incrementCount(d.label(), predictedClass);
      if (pw!=null) pw.printf("%s\t%s\n", d.label(), predictedClass);
    }
    System.out.println("==================Confusion Matrix==================");
    System.out.print("->real");
    TreeSet<String> firstKeySet = new TreeSet<String>();
    firstKeySet.addAll(confusionMatrix.firstKeySet());
    TreeSet<String> secondKeySet = new TreeSet<String>();
    secondKeySet.addAll(confusionMatrix.secondKeySet());
    for (String k : firstKeySet) {
      if (k.equals("relative clause")) k = "relc";
      else k = k.replaceAll(" ","");
      System.out.printf("\t"+k);
    }
    System.out.println();
    for (String k2 : secondKeySet) {
      String normK2 = k2;
      if (normK2.equals("relative clause")) normK2 = "relc";
      else normK2 = normK2.replaceAll(" ","");
      System.out.print(normK2+"\t");
      for (String k1 : firstKeySet) {
        System.out.print((int)confusionMatrix.getCount(k1,k2)+"\t");
      }
      System.out.println();
    }
    System.out.println("----------------------------------------------------");
    System.out.print("total\t");
    for (String k1 : firstKeySet) {
      System.out.print((int)confusionMatrix.totalCount(k1)+"\t");
    }
    System.out.println();
    System.out.println("====================================================");
    System.out.println();


    ExperimentUtils.resultSummary(confusionMatrix);
    System.out.println();
    ExperimentUtils.resultCoarseSummary(confusionMatrix);
  }
}
