package edu.stanford.nlp.mt.tm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.DTUFeaturizable;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.Phrasal;


/**
 * A translation rule that is associated with a particular source span
 * in an input sentence.
 * 
 * @author danielcer
 * 
 * @param <TK>
 */
public class ConcreteRule<TK,FV> implements Comparable<ConcreteRule<TK,FV>> {

  /**
   * The underlying translation rule.
   */
  public final Rule<TK> abstractRule;
  
  /**
   * The source coverage of this rule.
   */
  public final CoverageSet sourceCoverage;
    
  /**
   * The left edge in the source sequence of the source side
   * of this rule.
   */
  public final int sourcePosition;
  
  /**
   * The isolation score of this rule.
   */
  public final double isolationScore;
  
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

  /**
   * Constructor.
   * 
   * @param abstractRule
   * @param sourceCoverage
   * @param phraseFeaturizer
   * @param scorer
   * @param sourceSequence
   * @param sourceInputId
   */
  public ConcreteRule(Rule<TK> abstractRule, CoverageSet sourceCoverage,
      RuleFeaturizer<TK, FV> phraseFeaturizer, Scorer<FV> scorer,
      Sequence<TK> sourceSequence, int sourceInputId, InputProperties sourceInputProperties) {
    this.abstractRule = abstractRule;
    this.sourceCoverage = sourceCoverage;
    this.sourcePosition = sourceCoverage.nextSetBit(0);
    
    // Extract rule features
    Featurizable<TK, FV> f = new Featurizable<>(sourceSequence, sourceInputProperties, this,
        sourceInputId);
    List<FeatureValue<FV>> features = phraseFeaturizer == null ? 
        Collections.emptyList() : phraseFeaturizer.ruleFeaturize(f);
    
    // Cache selected features
    cachedFeatureList = new ArrayList<>(features.size());
    for (FeatureValue<FV> feature : features) {
      if ( ! feature.doNotCache) {
        cachedFeatureList.add(feature);
      }
    }
    this.isolationScore = scorer == null ? -199.0 : scorer.getIncrementalScore(features);
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
   * @param sourceInputId
   * @param hasTargetGap
   */
  public ConcreteRule(Rule<TK> abstractRule,
      CoverageSet sourceCoverage,
      RuleFeaturizer<TK, FV> phraseFeaturizer, Scorer<FV> scorer,
      Sequence<TK> sourceSequence, int sourceInputId, boolean hasTargetGap,
      InputProperties sourceInputProperties) {
    // System.err.printf("compute isolation score for: %s\n", abstractOption);
    assert (hasTargetGap);
    this.abstractRule = abstractRule;
    this.sourceCoverage = sourceCoverage;
    this.sourcePosition = sourceCoverage.nextSetBit(0);

    cachedFeatureList = new ArrayList<>();
    
    // TM scores:
    double totalScore = 0.0;
    {
      Featurizable<TK, FV> f = new Featurizable<TK, FV>(sourceSequence, sourceInputProperties, this,
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
        Featurizable<TK, FV> f = new DTUFeaturizable<TK, FV>(sourceSequence, sourceInputProperties,
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
    return String.format("%s ==> %s (%s) %f", abstractRule.source,
        abstractRule.target, sourceCoverage, isolationScore);
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
  
  @Override
  public int hashCode() {
    return abstractRule.hashCode() ^ (sourceCoverage.hashCode()*0xc2b2ae35);
  }
  
  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    else if ( ! (o instanceof ConcreteRule)) return false;
    else {
      ConcreteRule<TK,FV> other = (ConcreteRule<TK,FV>) o;
      return abstractRule.equals(other.abstractRule) && sourceCoverage.equals(other.sourceCoverage);
    }
  }
}
