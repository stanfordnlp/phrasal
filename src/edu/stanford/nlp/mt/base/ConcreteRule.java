package edu.stanford.nlp.mt.base;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.util.Generics;

/**
 * A translation rule that is associated with a particular source span
 * in an input sentence.
 * 
 * @author danielcer
 * 
 * @param <TK>
 */
public class ConcreteRule<TK,FV> implements
    Comparable<ConcreteRule<TK,FV>> {

  /**
   * The underlying translation rule.
   */
  public final Rule<TK> abstractRule;
  
  /**
   * The source coverage of this rule.
   */
  public final CoverageSet sourceCoverage;
  
  /**
   * The phrase table from which the rule was queried.
   */
  public final String phraseTableName;
  
  /**
   * The left edge in the source sequence of the source side
   * of this rule.
   */
  public final int sourcePosition;
  
  /**
   * The isolation score of this rule.
   */
  public double isolationScore;
  
  /**
   * Features that are extracted at query-time and then
   * cached.
   */
  public List<FeatureValue<FV>> cachedFeatureList;

  public enum LinearDistortionType {
    standard, first_contiguous_segment, last_contiguous_segment, closest_contiguous_segment, min_first_last_contiguous_segment, average_distance
  }

  private static LinearDistortionType linearDistortionType = LinearDistortionType.standard;

  public static void setLinearDistortionType(String type) {
    // System.err.println("Linear distortion type: "+type);
    linearDistortionType = LinearDistortionType.valueOf(type);
    if (linearDistortionType == LinearDistortionType.standard
        && Phrasal.withGaps)
      System.err
          .println("warning: standard linear distortion with DTU phrases.");
  }

  public ConcreteRule(Rule<TK> abstractRule,
      CoverageSet sourceCoverage,
      RuleFeaturizer<TK, FV> phraseFeaturizer, Scorer<FV> scorer,
      Sequence<TK> sourceSequence, String phraseTableName, int sourceInputId) {
    this.abstractRule = abstractRule;
    this.sourceCoverage = sourceCoverage;
    this.phraseTableName = phraseTableName;
    this.sourcePosition = sourceCoverage.nextSetBit(0);
    Featurizable<TK, FV> f = new Featurizable<TK, FV>(sourceSequence, this,
        sourceInputId);
    List<FeatureValue<FV>> features = phraseFeaturizer == null ? 
        new ArrayList<FeatureValue<FV>>() : phraseFeaturizer.ruleFeaturize(f);
    cachedFeatureList = Generics.newLinkedList();
    for (FeatureValue<FV> feature : features) {
      if ( ! feature.doNotCache) {
        cachedFeatureList.add(feature);
      }
    }
    this.isolationScore = scorer == null ? Double.MIN_VALUE : scorer.getIncrementalScore(features);
  }

  /**
   * TODO(spenceg): Merge with the constructor above. This is kludgey, and the DTU part
   * does not justify a separate constructor.
   * 
   * @param abstractRule
   * @param sourceCoverage
   * @param phraseFeaturizer
   * @param scorer
   * @param sourceSequence
   * @param phraseTableName
   * @param sourceInputId
   * @param hasTargetGap
   */
  public ConcreteRule(Rule<TK> abstractRule,
      CoverageSet sourceCoverage,
      RuleFeaturizer<TK, FV> phraseFeaturizer, Scorer<FV> scorer,
      Sequence<TK> sourceSequence, String phraseTableName, int sourceInputId,
      boolean hasTargetGap) {
    // System.err.printf("compute isolation score for: %s\n", abstractOption);
    assert (hasTargetGap);
    this.abstractRule = abstractRule;
    this.sourceCoverage = sourceCoverage;
    this.phraseTableName = phraseTableName;
    this.sourcePosition = sourceCoverage.nextSetBit(0);

    cachedFeatureList = Generics.newLinkedList();
    
    // TM scores:
    double totalScore = 0.0;
    {
      Featurizable<TK, FV> f = new Featurizable<TK, FV>(sourceSequence, this,
          sourceInputId);
      List<FeatureValue<FV>> features = phraseFeaturizer.ruleFeaturize(f);
      for (FeatureValue<FV> feature : features) {
        if ( ! feature.doNotCache) {
          cachedFeatureList.add(feature);
        }
      }
      totalScore += scorer.getIncrementalScore(features);
      // for(FeatureValue<FV> fv : features)
      // System.err.printf("feature(global): %s\n", fv);
    }
    // Get all other feature scores (LM, word penalty):
    if (abstractRule instanceof DTURule) {
      DTURule<TK> dtuOpt = (DTURule<TK>) abstractRule;
      for (int i = 0; i < dtuOpt.dtus.length; ++i) {
        Featurizable<TK, FV> f = new DTUFeaturizable<TK, FV>(sourceSequence,
            this, sourceInputId, i);
        assert (f.translationScores.length == 0);
        assert (f.phraseScoreNames.length == 0);
        List<FeatureValue<FV>> features = phraseFeaturizer
            .ruleFeaturize(f);
        // for(FeatureValue<FV> fv : features)
        // System.err.printf("feature(%s): %s\n", dtuOpt.dtus[i].toString(),
        // fv);
        totalScore += scorer.getIncrementalScore(features);
      }
    }
    this.isolationScore = totalScore;
    // System.err.printf("total isolation score for %s: %f\n", abstractOption,
    // this.isolationScore);
  }

  @Override
  public String toString() {
    StringBuilder sbuf = new StringBuilder();
    sbuf.append("ConcreteRule:\n");
    sbuf.append(String.format("\tAbstractOption: %s\n", abstractRule
        .toString().replaceAll("\n", "\n\t")));
    sbuf.append(String.format("\tSourceCoverage: %s\n", sourceCoverage));
    sbuf.append(String.format("\tPhraseTable: %s\n", phraseTableName));
    return sbuf.toString();
  }

  public int linearDistortion(ConcreteRule<TK,FV> rule) {
    return linearDistortion(rule, linearDistortionType);
  }

  public int linearDistortion(ConcreteRule<TK,FV> rule,
      LinearDistortionType type) {
    final int nextSourceToken;
    if (type != LinearDistortionType.standard)
      assert (Phrasal.withGaps);
    switch (type) {
    case standard:
      nextSourceToken = sourcePosition + abstractRule.source.size();
      break;
    case last_contiguous_segment:
      nextSourceToken = sourceCoverage.length();
      break;
    case first_contiguous_segment:
      nextSourceToken = sourceCoverage.nextClearBit(sourceCoverage
          .nextSetBit(0));
      break;
    case closest_contiguous_segment: {
      // int firstIdx =
      // foreignCoverage.nextClearBit(foreignCoverage.nextSetBit(0));
      // int lastIdx = foreignCoverage.length();
      // TO DO: may want to implement this
      throw new UnsupportedOperationException();
    }
    case min_first_last_contiguous_segment: {
      int firstIdx = sourceCoverage
          .nextClearBit(sourceCoverage.nextSetBit(0));
      int lastIdx = sourceCoverage.length();
      int firstDelta = Math.abs(firstIdx - rule.sourcePosition);
      int lastDelta = Math.abs(lastIdx - rule.sourcePosition);
      return Math.min(firstDelta, lastDelta);
    }
    case average_distance: {
      int firstIdx = sourceCoverage
          .nextClearBit(sourceCoverage.nextSetBit(0));
      int lastIdx = sourceCoverage.length();
      int firstDelta = Math.abs(firstIdx - rule.sourcePosition);
      int lastDelta = Math.abs(lastIdx - rule.sourcePosition);
      // System.err.printf("coverage: %s first=%d last=%d pos=%d delta=(%d,%d) min=%d\n",
      // foreignCoverage, firstIdx, lastIdx, opt.foreignPos, firstDelta,
      // lastDelta, Math.min(firstDelta, lastDelta));
      return (firstDelta + lastDelta) / 2;
    }
    default:
      throw new UnsupportedOperationException();
    }
    return Math.abs(nextSourceToken - rule.sourcePosition);
  }

  public int signedLinearDistortion(ConcreteRule<TK,FV> rule) {
    assert (linearDistortionType == LinearDistortionType.standard);
    int nextSourceToken = sourcePosition + abstractRule.source.size();
    return nextSourceToken - rule.sourcePosition;
  }

  @Override
  public int compareTo(ConcreteRule<TK,FV> o) {
    return (int) Math.signum(o.isolationScore - this.isolationScore);
  }

}
