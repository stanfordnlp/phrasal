package edu.stanford.nlp.mt.reranker;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

/**
 * @author cer (daniel.cer@colorado.edu)
 * @author Pi-Chuan Chang
 */

public class FeatureSetBank {

//  Map<Integer, Map<Integer, Map<String, Float>>> dataSetMap =
//    new HashMap<Integer, Map<Integer, Map<String, Float>>>();
  BufferedReader file;

  Map<Integer, TreeSet<Integer>> indexSetMap = new HashMap<Integer, TreeSet<Integer>>();


  String featureSetName = "Unnamed Feature Set\n";
  public FeatureSetBank() { ; }


  public void setFeature(int dataPtIdx, int hypothesisIdx) {
    if (!indexSetMap.containsKey(dataPtIdx)) {
      indexSetMap.put(dataPtIdx, new TreeSet<Integer>());
    }
    TreeSet<Integer> grpMap = indexSetMap.get(dataPtIdx);
    grpMap.add(hypothesisIdx);
  }


  public static FeatureSetBank load(String filename) throws IOException {
    return load(filename, Integer.MAX_VALUE);
  }

  public static FeatureSetBank load(String filename, int max) throws IOException {
    if (max==-1) max = Integer.MAX_VALUE;

    BufferedReader breader = null;
    // we support both gzipped and non-gzipped feature sets
    try {
      breader = new BufferedReader(new InputStreamReader(
        new GZIPInputStream(new FileInputStream(filename))));
    } catch (IOException e) {
      breader = new BufferedReader(new FileReader(filename));
    }
    FeatureSetBank featureSet = new FeatureSetBank();

    int previousDataPt = -1;
    for (String line; (line = breader.readLine()) != null; ) {
      //if (line.toLowerCase().matches("^# feature set name:.*")) {
      if (line.matches("^# feature set name:.*")) {
        String name = line.replaceFirst(".*:", "");
        featureSet.setName(name);
      }
      //line = line.replaceFirst("#.*$", "").replaceAll("\\s*$", "");
      //if (line.equals("")) continue;
      line = line.trim();
      if (line.startsWith("#")) continue;
      int split = line.indexOf(" ");
      String fields0 = line.substring(0,split);
      //String fields1 = line.substring(split+1);
      //String[] fields = line.split("\\s");
      int split2 = fields0.indexOf(",");
      //String[] sIdPair = fields[0].split(",");
      int dataPt = Integer.parseInt(fields0.substring(0,split2));
      int hypId  = Integer.parseInt(fields0.substring(split2+1));
      if (dataPt > max) {
        break;
      }
      featureSet.setFeature(dataPt, hypId);
      if (dataPt!=previousDataPt) {
        System.err.println("(Debug): (load) dataPt="+dataPt);
        previousDataPt = dataPt;
      }
    }
    breader.close();

    featureSet.resetFile(filename);
    return featureSet;
  }

  void resetFile(String filename) throws FileNotFoundException {
    BufferedReader breader;
    // we support both gzipped and non-gzipped feature sets
    try {
      breader = new BufferedReader(new InputStreamReader(
        new GZIPInputStream(new FileInputStream(filename))));
    } catch (IOException e) {
      breader = new BufferedReader(new FileReader(filename));
    }
    this.file = breader;
  }

  public void setName(String pName) { featureSetName = pName; }
  public String getName() { return featureSetName; }


  public Map<String, Float> getFeatures(int dataPtIdx, int hypothesisIdx) {
    return getFeatures(dataPtIdx, hypothesisIdx, null);
  }

  public Map<String, Float> getFeatures(int dataPtIdx, int hypothesisIdx, FeatureIndex featureIndex) {
    String line = null;
    Map<String, Float> features = new HashMap<String, Float>();
    try {
      if((line=file.readLine())==null) {
        System.err.println("end of file reached while trying to access:");
        System.err.println("  dataPtIdx="+dataPtIdx);
        System.err.println("  hypothesisIdx="+hypothesisIdx);

        throw new RuntimeException("in FeatureSetBank, data and hyp accessing has to be linear.");
      }
      for (; line!=null; line = file.readLine()) {
        //System.err.println(hypothesisIdx);
        //line = line.replaceFirst("#.*$", "").replaceAll("\\s*$", "");
        //if (line.equals("")) continue;
        line = line.trim();
        if (line.startsWith("#")) continue;
        int splitSpace = line.indexOf(" ");
        String fields0=null;// = line.substring(0,splitSpace);
        String fields1=null;// = line.substring(splitSpace+1);
        try {
          fields0 = line.substring(0,splitSpace);
          fields1 = line.substring(splitSpace+1);
        } catch (Exception e) {
          System.err.println("LINE="+line);
          e.printStackTrace();
        }
        //System.err.println("DEBUG: fields1="+fields1);
        //String[] fields = line.split("\\s");
        int split2 = fields0.indexOf(",");
        //String[] sIdPair = fields[0].split(",");
        int dataPt = Integer.parseInt(fields0.substring(0,split2));
        //System.err.println("DEBUG: dataPt="+fields0.substring(0,split2));
        int hypId  = Integer.parseInt(fields0.substring(split2+1));
        //System.err.println("DEBUG: hypId="+fields0.substring(split2+1));

        if (dataPtIdx != dataPt || hypothesisIdx != hypId) {
          if (dataPt <= dataPtIdx || hypothesisIdx <= hypId) {
            continue;
          }
            
          System.err.println("dataPtIdx="+dataPtIdx);
          System.err.println("dataPt="+dataPt);
          System.err.println("hypothesisIdx="+hypothesisIdx);
          System.err.println("hypId="+hypId);
          throw new RuntimeException("in FeatureSetBank, data and hyp accessing has to be linear.");
        }

        int start = 0;
        int split_i = 0;

        while(true) {
          if (split_i == -1) break;
          split_i = fields1.indexOf(" ", start);

          String fields_i = null;
          // take care of the last field that does not have a " " at the end
          if (split_i != -1)  fields_i = fields1.substring(start,split_i);
          else                fields_i = fields1.substring(start);
          //System.err.println("DEBUG: fields_i="+fields_i);
          start = split_i+1;
          
          int split = fields_i.indexOf(":");
          if (featureIndex != null) {
            //if (featureIndex.indexOf(featurePair[0])==-1) {
            if (featureIndex.indexOf(fields_i.substring(0,split))==-1) {
              continue;
            }
          }
          //float v = (featurePair.length == 1 ? 1.0 :
          //Float.parseFloat(featurePair[1]));
          float v = Float.parseFloat(fields_i.substring(split+1));
          features.put(fields_i.substring(0,split),v);
          //System.err.println("DEBUG: PUT: fields_i[0]="+fields_i.substring(0,split));
          //System.err.println("DEBUG: PUT: v="+v);
        }
        return features;
      }
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException("IOException");
    }
    return null;
  }

  public SortedSet<Integer> getDataPointIndices() {
    return new TreeSet<Integer>(indexSetMap.keySet());
  }

  public SortedSet<Integer> getHypothesisIndices(int dataPtIdx) {
    return indexSetMap.get(dataPtIdx);
  }

}
