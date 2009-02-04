package mt.discrimreorder;

import java.util.*;

public class TrainingExamples {
  AlignmentMatrix matrix;
  List<IndicesAndClass> examples;

  public TrainingExamples(AlignmentMatrix matrix) { 
    this.matrix = matrix;
    extractExamples();
  }

  private void extractExamples() {
    for(int ei = 0; ei < matrix.e.length-1; ei++) {
      int ei_prime = ei+1;
      Set<Integer> ei_fs = new TreeSet<Integer>();
      Set<Integer> eiprime_fs = new TreeSet<Integer>();
      IndicesAndClass example = null;

      for(int fi = 0; fi < matrix.f.length; fi++) {
        if (matrix.fe[fi][ei]) ei_fs.add(fi);
        if (matrix.fe[fi][ei_prime]) eiprime_fs.add(fi);
      }
      int minf_ei = Collections.min(ei_fs);
      int maxf_ei = Collections.max(ei_fs);
      int minf_eiprime = Collections.min(eiprime_fs);
      int maxf_eiprime = Collections.max(eiprime_fs);

      if (maxf_ei < minf_eiprime) {
        example = new IndicesAndClass(ei, maxf_ei, minf_eiprime, IndicesAndClass.ReorderingTypes.ordered);
      } else if (maxf_eiprime < minf_ei) {
        example = new IndicesAndClass(ei, minf_ei, maxf_eiprime, IndicesAndClass.ReorderingTypes.distorted);
      }
      if (example != null) examples.add(example);
    }
  }
  
};

class IndicesAndClass {
  int tgt_i = -1;
  int src_j = -1;
  int src_jprime = -1;
  enum ReorderingTypes { ordered, distorted }
  ReorderingTypes type = null;

  public IndicesAndClass(int tgt_i, int src_j, int src_jprime, ReorderingTypes type) {
    this.tgt_i = tgt_i;
    this.src_j = src_j;
    this.src_jprime = src_jprime;
    this.type = type;
  }
};