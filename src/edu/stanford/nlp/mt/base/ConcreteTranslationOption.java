package edu.stanford.nlp.mt.base;

import java.util.*;

import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.Phrasal;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 */
public class ConcreteTranslationOption<TK,FV> implements
    Comparable<ConcreteTranslationOption<TK,FV>> {

  public final TranslationOption<TK> abstractOption;
  public final CoverageSet foreignCoverage;
  public final String phraseTableName;
  public final int foreignPos;
  public final double isolationScore;
//  public final List<FeatureValue<FV>> cachedFeatureList;

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
   * 
   * @param <FV>
   */
  public ConcreteTranslationOption(TranslationOption<TK> abstractOption,
      CoverageSet foreignCoverage,
      IsolatedPhraseFeaturizer<TK, FV> phraseFeaturizer, Scorer<FV> scorer,
      Sequence<TK> foreignSequence, String phraseTableName, int translationId) {
    this.abstractOption = abstractOption;
    this.foreignCoverage = foreignCoverage;
    this.phraseTableName = phraseTableName;
    this.foreignPos = foreignCoverage.nextSetBit(0);
    Featurizable<TK, FV> f = new Featurizable<TK, FV>(foreignSequence, this,
        translationId);
    List<FeatureValue<FV>> features = phraseFeaturizer.phraseListFeaturize(f);
//    cachedFeatureList = new LinkedList<FeatureValue<FV>>();
//    for (FeatureValue<FV> feature : features) {
//      if (FeatureValues.isCacheable(feature))
//        cachedFeatureList.add(feature);
//    }
    this.isolationScore = scorer.getIncrementalScore(features);
  }

  /**
   * 
   * @param <FV>
   */
  public ConcreteTranslationOption(TranslationOption<TK> abstractOption,
      CoverageSet foreignCoverage,
      IsolatedPhraseFeaturizer<TK, FV> phraseFeaturizer, Scorer<FV> scorer,
      Sequence<TK> foreignSequence, String phraseTableName, int translationId,
      boolean hasTargetGap) {
    // System.err.printf("compute isolation score for: %s\n", abstractOption);
    assert (hasTargetGap);
    this.abstractOption = abstractOption;
    this.foreignCoverage = foreignCoverage;
    this.phraseTableName = phraseTableName;
    this.foreignPos = foreignCoverage.nextSetBit(0);

    // TM scores:
    double totalScore = 0.0;
    {
      Featurizable<TK, FV> f = new Featurizable<TK, FV>(foreignSequence, this,
          translationId);
      List<FeatureValue<FV>> features = phraseFeaturizer.phraseListFeaturize(f);
      totalScore += scorer.getIncrementalScore(features);
      // for(FeatureValue<FV> fv : features)
      // System.err.printf("feature(global): %s\n", fv);
    }
    // Get all other feature scores (LM, word penalty):
    if (abstractOption instanceof DTUOption) {
      DTUOption<TK> dtuOpt = (DTUOption<TK>) abstractOption;
      for (int i = 0; i < dtuOpt.dtus.length; ++i) {
        Featurizable<TK, FV> f = new DTUFeaturizable<TK, FV>(foreignSequence,
            this, translationId, i);
        assert (f.translationScores.length == 0);
        assert (f.phraseScoreNames.length == 0);
        List<FeatureValue<FV>> features = phraseFeaturizer
            .phraseListFeaturize(f);
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
    sbuf.append("ConcreteTranslationOption:\n");
    sbuf.append(String.format("\tAbstractOption: %s\n", abstractOption
        .toString().replaceAll("\n", "\n\t")));
    sbuf.append(String.format("\tForeignCoverage: %s\n", foreignCoverage));
    sbuf.append(String.format("\tPhraseTable: %s\n", phraseTableName));
    return sbuf.toString();
  }

  public int linearDistortion(ConcreteTranslationOption<TK,FV> opt) {
    return linearDistortion(opt, linearDistortionType);
  }

  public int linearDistortion(ConcreteTranslationOption<TK,FV> opt,
      LinearDistortionType type) {
    final int nextForeignToken;
    if (type != LinearDistortionType.standard)
      assert (Phrasal.withGaps);
    switch (type) {
    case standard:
      nextForeignToken = foreignPos + abstractOption.foreign.size();
      break;
    case last_contiguous_segment:
      nextForeignToken = foreignCoverage.length();
      break;
    case first_contiguous_segment:
      nextForeignToken = foreignCoverage.nextClearBit(foreignCoverage
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
      int firstIdx = foreignCoverage
          .nextClearBit(foreignCoverage.nextSetBit(0));
      int lastIdx = foreignCoverage.length();
      int firstDelta = Math.abs(firstIdx - opt.foreignPos);
      int lastDelta = Math.abs(lastIdx - opt.foreignPos);
      return Math.min(firstDelta, lastDelta);
    }
    case average_distance: {
      int firstIdx = foreignCoverage
          .nextClearBit(foreignCoverage.nextSetBit(0));
      int lastIdx = foreignCoverage.length();
      int firstDelta = Math.abs(firstIdx - opt.foreignPos);
      int lastDelta = Math.abs(lastIdx - opt.foreignPos);
      // System.err.printf("coverage: %s first=%d last=%d pos=%d delta=(%d,%d) min=%d\n",
      // foreignCoverage, firstIdx, lastIdx, opt.foreignPos, firstDelta,
      // lastDelta, Math.min(firstDelta, lastDelta));
      return (firstDelta + lastDelta) / 2;
    }
    default:
      throw new UnsupportedOperationException();
    }
    return Math.abs(nextForeignToken - opt.foreignPos);
  }

  public int signedLinearDistortion(ConcreteTranslationOption<TK,FV> opt) {
    assert (linearDistortionType == LinearDistortionType.standard);
    int nextForeignToken = foreignPos + abstractOption.foreign.size();
    return nextForeignToken - opt.foreignPos;
  }

  @Override
  public int compareTo(ConcreteTranslationOption<TK,FV> o) {
    return (int) Math.signum(o.isolationScore - this.isolationScore);
  }

}
