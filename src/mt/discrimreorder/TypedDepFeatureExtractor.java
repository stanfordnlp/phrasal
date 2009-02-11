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
      // skip the boundary cases
      if (example.src_j > 0 && example.src_j < matrix.f.length-1 &&
          example.src_jprime > 0 && example.src_jprime < matrix.f.length-1) {
        StringBuilder path = new StringBuilder("PATH:");
        if (matrix.chGraph != null) {
          SemanticGraph chGraph = matrix.chGraph;
          List<IndexedWord> list = chGraph.vertexList();
          path.append(getPathName(example.src_j-1, example.src_jprime-1, list, chGraph));
        } else if (matrix.pathMap != null) {
          path.append(getPathName(example.src_j-1, example.src_jprime-1, matrix.pathMap));
        }
        //System.err.printf("%d - %d\n", example.src_j-1, example.src_jprime-1);
        //System.err.println("PATH="+path.toString());
        features.add(path.toString());
      } 
    }
    return features;
  }
  
  static String getPathName(int node1, int node2, TwoDimensionalMap<Integer,Integer,String> pathMap) {
    return pathMap.get(node1, node2);
  }

  static String getPathName(int node1, int node2, List<IndexedWord> list, SemanticGraph chGraph) {
    List<SemanticGraphEdge> paths = chGraph.getShortestPathEdges(list.get(node1), list.get(node2));
    int startI = node1;
    StringBuilder sb = new StringBuilder();
    if (paths==null) {
    } else {
      for (SemanticGraphEdge path : paths) {
        int govid = list.indexOf(path.getGovernor());
        int depid = list.indexOf(path.getDependent());
        sb.append(path.getRelation());
        if (startI == govid) {
          sb.append("O");
          startI = depid;
        } else if (startI == depid) {
          sb.append("R");
          startI = govid;
        } else {
          throw new RuntimeException("blah");
        }
        sb.append("-");
      }
    }
    return sb.toString();
  }
}