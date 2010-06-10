package edu.stanford.nlp.mt.syntax.classifyde;

import java.util.*;
import java.io.*;


class TrainDevTest {
  private static final Boolean VERBOSE = false;

  public static void main(String[] args) throws IOException {
    String[] splitnames = {"test", "dev", "train", "train", "train", "train"};
    List<String> ans = splits(6, Arrays.asList(splitnames));
    for (String a : ans) {
      System.out.println(a);
    }
  }

  public static List<String> splits(int buckets) throws IOException {
    return splits(buckets, null);
  }

  public static List<String> splits(int buckets, List<String> splitnames) throws IOException {
    List<AnnotatedTreePair> atreepairs = ExperimentUtils.readAnnotatedTreePairs(true);
    Map<String,List<Integer>> labelIndices = new HashMap<String,List<Integer>>();
    Map<Integer, String> result = new HashMap<Integer, String>();

    int npidx = 0;
    for (AnnotatedTreePair validSent : atreepairs) {
      for(int deIdxInSent : validSent.NPwithDEs_deIdx_set) {
        String np = validSent.oracleChNPwithDE(deIdxInSent);
        np = np.trim();
        String label = validSent.NPwithDEs_categories.get(deIdxInSent);

        if (VERBOSE) System.err.printf("%d\t%s\n", npidx, label);

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
      if (VERBOSE) System.err.printf("%s : %d\n", key, indices.size());
      //List<String> split = ThreeWayRandomSplit(indices.size());
      List<String> split = NWayRandomSplit(indices.size(), buckets, splitnames);
      for (int i = 0; i < split.size(); i++) {
        int index = indices.get(i);
        String set = split.get(i);
        if (result.get(index) != null) throw new RuntimeException("multiple assignment for index " + index);
        result.put(index, set);
      }
    }

    // output the decision
    npidx = 0;
    List<String> finalSplits = new ArrayList<String>();

    for (AnnotatedTreePair validSent : atreepairs) {
      //for(Map.Entry<Pair<Integer,Integer>, String> labeledNPs : validSent.NPwithDEs_categories.entrySet()) {
      for(int deIdxInSent : validSent.NPwithDEs_deIdx_set) {
        String np = validSent.oracleChNPwithDE(deIdxInSent);
        np = np.trim();
        String label = validSent.NPwithDEs_categories.get(deIdxInSent);

        if (!ExperimentUtils.is6class(label)) {
          //System.out.println("n/a");
          finalSplits.add("n/a");
        } else {
          String set = result.get(npidx);
          if (set==null) throw new RuntimeException("no assignment for index " + npidx);
          //System.out.println(set);
          finalSplits.add(set);
        }
        npidx++;
      }
    }    
    return finalSplits;
  }

  private static List<String> NWayRandomSplit(int numitems, int n, List<String> splitnames) {
    int[] nsize = NWaySplitSize(numitems, n);
    if (splitnames == null) {
      splitnames = new ArrayList<String>();
      for(int i = 0; i < n; i++) {
        splitnames.add(""+i);
      }
    }

    if (splitnames.size() != n) throw new RuntimeException("the given splitnames should have size n");
    
    List<String> splits = new ArrayList<String>();
    for(int bucket = 0; bucket < nsize.length; bucket++) {
      int size = nsize[bucket];
      String name = splitnames.get(bucket);
      for(int i = 0; i < size; i++) {
        splits.add(name);
      }
    }
    Random r = new Random();
    Collections.shuffle(splits, r);
    return splits;
  }

  private static int[] NWaySplitSize(int size, int n) {
    int nsize[] = new int[n];
    int current_size = size;
    int current_n = n;
    for(int i = 0; i < n; i++) {
      nsize[i] = (int)(((double)current_size / current_n)+0.5);
      current_size -= nsize[i];
      current_n--;
    }
    Arrays.sort(nsize);
    return nsize;
  }
  
  // this is replaced by NWayRandomSplit
  /*
  private static List<String> ThreeWayRandomSplit(int size) {
    //String[] split = new String[size];
    List<String> split = new ArrayList<String>(size);
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
      if (test.contains(i)) { split.add(i, "test"); finalTestSize++; }
      else if (dev.contains(i)) { split.add(i, "dev"); finalDevSize++; }
      else { split.add(i, "train"); finalTrainSize++; }
    }
    if (finalTestSize != testSize) throw new RuntimeException("test size error: "+finalTestSize+" != " +testSize);
    if (finalDevSize != devSize) throw new RuntimeException("dev size error");
    if (finalTrainSize != trainSize) throw new RuntimeException("train size error");

    return split;
  }
  */
}
