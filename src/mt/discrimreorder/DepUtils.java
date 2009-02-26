package mt.discrimreorder;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.trees.international.pennchinese.*;
import edu.stanford.nlp.trees.semgraph.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.ling.IndexedWord;


public class DepUtils {
  public static void addPathsToMap(String path, TwoDimensionalMap<Integer,Integer,String> pathMap) {
    path = path.trim();
    if (path.length() > 0) {
      String[] paths = path.split(" ");
      for (String tuple : paths) {
        String[] pieces = tuple.split(":");
        if (pieces.length != 3) throw new RuntimeException("wrong format: "+path);
        int i = Integer.parseInt(pieces[0]);
        int j = Integer.parseInt(pieces[1]);
        String pname = pieces[2];
        if (pathMap.get(i,j) == null) {
          pathMap.put(i,j,pname);
        }
      }
    }

  }
  public static String getPathName(int node1, int node2, TwoDimensionalMap<Integer,Integer,String> pathMap) {
    return pathMap.get(node1, node2);
  }
  

  static String getPathName(int node1, int node2, List<IndexedWord> list, SemanticGraph chGraph) {
    return getPathName(node1, node2, list, chGraph, false);
  }

  static String getPathName(int node1, int node2, List<IndexedWord> list, SemanticGraph chGraph, boolean useSameDirPath) {
    
    if (useSameDirPath) {
      if (node1 > node2) {
        int tmp = node1;
        node1 = node2;
        node2 = tmp;
      }
    }
    List<SemanticGraphEdge> paths = chGraph.getShortestPathEdges(list.get(node1), list.get(node2));
    int startI = node1;
    StringBuilder sb = new StringBuilder();
    if (paths==null) {
    } else {
      //for (SemanticGraphEdge path : paths) {
      for (int pi = 0; pi < paths.size(); pi++) {
        SemanticGraphEdge path = paths.get(pi);
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