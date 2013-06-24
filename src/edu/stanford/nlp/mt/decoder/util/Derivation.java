package edu.stanford.nlp.mt.decoder.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.DTUFeaturizable;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.RawSequence;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.Rule;
import edu.stanford.nlp.mt.decoder.annotators.Annotator;
import edu.stanford.nlp.mt.decoder.annotators.TargetDependencyAnnotator;
import edu.stanford.nlp.mt.decoder.feat.CombinedFeaturizer;
import edu.stanford.nlp.mt.decoder.h.SearchHeuristic;

/**
 * 
 * Note: this class has a natural ordering that is inconsistent with equals
 * 
 * @author danielcer
 * 
 * @param <TK>
 */
public class Derivation<TK, FV> implements Comparable<Derivation<TK, FV>>,
State<Derivation<TK, FV>> {

  public static AtomicLong nextId = new AtomicLong();

  // primitives
  public final long id;
  public final double score;
  public final double h;
  public final int insertionPosition;
  public final int untranslatedTokens;
  public final int depth;
  public final int linearDistortion;
  public final int length;

  // non-primitives that already exist at the time of
  // hypothesis creation and just receive an additional
  // reference here
  public final ConcreteRule<TK,FV> rule;
  public final Sequence<TK> sourceSequence;

  // right now, translations are built up strictly in sequence.
  // however, we don't want to encourage people writing feature
  // functions to be dependent upon this fact.
  public final Derivation<TK, FV> preceedingDerivation;

  // non-primitives created anew for each hypothesis
  public final CoverageSet sourceCoverage;
  public final Featurizable<TK, FV> featurizable;

  public final List<FeatureValue<FV>> localFeatures;
  public final List<Annotator<TK,FV>> annotators;

  public IString[] posTags;

  /**
   * 
   */
  public boolean isDone() {
    return untranslatedTokens == 0;
  }

  /**
   * 
   */
  public double finalScoreEstimate() {
    return score + h;
  }

  /**
   * 
   */
  @Override
  public double score() {
    return score + h;
  }

  /**
   * 
   */
  public Derivation(int sourceInputId, Sequence<TK> sourceSequence,
      SearchHeuristic<TK, FV> heuristic,
      Scorer<FV> scorer,
      List<Annotator<TK,FV>> annotators,
      List<List<ConcreteRule<TK,FV>>> ruleList) {
    this.id = nextId.incrementAndGet();
    score = 0;
    h = heuristic.getInitialHeuristic(sourceSequence, ruleList, scorer, sourceInputId);
    insertionPosition = 0;
    length = 0;
    rule = null;
    this.sourceSequence = sourceSequence;
    preceedingDerivation = null;
    featurizable = null;
    untranslatedTokens = sourceSequence.size();
    sourceCoverage = new CoverageSet(sourceSequence.size());
    localFeatures = null;
    depth = 0;
    linearDistortion = 0;
    this.annotators = new ArrayList<Annotator<TK,FV>>(annotators.size());
    for (Annotator<TK,FV> annotator : annotators) {
      this.annotators.add(annotator.initialize(sourceSequence));
    }
  }

  /**
   * 
   */
  public Derivation(int sourceInputId,
      ConcreteRule<TK,FV> rule, int insertionPosition,
      Derivation<TK, FV> base, CombinedFeaturizer<TK, FV> featurizer,
      Scorer<FV> scorer, SearchHeuristic<TK, FV> heuristic) {
    this.id = nextId.incrementAndGet();
    this.insertionPosition = insertionPosition;
    this.rule = rule;
    this.preceedingDerivation = base;
    this.sourceCoverage = base.sourceCoverage.clone();
    this.sourceCoverage.or(rule.sourceCoverage);
    this.length = (insertionPosition < base.length ? base.length : // internal
      // insertion
      insertionPosition + rule.abstractRule.target.size()); // edge
    // insertion
    sourceSequence = base.sourceSequence;
    untranslatedTokens = this.sourceSequence.size()
    - this.sourceCoverage.cardinality();
    linearDistortion = (base.rule == null ? rule.sourcePosition
        : base.rule.linearDistortion(rule));
    featurizable = new Featurizable<TK, FV>(this, sourceInputId, featurizer
        .getNumberStatefulFeaturizers());

    annotators = new ArrayList<Annotator<TK,FV>>(base.annotators.size());
    for (Annotator<TK,FV> annotator : base.annotators) {
      /*if (baseHyp.featurizable != null) {
    	   System.out.println("Extending: "+baseHyp.featurizable.partialTranslation);
    	} else {
    		System.out.println("Extend null hypothesis");
    	}
    	System.out.println("with: "+translationOpt.abstractOption.translation)	; */
      Annotator<TK,FV> extendedAnnotator = annotator.extend(rule);
      annotators.add(extendedAnnotator);
      if(untranslatedTokens==0 && annotator.getClass().getName().endsWith("TargetDependencyAnnotator")) {
        ((TargetDependencyAnnotator<TK,FV>) extendedAnnotator).addRoot();
      }
      // System.out.println("done with extension "+translationOpt.abstractOption.translation);
    }

    localFeatures = featurizer.listFeaturize(featurizable);
    localFeatures.addAll(rule.cachedFeatureList);
    score = base.score + scorer.getIncrementalScore(localFeatures);
    h = (Double.isInfinite(base.h)) ? base.h : base.h
        + heuristic.getHeuristicDelta(this, rule.sourceCoverage);
    // System.err.printf("h: %f %f %d %s\n", baseHyp.h,
    // heuristic.getHeuristicDelta(this, translationOpt.foreignCoverage),
    // untranslatedTokens, foreignCoverage);
    assert (!Double.isNaN(h));
    depth = base.depth + 1;
  }


  protected Derivation(int sourceInputId,
      ConcreteRule<TK,FV> rule,
      Rule<TK> abstractRule, int insertionPosition,
      Derivation<TK, FV> base, CombinedFeaturizer<TK, FV> featurizer,
      Scorer<FV> scorer, SearchHeuristic<TK, FV> heuristic,
      RawSequence<TK> targetPhrase, boolean hasPendingPhrases, int segmentIdx) {
    this.id = nextId.incrementAndGet();
    this.insertionPosition = insertionPosition;
    this.rule = rule;
    this.preceedingDerivation = base;
    this.sourceCoverage = base.sourceCoverage.clone();
    this.sourceCoverage.or(rule.sourceCoverage);
    this.length = (insertionPosition < base.length) ? base.length
        : insertionPosition + targetPhrase.size();
    sourceSequence = base.sourceSequence;
    untranslatedTokens = this.sourceSequence.size()
    - this.sourceCoverage.cardinality();
    linearDistortion = (base.rule == null ? rule.sourcePosition
        : base.rule.linearDistortion(rule));
    featurizable = new DTUFeaturizable<TK, FV>(this, abstractRule,
        sourceInputId, featurizer.getNumberStatefulFeaturizers(), targetPhrase,
        hasPendingPhrases, segmentIdx);

    annotators = new ArrayList<Annotator<TK,FV>>(base.annotators.size());
    for (Annotator<TK,FV> annotator : base.annotators) {
      /*if (baseHyp.featurizable != null) {
      	   System.out.println("Extending: "+baseHyp.featurizable.partialTranslation);
      	} else {
      		System.out.println("Extend null hypothesis");
      	}
      	System.out.println("with: "+translationOpt.abstractOption.translation)	; */
      annotators.add(annotator.extend(rule));
      // System.out.println("done with extension "+translationOpt.abstractOption.translation);
    }


    localFeatures = featurizer.listFeaturize(featurizable);
    localFeatures.addAll(rule.cachedFeatureList);
    score = base.score + scorer.getIncrementalScore(localFeatures);
    depth = base.depth + 1;
    h = (Double.isInfinite(base.h)) ? base.h : base.h
        + heuristic.getHeuristicDelta(this, rule.sourceCoverage);
    assert (!Double.isNaN(h));
  }


  /**
   * 
   */
  private void injectSegmentationBuffer(StringBuffer sbuf,
      Derivation<TK, FV> derivation) {
    if (derivation.preceedingDerivation != null)
      injectSegmentationBuffer(sbuf, derivation.preceedingDerivation);
    sbuf.append("\t").append(derivation.rule.abstractRule.target)
    .append(" ");
    sbuf.append(derivation.rule.sourceCoverage).append(" ");
    sbuf.append(Arrays.toString(derivation.rule.abstractRule.scores));
    sbuf.append("\n");
  }


  /**
   * 
   */
  public String toString(int verbosity) {
    StringBuffer sbuf = new StringBuffer();
    if (featurizable != null) {
      sbuf.append(featurizable.targetPrefix);
    } else {
      sbuf.append("<NONE>");
    }
    sbuf.append("  ").append(sourceCoverage);
    sbuf.append(String.format(" [%.3f h: %.3f]", score + h, h));
    if (verbosity > 0) {
      sbuf.append("\nSegmentation:\n");
      injectSegmentationBuffer(sbuf, this);
    }
    return sbuf.toString();
  }

  @Override
  public String toString() {
    return toString(0);
  }

  @Override
  public int compareTo(Derivation<TK, FV> competitor) {
    int cmp = (int) Math.signum(competitor.finalScoreEstimate()
        - finalScoreEstimate());
    if (cmp != 0) {
      return cmp;
    }
    return (int) (id - competitor.id);
  }

  @Override
  public State<Derivation<TK, FV>> parent() {
    return preceedingDerivation;
  }

  @Override
  public double partialScore() {
    return score;
  }

  @Override
  public int depth() {
    return depth;
  }

  @Override
  public int hashCode() {
    return (int) id;
  }

  public boolean hasExpired() {
    return false;
  }

  public boolean hasUntranslatedTokens() {
    return untranslatedTokens > 0;
  }

  public void debug() { /* nothing relevant to debug; meant to be overridden */
  }

  public boolean hasPendingPhrases() {
    return false;
  }
}
