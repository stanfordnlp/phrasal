package edu.stanford.nlp.mt.syntax.discrimreorder;

import edu.stanford.nlp.stats.*;
import java.util.*;

public class TrainingExamples {
  List<TrainingExample> examples;

  public enum ReorderingTypes {
    ordered, distorted, ordered_disc, distorted_disc, disc
  }

  private boolean dealWithEmpty = false, dealWithMultiTarget = false;
  private int useNClass = 2;

  public TrainingExamples(boolean dealWithEmpty, boolean dealWithMultiTarget,
      int useNClass) {
    this();
    this.dealWithEmpty = dealWithEmpty;
    this.dealWithMultiTarget = dealWithMultiTarget;
    this.useNClass = useNClass;
  }

  public TrainingExamples() {
    examples = new ArrayList<TrainingExample>();
  }

  Counter<String> extractExamples(AlignmentMatrix matrix) {
    Counter<String> classCounter = new IntCounter<String>();

    int maxEinMatrix[] = null;

    if (dealWithMultiTarget) {
      maxEinMatrix = new int[matrix.f.length];
      for (int fi = 0; fi < matrix.f.length; fi++) {
        boolean set = false;
        for (int ei = matrix.e.length - 1; ei >= 0; ei--) {
          if (matrix.fe[fi][ei]) {
            maxEinMatrix[fi] = ei;
            set = true;
            break;
          }
        }
        if (!set)
          maxEinMatrix[fi] = -1;
      }
    }

    for (int ei = 0; ei < matrix.e.length - 1; ei++) {
      int ei_prime = ei + 1;
      Set<Integer> ei_fs = new TreeSet<Integer>();
      Set<Integer> eiprime_fs = new TreeSet<Integer>();
      TrainingExample example = null;

      for (int fi = 0; fi < matrix.f.length; fi++) {
        if (matrix.fe[fi][ei])
          ei_fs.add(fi);
        if (matrix.fe[fi][ei_prime])
          eiprime_fs.add(fi);
      }

      if (dealWithEmpty) {
        while (eiprime_fs.size() == 0 && ei_prime + 1 < matrix.e.length) {
          ei_prime++;
          for (int fi = 0; fi < matrix.f.length; fi++) {
            if (matrix.fe[fi][ei_prime])
              eiprime_fs.add(fi);
          }
        }
      }

      if (ei_fs.size() == 0 || eiprime_fs.size() == 0) {
        classCounter.incrementCount("empty");
        continue;
      }

      boolean skip = false;
      if (dealWithMultiTarget) {
        for (int f : ei_fs) {
          if (maxEinMatrix[f] > ei) {
            classCounter.incrementCount("mixed");
            skip = true;
            break;
          }
        }
      }

      if (!skip) {
        int minf_ei = Collections.min(ei_fs);
        int maxf_ei = Collections.max(ei_fs);
        int minf_eiprime = Collections.min(eiprime_fs);
        int maxf_eiprime = Collections.max(eiprime_fs);

        if (useNClass == 4) {
          if (maxf_ei + 1 == minf_eiprime) {
            example = new TrainingExample(ei, maxf_ei, minf_eiprime,
                ReorderingTypes.ordered);
            classCounter.incrementCount("ordered_cont");
          } else if (maxf_eiprime + 1 == minf_ei) {
            example = new TrainingExample(ei, minf_ei, maxf_eiprime,
                ReorderingTypes.distorted);
            classCounter.incrementCount("distorted_cont");
          } else if (maxf_ei < minf_eiprime) {
            example = new TrainingExample(ei, maxf_ei, minf_eiprime,
                ReorderingTypes.ordered_disc);
            classCounter.incrementCount("ordered_disc");
          } else if (maxf_eiprime < minf_ei) {
            example = new TrainingExample(ei, minf_ei, maxf_eiprime,
                ReorderingTypes.distorted_disc);
            classCounter.incrementCount("distorted_disc");
          } else {
            classCounter.incrementCount("mixed");
          }
        } else if (useNClass == 2) {
          if (maxf_ei < minf_eiprime) {
            example = new TrainingExample(ei, maxf_ei, minf_eiprime,
                ReorderingTypes.ordered);
            classCounter.incrementCount("ordered");
          } else if (maxf_eiprime < minf_ei) {
            example = new TrainingExample(ei, minf_ei, maxf_eiprime,
                ReorderingTypes.distorted);
            classCounter.incrementCount("distorted");
          } else {
            classCounter.incrementCount("mixed");
          }
        } else if (useNClass == 3) {
          if (maxf_ei + 1 == minf_eiprime) {
            example = new TrainingExample(ei, maxf_ei, minf_eiprime,
                ReorderingTypes.ordered);
            classCounter.incrementCount("ordered_cont");
          } else if (maxf_eiprime + 1 == minf_ei) {
            example = new TrainingExample(ei, minf_ei, maxf_eiprime,
                ReorderingTypes.distorted);
            classCounter.incrementCount("distorted_cont");
          } else if (maxf_ei < minf_eiprime) {
            example = new TrainingExample(ei, maxf_ei, minf_eiprime,
                ReorderingTypes.disc);
            classCounter.incrementCount("discontinuous");
          } else if (maxf_eiprime < minf_ei) {
            example = new TrainingExample(ei, minf_ei, maxf_eiprime,
                ReorderingTypes.disc);
            classCounter.incrementCount("discontinuous");
          } else {
            classCounter.incrementCount("mixed");
          }
        } else {
          throw new RuntimeException("invalid useNClass = " + useNClass);
        }
      }

      if (example != null)
        examples.add(example);
    }
    return classCounter;
  }
};

class TrainingExample {
  int tgt_i = -1;
  int src_j = -1;
  int src_jprime = -1;
  TrainingExamples.ReorderingTypes type = null;

  public TrainingExample(int tgt_i, int src_j, int src_jprime,
      TrainingExamples.ReorderingTypes type) {
    this.tgt_i = tgt_i;
    this.src_j = src_j;
    this.src_jprime = src_jprime;
    this.type = type;
  }
};
