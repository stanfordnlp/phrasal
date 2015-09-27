package edu.stanford.nlp.mt.decoder.util;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import edu.stanford.nlp.mt.decoder.feat.FeatureExtractor;
import edu.stanford.nlp.mt.decoder.h.SearchHeuristic;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.Rule;
import edu.stanford.nlp.mt.util.CoverageSet;
import edu.stanford.nlp.mt.util.DTUFeaturizable;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;

/**
 * A derivation that maps a source input to a target output.
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
  public final double h;
  public final int insertionPosition;
  public final int untranslatedSourceTokens;
  public final int depth;
  public final int linearDistortion;
  public int length;
  
  // In prefix-constrained decoding, we need to know whether the prefix has been completed for recombination
  public boolean prefixCompleted;
  public int prefixLength;
  
  public double score;
  
  public final InputProperties sourceInputProperties;

  // non-primitives that already exist at the time of
  // hypothesis creation and just receive an additional
  // reference here
  public ConcreteRule<TK,FV> rule;
  public final Sequence<TK> sourceSequence;

  // The partial translation
  public Sequence<TK> targetSequence;
  
  // right now, translations are built up strictly in sequence.
  // however, we don't want to encourage people writing feature
  // functions to be dependent upon this fact.
  public final Derivation<TK, FV> parent;

  // non-primitives created anew for each hypothesis
  public final CoverageSet sourceCoverage;
  public Featurizable<TK, FV> featurizable;

  // Features extracted to score this derivation
  public List<FeatureValue<FV>> features;

  /**
   * 
   */
  public boolean isDone() {
    return untranslatedSourceTokens == 0;
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
   * Constructor for null hypotheses (root of translation lattice).
   * 
   * @param sourceInputId
   * @param sourceSequence
   * @param sourceInputProperties 
   * @param heuristic
   * @param scorer
   * @param ruleList
   * @param outputSpace
   */
  public Derivation(int sourceInputId, Sequence<TK> sourceSequence,
      InputProperties sourceInputProperties, SearchHeuristic<TK, FV> heuristic,
      Scorer<FV> scorer,
      List<List<ConcreteRule<TK,FV>>> ruleList,
      OutputSpace<TK, FV> outputSpace) {
    this.id = nextId.incrementAndGet();
    score = 0;
    h = heuristic.getInitialHeuristic(sourceSequence, sourceInputProperties, ruleList, scorer, sourceInputId);
    insertionPosition = 0;
    length = 0;
    rule = null;
    this.prefixCompleted = outputSpace == null ? true : (outputSpace.getPrefixLength() == 0);
    this.prefixLength = outputSpace == null ? 0 : outputSpace.getPrefixLength();
    this.sourceSequence = sourceSequence;
    this.sourceInputProperties = sourceInputProperties;
    parent = null;
    featurizable = null;
    untranslatedSourceTokens = sourceSequence.size();
    sourceCoverage = new CoverageSet(sourceSequence.size());
    features = null;
    depth = 0;
    linearDistortion = 0;
    targetSequence = Sequences.emptySequence();    
  }

  /**
   * Constructor for standard phrase-based (left-to-right) derivation expansion.
   * 
   * @param sourceInputId
   * @param rule
   * @param insertionPosition
   * @param base
   * @param featurizer
   * @param scorer
   * @param heuristic
   * @param outputSpace
   */
  public Derivation(int sourceInputId,
      ConcreteRule<TK,FV> rule, int insertionPosition,
      Derivation<TK, FV> base, FeatureExtractor<TK, FV> featurizer,
      Scorer<FV> scorer, SearchHeuristic<TK, FV> heuristic,
      OutputSpace<TK, FV> outputSpace) {
    this.id = nextId.incrementAndGet();
    this.insertionPosition = insertionPosition;
    this.rule = rule;
    this.parent = base;
    this.sourceInputProperties = base.sourceInputProperties;
    this.sourceCoverage = base.sourceCoverage.clone();
    this.sourceCoverage.or(rule.sourceCoverage);
    this.prefixCompleted = outputSpace == null ? true : (outputSpace.getPrefixLength() == 0);
    this.prefixLength = outputSpace == null ? 0 : outputSpace.getPrefixLength();
    assert insertionPosition >= base.length : String.format("Invalid insertion position %d %d", insertionPosition, base.length);
    this.length = insertionPosition + rule.abstractRule.target.size();
    sourceSequence = base.sourceSequence;
    targetSequence = base.targetSequence.concat(rule.abstractRule.target);
    untranslatedSourceTokens = this.sourceSequence.size()
    - this.sourceCoverage.cardinality();
    linearDistortion = (base.rule == null ? rule.sourcePosition
        : base.rule.linearDistortion(rule));
    
    featurizable = new Featurizable<>(this, sourceInputId, featurizer.getNumDerivationFeaturizers());
    
    features = featurizer.featurize(featurizable);
    features.addAll(rule.cachedFeatureList);
    score = base.score + scorer.getIncrementalScore(features);
    h = (Double.isInfinite(base.h)) ? base.h : base.h
        + heuristic.getHeuristicDelta(this, rule.sourceCoverage);
    // System.err.printf("h: %f %f %d %s\n", baseHyp.h,
    // heuristic.getHeuristicDelta(this, translationOpt.foreignCoverage),
    // untranslatedTokens, foreignCoverage);
    assert (!Double.isNaN(h));
    depth = base.depth + 1;    
  }

  /**
   * Constructor for DTU.
   * 
   * @param sourceInputId
   * @param rule
   * @param abstractRule
   * @param insertionPosition
   * @param base
   * @param featurizer
   * @param scorer
   * @param heuristic
   * @param targetPhrase
   * @param hasPendingPhrases
   * @param segmentIdx
   * @param outputSpace
   */
  protected Derivation(int sourceInputId,
      ConcreteRule<TK,FV> rule,
      Rule<TK> abstractRule, int insertionPosition,
      Derivation<TK, FV> base, FeatureExtractor<TK, FV> featurizer,
      Scorer<FV> scorer, SearchHeuristic<TK, FV> heuristic,
      Sequence<TK> targetPhrase, boolean hasPendingPhrases, int segmentIdx,
      OutputSpace<TK, FV> outputSpace) {
    this.id = nextId.incrementAndGet();
    this.insertionPosition = insertionPosition;
    this.rule = rule;
    this.parent = base;
    this.sourceInputProperties = base.sourceInputProperties;
    this.sourceCoverage = base.sourceCoverage.clone();
    this.sourceCoverage.or(rule.sourceCoverage);
    this.length = (insertionPosition < base.length) ? base.length
        : insertionPosition + targetPhrase.size();
    sourceSequence = base.sourceSequence;
    targetSequence = base.targetSequence.concat(targetPhrase);
    untranslatedSourceTokens = this.sourceSequence.size()
    - this.sourceCoverage.cardinality();
    linearDistortion = (base.rule == null ? rule.sourcePosition
        : base.rule.linearDistortion(rule));

    featurizable = new DTUFeaturizable<>(this, abstractRule,
        sourceInputId, featurizer.getNumDerivationFeaturizers(), targetPhrase,
        hasPendingPhrases, segmentIdx);

    features = featurizer.featurize(featurizable);
    features.addAll(rule.cachedFeatureList);
    score = base.score + scorer.getIncrementalScore(features);
    depth = base.depth + 1;
    h = (Double.isInfinite(base.h)) ? base.h : base.h
        + heuristic.getHeuristicDelta(this, rule.sourceCoverage);
    assert (!Double.isNaN(h));
    
    prefixCompleted = outputSpace == null ? true : (outputSpace.getPrefixLength() == 0);
    prefixLength = outputSpace == null ? 0 : outputSpace.getPrefixLength();
  }

  /**
   * Extend this derivation with a target insertion rule.
   * 
   * @param rule
   */
  public void targetInsertion(Sequence<TK> targetSpan, FeatureExtractor<TK, FV> featurizer,
      Scorer<FV> scorer, int sourceInputId) {
    // Manufacture a new rule
    Sequence<TK> ruleTarget = rule.abstractRule.target.concat(targetSpan);
    int[][] e2f = new int[ruleTarget.size()][];
    for (int i = 0; i < rule.abstractRule.target.size(); ++i) {
      e2f[i] = rule.abstractRule.alignment.t2s(i);
    }
    PhraseAlignment align = new PhraseAlignment(e2f);
    ConcreteRule<TK,FV> newRule = SyntheticRules.makeSyntheticRule(rule, ruleTarget, align, scorer, featurizer,
        sourceSequence, sourceInputProperties, sourceInputId);
    
    // Update the derivation...Same sequence as the constructor above.
    this.rule = newRule;
    this.length = length + targetSpan.size();
    targetSequence = targetSequence.concat(targetSpan);
    featurizable = new Featurizable<>(this, sourceInputId, featurizer.getNumDerivationFeaturizers());
    features = featurizer.featurize(featurizable);
    features.addAll(rule.cachedFeatureList);
    double baseScore = parent == null ? 0.0 : parent.score;
    score = baseScore + scorer.getIncrementalScore(features);    
  }
  
  @Override
  public String toString() {
    return String.format("%s %s [%.3f h: %.3f]", targetSequence.toString(), 
        sourceCoverage.toString(), score + h, h);
  }

  @Override
  public int compareTo(Derivation<TK, FV> competitor) {
    final int cmp = (int) Math.signum(competitor.finalScoreEstimate() - finalScoreEstimate());
    return cmp == 0 ? (int) (id - competitor.id) : cmp;
  }

  @Override
  public State<Derivation<TK, FV>> parent() {
    return parent;
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

  public String historyString() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    Derivation<TK,FV> d = this;
    while (d != null) {
      if (sb.length() > 0) sb.append(nl);
      sb.append(d.rule == null ? "<GOAL>" : d.rule.toString());
      d = d.parent;
    }
    return sb.toString();
  }
  
  public boolean hasExpired() {
    return false;
  }

  public boolean hasUntranslatedTokens() {
    return untranslatedSourceTokens > 0;
  }

  public void debug() { /* nothing relevant to debug; meant to be overridden */
  }

  public boolean hasPendingPhrases() {
    return false;
  }
}
