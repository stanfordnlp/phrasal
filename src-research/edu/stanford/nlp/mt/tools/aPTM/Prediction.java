package edu.stanford.nlp.mt.tools.aPTM;

public class Prediction {
  final String srcPrefCoverage;
  final String tgtPhrase;

  // Source coverage is of the form "0-1-3-4-15-6" and so on
  final String srcCoverage;

  public Prediction(String srcPrefCov, String tgtPhrase, String srcCov) {
    this.srcPrefCoverage = srcPrefCov;
    this.tgtPhrase = tgtPhrase;
    this.srcCoverage = srcCov;
  }
  
  @Override
  public String toString() {
    return String.format("pred: %s | cset: %s | prefcset: %s",tgtPhrase, srcCoverage, srcPrefCoverage);
  }
}
