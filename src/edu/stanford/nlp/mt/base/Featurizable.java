package edu.stanford.nlp.mt.base;

import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeaturizerState;
import edu.stanford.nlp.mt.pt.ConcreteRule;
import edu.stanford.nlp.mt.pt.DTURule;
import edu.stanford.nlp.mt.pt.Rule;

/**
 * Packages information about a newly constructed hypothesis for use in the 
 * feature API.
 * 
 * @author danielcer
 * 
 * @param <TK>
 */
public class Featurizable<TK, FV> {

  /**
   * Unique id associated with the source input sentence. Usually this is
   * the zero-indexed line number of the newline-delimited input file.
   */
  public final int sourceInputId;

  /**
   * Annotations specific to this input. See <code>InputProperty</code>.
   */
  public final InputProperties sourceInputProperties;
  
  /**
   * Most recently translated source phrase (source side of rule)
   */
  public final Sequence<TK> sourcePhrase;

  /**
   * Most recently translated target phrase (target side of rule)
   */
  public final Sequence<TK> targetPhrase;

  /**
   * Phrase table used to produce the most recently translated phrase.
   */
  public final String phraseTableName;

  /**
   * Rule scores as provided by the phrase table
   */
  public final float[] translationScores;

  /**
   * Feature names associated with <code>translationScores</code>
   */
  public final String[] phraseScoreNames;

  /**
   * The translation rule that is being applied.
   */
  public final ConcreteRule<TK,FV> rule;

  /**
   * Insertion point in targetPrefix.
   */
  public final int targetPosition;

  /**
   * First position in the source sequence covered by the source side of the rule
   */
  public final int sourcePosition;

  /**
   * Number of tokens in the source sequence that are still untranslated
   */
  public final int numUntranslatedSourceTokens;

  /**
   * Partial Target sequence associated with the current hypothesis
   */
  public final Sequence<TK> targetPrefix;

  /**
   * Full source input sentence.
   */
  public final Sequence<TK> sourceSentence;

  /**
   * Degree of linear distortion associated with the most recently translated
   * phrase
   */
  public final int linearDistortion;

  /**
   * Indicates whether or not the current hypothesis provides a finished
   * translation of the source sentence
   */
  public final boolean done;

  /**
   * Can walk back through the lattice of hypotheses with <code>derivation</code>
   * <br>
   * You can do the same thing with <code>prior</code>
   */
  public final Derivation<TK, FV> derivation;

  /**
   * Featurizable associated with the partial hypothesis that was used to
   * generate the current hypothesis.
   * 
   * It is recommended that Featurizers that need to make use of internal state
   * information as a hypothesis is being constructed maintain an internal Map
   * from previously seen Featurizable<TK> objects to appropriate state objects.
   */
  public final Featurizable<TK, FV> prior;

  /**
   * For stateful featurizers. If multiple featurizers require access to this
   * variable, 'state' should probably reference a map or a list.
   */
  final private FeaturizerState[] states;

  /**
   * Constructor.
   * 
   * @param derivation
   * @param sourceInputId
   * @param nbStatefulFeaturizers
   */
  public Featurizable(Derivation<TK, FV> derivation, int sourceInputId,
      int nbStatefulFeaturizers) {
    this.sourceInputId = sourceInputId;
    done = derivation.isDone();
    this.rule = derivation.rule;
    Rule<TK> abstractRule = derivation.rule.abstractRule;
    sourcePhrase = abstractRule.source;
    targetPhrase = abstractRule.target;
    phraseTableName = rule.phraseTableName;
    translationScores = abstractRule.scores;
    phraseScoreNames = abstractRule.phraseScoreNames;
    targetPosition = derivation.insertionPosition;
    sourcePosition = rule.sourcePosition;
    linearDistortion = derivation.linearDistortion;
    targetPrefix = derivation.targetSequence;
    sourceSentence = derivation.sourceSequence;
    sourceInputProperties = derivation.sourceInputProperties;
    numUntranslatedSourceTokens = derivation.untranslatedTokens;
    prior = derivation.preceedingDerivation.featurizable;
    states = (nbStatefulFeaturizers > 0) ? new FeaturizerState[nbStatefulFeaturizers]
        : null;
    this.derivation = derivation;
  }

  /**
   * DTU constructor.
   * 
   * @param derivation
   * @param sourceInputId
   * @param nbStatefulFeaturizers
   * @param targetPhrase
   * @param tokens
   * @param hasPendingPhrases
   * @param targetOnly
   */
  protected Featurizable(Derivation<TK, FV> derivation, int sourceInputId,
      int nbStatefulFeaturizers, Sequence<TK> targetPhrase,
      boolean hasPendingPhrases, boolean targetOnly) {
    this.sourceInputId = sourceInputId;
    done = derivation.isDone() && !hasPendingPhrases;
    this.rule = derivation.rule;
    Rule<TK> abstractRule = derivation.rule.abstractRule;
    sourcePhrase = abstractRule.source;
    this.targetPhrase = targetPhrase;
    phraseTableName = rule.phraseTableName;
    if (targetOnly) {
      translationScores = nullScores;
      phraseScoreNames = nullNames;
    } else {
      translationScores = abstractRule.scores;
      phraseScoreNames = abstractRule.phraseScoreNames;
    }
    targetPosition = derivation.insertionPosition;
    sourcePosition = rule.sourcePosition;
    linearDistortion = derivation.linearDistortion;

    targetPrefix = derivation.targetSequence;
    sourceSentence = derivation.sourceSequence;
    sourceInputProperties = derivation.sourceInputProperties;
    numUntranslatedSourceTokens = derivation.untranslatedTokens;
    prior = derivation.preceedingDerivation.featurizable;
    states = (nbStatefulFeaturizers > 0) ? new FeaturizerState[nbStatefulFeaturizers]
        : null;
    this.derivation = derivation;
  }

  public FeaturizerState getState(DerivationFeaturizer<TK, FV> f) {
    return states[f.getId()];
  }

  public void setState(DerivationFeaturizer<TK, FV> f, FeaturizerState s) {
    states[f.getId()] = s;
  }

  /**
   * Current segment in (dis)continuous phrase. Note: A continuous phrase hsa
   * only one segment.
   */
  public int getSegmentIdx() {
    return 0;
  }

  /**
   * Number of segments in (dis)continuous phrase. Note: A continuous phrase hsa
   * only one segment.
   */
  public int getSegmentNumber() {
    return 1;
  }

  /**
   * Constructor for rule scoring. Not used for derivation building.
   * 
   * @param sourceSequence
   * @param rule
   * @param sourceInputId
   */
  public Featurizable(Sequence<TK> sourceSequence, InputProperties sourceInputProperties,
      ConcreteRule<TK,FV> rule, int sourceInputId) {
    this.sourceInputId = sourceInputId;
    this.rule = rule;
    done = false;
    Rule<TK> abstractRule = rule.abstractRule;
    sourcePhrase = abstractRule.source;
    targetPhrase = abstractRule.target;
    phraseTableName = rule.phraseTableName;
    translationScores = abstractRule.scores;
    phraseScoreNames = abstractRule.phraseScoreNames;
    targetPosition = 0;
    sourcePosition = rule.sourcePosition;
    targetPrefix = targetPhrase;
    sourceSentence = sourceSequence;
    this.sourceInputProperties = sourceInputProperties;
    numUntranslatedSourceTokens = sourceSequence.size() - sourcePhrase.size();
    prior = null;
    states = null;
    linearDistortion = Integer.MAX_VALUE;
    derivation = null;
  }

  /**
   * DTU constructor.
   * 
   * @param sourceSequence
   * @param rule
   * @param sourceInputId
   * @param targetPhrase
   */
  protected Featurizable(Sequence<TK> sourceSequence, InputProperties sourceInputProperties,
      ConcreteRule<TK,FV> rule, int sourceInputId,
      Sequence<TK> targetPhrase) {
    assert (rule.abstractRule.getClass().equals(DTURule.class));
    this.sourceInputId = sourceInputId;
    this.rule = rule;
    done = false;
    Rule<TK> abstractRule = rule.abstractRule;
    sourcePhrase = abstractRule.source;
    this.targetPhrase = targetPhrase;
    phraseTableName = rule.phraseTableName;
    translationScores = nullScores;
    // translationScores = transOpt.scores;
    phraseScoreNames = nullNames;
    // phraseScoreNames = transOpt.phraseScoreNames;
    targetPosition = 0;
    sourcePosition = rule.sourcePosition;
    targetPrefix = targetPhrase;
    sourceSentence = sourceSequence;
    this.sourceInputProperties = sourceInputProperties;
    numUntranslatedSourceTokens = sourceSequence.size() - sourcePhrase.size();
    prior = null;
    states = null;
    linearDistortion = Integer.MAX_VALUE;
    derivation = null;
  }

  private static final float[] nullScores = new float[0];
  private static final String[] nullNames = new String[0];
  
  // Thang Feb14
  @Override
  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append(sourcePhrase + " ||| ");
    sb.append(targetPhrase + " ||| ");
    sb.append(rule.abstractRule.alignment);
    return sb.toString();
  }
}
