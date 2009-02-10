package mt.discrimreorder;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.international.pennchinese.*;
import edu.stanford.nlp.trees.semgraph.*;
import edu.stanford.nlp.ling.IndexedWord;

public class TypedDepFeatureExtractor implements FeatureExtractor {
  boolean HEAD_DEP_DIR;
  
  static final Set<String> OPTS = new HashSet<String>();
  static {
    OPTS.addAll(
      Arrays.asList(
        "head_dep_dir"
        ));
  }


  public TypedDepFeatureExtractor(Properties props) throws Exception {
    HEAD_DEP_DIR  = Boolean.parseBoolean(props.getProperty("head_dep_dir", "false"));

    System.out.println();
    System.out.println("TypedDepFeatureExtractor stats:");
    System.out.println("HEAD_DEP_DIR = " + HEAD_DEP_DIR);
    System.out.println("---------------------------");
  }

  public List<String> extractFeatures(AlignmentMatrix matrix, TrainingExample example) {
    List<String> features = new ArrayList<String>();
    Set<Integer> gIdxs = null;
    String headDep = null;
    
    if (HEAD_DEP_DIR) {
      SemanticGraph chGraph = matrix.chGraph;
      List<IndexedWord> list = chGraph.vertexList();
      /*
      System.err.println("F="+StringUtils.join(matrix.f, " "));
      System.err.println("F.len="+matrix.f.length);
      System.err.println("list.size="+list.size());
      */

      // skip the boundary cases
      if (example.src_j > 0 && example.src_j < matrix.f.length-1 &&
          example.src_jprime > 0 && example.src_jprime < matrix.f.length-1) {
        String srcj_word = matrix.getSourceWord(example.src_j);
        String srcj2_word = matrix.getSourceWord(example.src_jprime);
        //System.err.printf("path between %s(%d) and %s(%d):\n", srcj_word, example.src_j, srcj2_word, example.src_jprime);
        List<SemanticGraphEdge> paths = chGraph.getShortestPathEdges(list.get(example.src_j-1), list.get(example.src_jprime-1));
        int startI = example.src_j-1;
        if (paths==null) {
          //System.err.println("paths=null");
        } else {
          StringBuilder sb = new StringBuilder("PATH:");
          for (SemanticGraphEdge path : paths) {
            int govid = list.indexOf(path.getGovernor());
            int depid = list.indexOf(path.getDependent());
            sb.append(path.getRelation());
            if (startI == govid) {
              startI = depid;
            } else if (startI == depid) {
              sb.append("R");
              startI = govid;
            } else {
              throw new RuntimeException("blah");
            }
            sb.append("-");
          }
          //System.err.println("DEBUG: add: " + sb.toString());
          features.add(sb.toString());
        }
      }
    }

    return features;
  }
}
