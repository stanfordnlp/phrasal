package mt.discrimreorder;

import java.util.*;
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
    
    if (HEAD_DEP_DIR) {
      // skip the boundary cases
      if (example.src_j > 0 && example.src_j < matrix.f.length-1 &&
          example.src_jprime > 0 && example.src_jprime < matrix.f.length-1) {
        StringBuilder path = new StringBuilder("PATH:");
        if (matrix.chGraph != null) {
          SemanticGraph chGraph = matrix.chGraph;
          List<IndexedWord> list = chGraph.vertexList();
          path.append(DepUtils.getPathName(example.src_j-1, example.src_jprime-1, list, chGraph));
        } else if (matrix.pathMap != null) {
          path.append(DepUtils.getPathName(example.src_j-1, example.src_jprime-1, matrix.pathMap));
        }
        //System.err.printf("%d - %d\n", example.src_j-1, example.src_jprime-1);
        //System.err.println("PATH="+path.toString());
        features.add(path.toString());
      } 
    }
    return features;
  }
  

}