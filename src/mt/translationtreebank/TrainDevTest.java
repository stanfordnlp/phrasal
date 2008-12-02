package mt.translationtreebank;

import java.util.*;
import java.io.*;
import edu.stanford.nlp.util.*;

class TrainDevTest {
  public static void main(String[] args) throws IOException {
    List<TreePair> treepairs = ExperimentUtils.readAnnotatedTreePairs(true);
    Map<String,List<Integer>> labelIndices = new HashMap<String,List<Integer>>();
    Map<Integer, String> result = new HashMap<Integer, String>();

    int npidx = 0;
    for (TreePair validSent : treepairs) {

      //for(Map.Entry<Pair<Integer,Integer>, String> labeledNPs : validSent.NPwithDEs_categories.entrySet()) {
      for(int deIdxInSent : validSent.NPwithDEs_deIdx_set) {
        Pair<Integer, Integer> chNPrange = validSent.NPwithDEs_deIdx.get(deIdxInSent);
        String np = validSent.oracleChNPwithDE(deIdxInSent);
        np = np.trim();
        String label = validSent.NPwithDEs_categories.get(deIdxInSent);

        System.err.printf("%d\t%s\n", npidx, label);

        //if (!ExperimentUtils.is5class(label)) {
        if (!ExperimentUtils.is6class(label)) {
          //nothing
        } else {
          List<Integer> list = labelIndices.get(label);
          if (list==null) list = new ArrayList<Integer>();
          list.add(npidx);
          labelIndices.put(label, list);
        }
        npidx++;
      }
    }
    
    // randomize the split
    for (String key : labelIndices.keySet()) {
      List<Integer> indices = labelIndices.get(key);
      System.err.printf("%s : %d\n", key, indices.size());
      String[] split = ThreeWayRandomSplit(indices.size());
      for (int i = 0; i < split.length; i++) {
        int index = indices.get(i);
        String set = split[i];
        if (result.get(index) != null) throw new RuntimeException("multiple assignment for index " + index);
        result.put(index, set);
      }
    }

    // output the decision
    npidx = 0;
    for (TreePair validSent : treepairs) {
      //for(Map.Entry<Pair<Integer,Integer>, String> labeledNPs : validSent.NPwithDEs_categories.entrySet()) {
      for(int deIdxInSent : validSent.NPwithDEs_deIdx_set) {
        Pair<Integer, Integer> chNPrange = validSent.NPwithDEs_deIdx.get(deIdxInSent);
        String np = validSent.oracleChNPwithDE(deIdxInSent);
        np = np.trim();
        String label = validSent.NPwithDEs_categories.get(deIdxInSent);

        //if (!ExperimentUtils.is5class(label)) {
        if (!ExperimentUtils.is6class(label)) {
          System.out.println("n/a");
        } else {
          String set = result.get(npidx);
          if (set==null) throw new RuntimeException("no assignment for index " + npidx);
          System.out.println(set);
        }
        npidx++;
      }
    }    
  }

  private static String[] ThreeWayRandomSplit(int size) {
    String[] split = new String[size];
    int devSize   = size / 6;
    int testSize  = size / 6;
    int trainSize = size - devSize - testSize;
    Set<Integer>  dev  = new HashSet<Integer>();
    Set<Integer> test  = new HashSet<Integer>();
    // fill dev
    Random r = new Random();
    while(dev.size() < devSize) {
      int i = r.nextInt(size);
      dev.add(i);
    }
    while(test.size() < testSize) {
      int i = r.nextInt(size);
      if ( !dev.contains(i)) {
        test.add(i);
      }
    }

    int finalTestSize = 0;
    int finalDevSize = 0;
    int finalTrainSize = 0;
    for (int i = 0; i < size; i++) {
      if (test.contains(i)) { split[i] = "test"; finalTestSize++; }
      else if (dev.contains(i)) { split[i] = "dev"; finalDevSize++; }
      else { split[i] = "train"; finalTrainSize++; }
    }
    if (finalTestSize != testSize) throw new RuntimeException("test size error: "+finalTestSize+" != " +testSize);
    if (finalDevSize != devSize) throw new RuntimeException("dev size error");
    if (finalTrainSize != trainSize) throw new RuntimeException("train size error");

    return split;
  }
}
