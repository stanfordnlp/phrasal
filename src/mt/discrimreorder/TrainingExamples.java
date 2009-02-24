package mt.discrimreorder;

import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.international.pennchinese.*;
import edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams;
import java.util.*;
import java.io.*;

public class TrainingExamples {
  List<TrainingExample> examples;
  public enum ReorderingTypes { ordered, distorted }

  private boolean dealWithEmpty=false, dealWithMultiTarget=false;

  public TrainingExamples(boolean dealWithEmpty, boolean dealWithMultiTarget) {
    this();
    this.dealWithEmpty = dealWithEmpty;
    this.dealWithMultiTarget = dealWithMultiTarget;
  }
  public TrainingExamples() {
    examples = new ArrayList<TrainingExample>();
  }

  Counter<String> extractExamples(AlignmentMatrix matrix) {
    Counter<String> classCounter = new IntCounter<String>();
    for(int ei = 0; ei < matrix.e.length-1; ei++) {
      int ei_prime = ei+1;
      Set<Integer> ei_fs = new TreeSet<Integer>();
      Set<Integer> eiprime_fs = new TreeSet<Integer>();
      TrainingExample example = null;

      for(int fi = 0; fi < matrix.f.length; fi++) {
        if (matrix.fe[fi][ei]) ei_fs.add(fi);
        if (matrix.fe[fi][ei_prime]) eiprime_fs.add(fi);
      }

      if (dealWithEmpty) {
        while (eiprime_fs.size() == 0 && ei_prime+1 < matrix.e.length) {
          ei_prime++;
          for(int fi = 0; fi < matrix.f.length; fi++) {
            if (matrix.fe[fi][ei_prime]) eiprime_fs.add(fi);
          }
        }
      }

      if (ei_fs.size() == 0 || eiprime_fs.size() == 0) {
        classCounter.incrementCount("empty");
        continue;
      }

      int minf_ei = Collections.min(ei_fs);
      int maxf_ei = Collections.max(ei_fs);
      int minf_eiprime = Collections.min(eiprime_fs);
      int maxf_eiprime = Collections.max(eiprime_fs);

      if (maxf_ei < minf_eiprime) {
        example = new TrainingExample(ei, maxf_ei, minf_eiprime, ReorderingTypes.ordered);
        classCounter.incrementCount("ordered");
      } else if (maxf_eiprime < minf_ei) {
        example = new TrainingExample(ei, minf_ei, maxf_eiprime, ReorderingTypes.distorted);
        classCounter.incrementCount("distorted");
      } else {
        classCounter.incrementCount("mixed");
      }
      if (example != null) examples.add(example);
    }
    return classCounter;
  }
};

class TrainingExample {
  int tgt_i = -1;
  int src_j = -1;
  int src_jprime = -1;
  TrainingExamples.ReorderingTypes type = null;

  public TrainingExample(int tgt_i, int src_j, int src_jprime, TrainingExamples.ReorderingTypes type) {
    this.tgt_i = tgt_i;
    this.src_j = src_j;
    this.src_jprime = src_jprime;
    this.type = type;
  }
};