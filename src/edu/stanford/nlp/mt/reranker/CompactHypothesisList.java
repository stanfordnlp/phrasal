package edu.stanford.nlp.mt.reranker;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

/**
 * @author Pi-Chuan Chang
 * @author Dan Cer
 */

public class CompactHypothesisList implements Serializable {
  
  public static final String N_THRESH_PROP = "nThresh";
  public static final String DEFAULT_N_THRESH= null;
  private static final long serialVersionUID = 1L;

  int[][] fIndices;
  float[][] fValues;
  int nbestSize = 0;
  double[] scores;

  FeatureIndex featureIndex;

  @Deprecated
  public CompactHypothesisList(int[][] fIndices, float[][] fValues,
    double[] bleus, int nbestSize, FeatureIndex featureIndex) {

    this.featureIndex = featureIndex;
    this.fIndices = fIndices;
    this.fValues = fValues;
    this.scores = bleus;
    this.nbestSize = nbestSize;
  }

  public CompactHypothesisList(
    FeatureIndex featureIndex,
    List<FeatureSetBank> featureSets,
    Scores pScores, int dataPtId) {
    this(featureIndex, featureSets, pScores, dataPtId, false);
  }


  public CompactHypothesisList(
    FeatureIndex featureIndex,
    List<FeatureSetBank> featureSets,
    Scores pScores, int dataPtId, boolean featIdxLock) {
    this(featureIndex, featureSets, pScores, dataPtId, false, -1);
  }

  public CompactHypothesisList(
    FeatureIndex featureIndex,
    List<FeatureSetBank> featureSets,
    Scores pScores, int dataPtId, boolean featIdxLock, int nbestS) {
    this.featureIndex = featureIndex;

    SortedSet<Integer> hypIds = pScores.getHypothesisIndices(dataPtId);
    Map<Integer, Map<String, Float>> featsForHyps
            = new HashMap<Integer, Map<String, Float>>();
    if (nbestS <= 0 || nbestS>=hypIds.size()) {
      this.nbestSize = hypIds.size();
      System.err.println("Setting NBest size to "+nbestSize);
    }
    //int[] featureCnts = new int[hypIds.size()];
    int[] featureCnts = new int[nbestSize];
    for (FeatureSetBank featureSet : featureSets) {
      for (Integer hypId : featureSet.getHypothesisIndices(dataPtId)) {
        if (hypId >= nbestSize) {continue;}
        Map<String,Float> featuresForHypId = featsForHyps.get(hypId);
        if (featuresForHypId==null) featuresForHypId = new HashMap<String,Float>();
        if (featIdxLock) {
          featuresForHypId.putAll(featureSet.getFeatures(dataPtId, hypId, featureIndex));
        } else {
          featuresForHypId.putAll(featureSet.getFeatures(dataPtId,hypId));
        }
        featsForHyps.put(hypId,featuresForHypId);
      }
    }
    for (int hypId : featsForHyps.keySet()) {
      featureCnts[hypId] += featsForHyps.get(hypId).size();
    }

    //nbestSize = hypIds.size();
    fIndices = new int[nbestSize][];
    fValues  = new float[nbestSize][];
    scores   = new double[nbestSize];

    for (Integer hypId : hypIds) {
      if (hypId >= nbestSize) { continue; }
      fIndices[hypId] = new int[featureCnts[hypId]];
      fValues[hypId] =  new float[featureCnts[hypId]];
      scores[hypId] = pScores.getScore(dataPtId, hypId);
      int topPtr = 0;
      Map<String, Float> feats = featsForHyps.get(hypId);
      for (String featName : feats.keySet()) {
        if (!featIdxLock)
          featureIndex.add(featName);
        int idx = featureIndex.indexOf(featName);
        if (idx==-1) {
          throw new RuntimeException("feature not found");
        }
        fIndices[hypId][topPtr] = idx;
        fValues[hypId][topPtr] = feats.get(featName);
        topPtr++;
      }
      if (topPtr!=featureCnts[hypId]) {
        throw new RuntimeException("check failed");
      }
    }
//    for (FeatureSetBank featureSet : featureSets) {
//      featureSet.dataSetMap.remove(dataPtId);
//      pScores.scoreMap.remove(dataPtId);
//    }
    featsForHyps = null;
    hypIds = null;
    //this.nbestSize = nbestSize;
  }

  public int[][] getFIndices() {
    return fIndices;
  }

  public float[][] getFValues() {
    return fValues;
  }

  public double[] getScores() {
    return scores;
  }

  public int size() {
    return getNBestSize();
  }

  public int getNBestSize() {
    return nbestSize;
  }

  public FeatureIndex getFeatureIndex() {
    return featureIndex;
  }

}
