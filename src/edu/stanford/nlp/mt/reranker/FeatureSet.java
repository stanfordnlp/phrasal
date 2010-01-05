package edu.stanford.nlp.mt.reranker;


import edu.stanford.nlp.stats.ClassicCounter;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * @author cer (daniel.cer@colorado.edu)
 * @author Pi-Chuan Chang
 */

public class FeatureSet {
  public static final String PRUNE_THRESH_PROP = "pruneThresh";
  public static final String DEFAULT_PRUNE_THRESH = "0";
  public static final String N_THRESH_PROP = "nThresh";
  public static final String DEFAULT_N_THRESH= null;

  Map<Integer, Map<Integer, Map<String, Double>>> dataSetMap =
    new HashMap<Integer, Map<Integer, Map<String, Double>>>();

  String featureSetName = "Unnamed Feature Set\n";
  public FeatureSet() { ; }

  public static void pruneFeaturesByCount(FeatureSet featSet, int threshold, ClassicCounter<String> cnt) {
    /*
    Counter cnt = new Counter();
    for (Map.Entry<Integer,Map<Integer, Map<String, Double>>> e : dataSetMap.entrySet()) {
      int dataPt = e.getKey();
      Map<Integer, Map<String, Double>> v = e.getValue();
      for (Map.Entry<Integer, Map<String, Double>> sentE : v.entrySet()) {
        int hypId = sentE.getKey();
        Map<String,Double> feats = sentE.getValue();
        for (Map.Entry<String,Double> feat : feats.entrySet()) {
          cnt.incrementCount(feat.getKey(), feat.getValue());
        }
      }
    }
    */
    for (Map.Entry<Integer,Map<Integer, Map<String, Double>>> e : featSet.dataSetMap.entrySet()) {
      Map<Integer, Map<String, Double>> v = e.getValue();
      for (Map.Entry<Integer, Map<String, Double>> sentE : v.entrySet()) {
        Map<String,Double> feats = sentE.getValue();
        List<String> toRemove = new ArrayList<String>();
        for (Map.Entry<String,Double> feat : feats.entrySet()) {
          //cnt.incrementCount(feat.getKey(), feat.getValue());
          String featName = feat.getKey();
          if (cnt.getCount(featName) < threshold && !featName.startsWith("moses")) {
            toRemove.add(featName);
          }
        }
        for (String remove : toRemove) {
          feats.remove(remove);
        }
      }
    }


  }
  public static FeatureSet load(String filename) throws IOException {
    ClassicCounter<String> cnt = new ClassicCounter<String>();
    BufferedReader breader = null;
    // we support both gzipped and non-gzipped feature sets
    String n = System.getProperty(N_THRESH_PROP, DEFAULT_N_THRESH);
    int nThresh = Integer.MAX_VALUE;
    if (n!=null)
      nThresh = Integer.parseInt(n);
    System.err.println("Max N = "+nThresh);

    try {
      breader = new BufferedReader(new InputStreamReader(
        new GZIPInputStream(new FileInputStream(filename))));
    } catch (IOException e) {
      breader = new BufferedReader(new FileReader(filename));
    }
    FeatureSet featureSet = new FeatureSet();
    
    for (String line; (line = breader.readLine()) != null; ) {
      if (line.toLowerCase().matches("^# feature set name:.*")) {
        String name = line.replaceFirst(".*:", "");
        featureSet.setName(name); 
      }
      line = line.replaceFirst("#.*$", "").replaceAll("\\s*$", "");
      if (line.equals("")) continue;
      String[] fields = line.split("\\s");
      String[] sIdPair = fields[0].split(",");
      int dataPt = Integer.parseInt(sIdPair[0]);
      int hypId = Integer.parseInt(sIdPair[1]);
      if (hypId >= nThresh) continue;
      for (int i = 1; i < fields.length; i++) {
        String[] featurePair = fields[i].split(":");
        double v = (featurePair.length == 1 ? 1.0 : 
          Double.parseDouble(featurePair[1]));
        featureSet.setFeature(dataPt, hypId, featurePair[0], v);
        cnt.incrementCount(featurePair[0],v);
      }
    }
    int pruneThresh = Integer.parseInt(System.getProperty(PRUNE_THRESH_PROP, DEFAULT_PRUNE_THRESH));
    System.err.println("Pruning features with counts lower than "+pruneThresh);
    System.err.println("Before pruning, # of feature types="+cnt.size());
    pruneFeaturesByCount(featureSet, pruneThresh, cnt);
    System.err.println("Done pruning.");
    return featureSet;
  }
   
  public void write(String filename) throws IOException {
    PrintStream pstrm = new PrintStream(new FileOutputStream(filename));
    pstrm.printf("# Feature Set Name: %f\n", featureSetName);
    pstrm.printf("# Created: %s\n", new Date());
    for (Integer dataPt  : new TreeSet<Integer>(dataSetMap.keySet())) {
      pstrm.printf("\n# Data Pt %d\n", dataPt);
      for (Integer hypId : new TreeSet<Integer>(
        dataSetMap.get(dataPt).keySet())) {
        pstrm.printf("%d,%d");
        for (String feat : new TreeSet<String>(dataSetMap.get(dataPt).
          get(hypId).keySet())) {
          pstrm.printf(" %s:%f", feat, dataSetMap.get(dataPt).get(hypId).
            get(feat));
        } 
        pstrm.println();
      }
    }
    pstrm.close();
  }

  public void setName(String pName) { featureSetName = pName; }
  public String getName() { return featureSetName; }

  public void setFeature(int dataPtIdx, int hypothesisIdx, 
                    String featureName, double featureVal) {
    if (!dataSetMap.containsKey(dataPtIdx)) {
      dataSetMap.put(dataPtIdx, new HashMap<Integer, Map<String, Double>>());
    }
    Map<Integer, Map<String, Double>> grpMap = dataSetMap.get(dataPtIdx);
    if (!grpMap.containsKey(hypothesisIdx)) {
        grpMap.put(hypothesisIdx, new HashMap<String, Double>());
    } 
    Map<String, Double> featuresMap = grpMap.get(hypothesisIdx);
    featuresMap.put(featureName, featureVal);
  } 
  
  public Map<String, Double> getFeatures(int dataPtIdx, int hypothesisIdx) {
    if (dataSetMap.containsKey(dataPtIdx) &&
        dataSetMap.get(dataPtIdx).containsKey(hypothesisIdx)) {
       return dataSetMap.get(dataPtIdx).get(hypothesisIdx);
    }
    return null;
  }
 
  public SortedSet<Integer> getDataPointIndices() {
    return new TreeSet<Integer>(dataSetMap.keySet()); 
  }

  public SortedSet<Integer> getHypothesisIndices(int dataPtIdx) {
    return new TreeSet<Integer>(dataSetMap.get(dataPtIdx).keySet());
  }

  static public void main(String[] args) throws Exception {
    if (args.length != 1) {
      System.err.println(
         "Usage:\n\tjava ...AuxiliarFeatures (feature filename)\n");
      System.exit(-1);
    }
    FeatureSet featureSet = FeatureSet.load(args[0]);
    for (Integer dataPt : featureSet.getDataPointIndices()) {
     System.out.printf("\nData pt: %d\n", dataPt);
     for (Integer hypPt : featureSet.getHypothesisIndices(dataPt)) {
       System.out.printf("\t%d:", dataPt);
       Map<String, Double> features = featureSet.getFeatures(dataPt, hypPt);
       for (String feature : new TreeSet<String>(features.keySet())) {
         System.out.printf(" %s:%f", feature, features.get(feature));
       }
       System.out.println();
     }
    } 
  }
}
