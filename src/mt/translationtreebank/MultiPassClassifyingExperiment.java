package mt.translationtreebank;

import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.ling.*;
import java.io.*;
import java.util.*;

class MultiPassClassifyingExperiment {
  public static void main(String args[]) throws Exception {
    Properties props = StringUtils.argsToProperties(args);
    TwoDimensionalCounter<String,String> trainStats = new TwoDimensionalCounter<String,String>();
    TwoDimensionalCounter<String,String> devStats = new TwoDimensionalCounter<String,String>();
    TwoDimensionalCounter<String,String> testStats = new TwoDimensionalCounter<String,String>();
    
    int runtimes = 10;
    for (int i = 0; i < runtimes; i++) {
      List<String> split = TrainDevTest.splits();
      ClassifyingExperiment exp = new ClassifyingExperiment();
      exp.run(props, split, false);
      trainStats.addAll(exp.trainStats);
      devStats.addAll(exp.devStats);
      testStats.addAll(exp.testStats);
    }

    ClassifyingExperiment.displayEval(trainStats, devStats, testStats);
  }
}