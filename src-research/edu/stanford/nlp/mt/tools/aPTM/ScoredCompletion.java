package edu.stanford.nlp.mt.tools.aPTM;

//TODO(spenceg): Add getters/setters for all fields
public class ScoredCompletion {
  final String srcPrefCoverage;
  
  final String srcPhrase;
  final String tgtPhrase;

  // Source coverage is of the form "0-1-3-4-15-6" and so on
  final String srcCoverage;
  final double score;

  public ScoredCompletion(String sourcePrefixCoverage, String srcPhrase, String targetCompletion, String sourceCompletionCoverage, double score) {
    this.srcPrefCoverage = sourcePrefixCoverage;
    this.srcPhrase = srcPhrase;
    this.tgtPhrase = targetCompletion;
    this.srcCoverage = sourceCompletionCoverage;
    this.score = score;
  }

  public String toString() {
    return String.format("Source: %s, Target: %s, Coverage: %s, Model Score: %e", srcPrefCoverage, tgtPhrase, srcCoverage, score);
  }
}
