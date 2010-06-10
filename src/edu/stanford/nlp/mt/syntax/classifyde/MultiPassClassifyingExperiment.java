package edu.stanford.nlp.mt.syntax.classifyde;

import edu.stanford.nlp.util.*;
import edu.stanford.nlp.stats.*;
import java.util.*;

class MultiPassClassifyingExperiment {
  public static void main(String args[]) throws Exception {
    Properties props = StringUtils.argsToProperties(args);
    TwoDimensionalCounter<String,String> trainStats_6class = new TwoDimensionalCounter<String,String>();
    TwoDimensionalCounter<String,String> devStats_6class = new TwoDimensionalCounter<String,String>();
    TwoDimensionalCounter<String,String> testStats_6class = new TwoDimensionalCounter<String,String>();
    TwoDimensionalCounter<String,String> trainStats_5class = new TwoDimensionalCounter<String,String>();
    TwoDimensionalCounter<String,String> devStats_5class = new TwoDimensionalCounter<String,String>();
    TwoDimensionalCounter<String,String> testStats_5class = new TwoDimensionalCounter<String,String>();
    TwoDimensionalCounter<String,String> trainStats_2class = new TwoDimensionalCounter<String,String>();
    TwoDimensionalCounter<String,String> devStats_2class = new TwoDimensionalCounter<String,String>();
    TwoDimensionalCounter<String,String> testStats_2class = new TwoDimensionalCounter<String,String>();

    int numsplits = Integer.parseInt(props.getProperty("splits", "6"));
    int runtimes = Integer.parseInt(props.getProperty("passes", "5"));

    for (int i = 0; i < runtimes; i++) {
      System.err.println("Pass # "+i);
      // get a general split
      List<String> split = TrainDevTest.splits(numsplits);
      for (int testid = 0; testid < numsplits; testid++) {
        System.err.printf("  Using split %d/%d as test...\n", testid, numsplits);
        List<String> newsplit = transform(split, testid);
        // 5class
        props.setProperty("6class", "false");
        props.setProperty("2class", "false");
        ClassifyingExperiment exp = new ClassifyingExperiment();
        exp.run(props, newsplit, false);
        trainStats_5class.addAll(exp.trainStats);
        devStats_5class.addAll(exp.devStats);
        testStats_5class.addAll(exp.testStats);

        // 6class
        props.setProperty("6class", "true");
        props.setProperty("2class", "false");
        exp = new ClassifyingExperiment();
        exp.run(props, newsplit, false);
        trainStats_6class.addAll(exp.trainStats);
        devStats_6class.addAll(exp.devStats);
        testStats_6class.addAll(exp.testStats);
        // 2class
        props.setProperty("6class", "true");
        props.setProperty("2class", "true");
        exp = new ClassifyingExperiment();
        exp.run(props, newsplit, false);
        trainStats_2class.addAll(exp.trainStats);
        devStats_2class.addAll(exp.devStats);
        testStats_2class.addAll(exp.testStats);
      }
    }
    System.out.println("#splits="+numsplits);
    System.out.println("#passes="+runtimes);
    System.out.println("============================ 5class ============================");
    ClassifyingExperiment.displayEval(trainStats_5class, devStats_5class, testStats_5class);
    System.out.println("============================ 6class ============================");
    ClassifyingExperiment.displayEval(trainStats_6class, devStats_6class, testStats_6class);
    System.out.println("=========================== 2class ===========================");
    ClassifyingExperiment.displayEval(trainStats_2class, devStats_2class, testStats_2class);
  }

  private static List<String> transform(List<String> split, int testid) {
    String testname = ""+testid;
    List<String> newsplit = new ArrayList<String>();
    for (String label : split) {
      if (label.equals(testname))
        newsplit.add("test");
      else
        newsplit.add("train");
    }
    return newsplit;
  }
}
