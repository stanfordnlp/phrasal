package edu.stanford.nlp.mt.base;

import edu.stanford.nlp.mt.decoder.util.Hypothesis;
import edu.stanford.nlp.mt.decoder.feat.StatefulFeaturizer;
import edu.stanford.nlp.mt.Phrasal;

/**
 * Packages information about a newly constructed hypothesis for use by the
 * incremental featurizers.
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
   * Most recently translated foreign phrase (source side)
   */
  public final Sequence<TK> sourcePhrase;

  /**
   * Translated Phrase produced for the most recently translated foreign phrase (target side)
   */
  public final Sequence<TK> targetPhrase;

  /**
   * Phrase table used to produce the most recently translated phrase.
   */
  public final String phraseTableName;

  /**
   * Translation Scores as provided by the phrase table
   */
  public final float[] translationScores;

  /**
   * Names associated with the Translation Scores provided by the phrase table
   */
  public final String[] phraseScoreNames;

  public final ConcreteTranslationOption<TK,FV> option;

  public final int targetPosition;

  /**
   * First position in the source sequence covered by the most recently phrase
   */
  public final int sourcePosition;

  /**
   * Number of tokens in the source sequence that are still untranslated
   */
  public final int untranslatedTokens;

  /**
   * Partial Target sequence associated with the current hypothesis
   */
  public final Sequence<TK> targetPrefix;

  /**
   * Source sequence
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
   * Can walk back through the lattice of hypotheses with <code>hyp</code>
   * <br>
   * You can do the same thing with <code>prior</code>
   */
  public final Hypothesis<TK, FV> hyp;

  /**
   * While, internally, I want to be able to take advantage of the fact that
   * partial translations are represented using RawSequence, I don't want to
   * make that part of the API for people involved with writing features.
   */
  protected final RawSequence<TK> targetPrefixRaw;

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
   * For a given sequence position in t2fAlignmentIndex or f2tAlignment, the
   * index PHRASE_START is used to retrieve the start position of the aligned
   * phrase.
   */
  public static final int PHRASE_START = 0;

  /**
   * For a given sequence position in t2fAlignmentIndex or f2tAlignment, the
   * index PHRASE_END is used to retrieve the end position of the aligned
   * phrase.
   */
  public static final int PHRASE_END = 1;

  /**
   * Partial translation to foreign sentence alignment index. By default, it is
   * set to null. It is constructed only if any featurizer implements
   * AlignmentFeaturizer.
   * 
   * Guarantees that ranges corresponding to the same phrase are represented
   * with the same int[] in order to allow '==' to be used over the ranges
   * within a given index.
   * 
   * For positions where no alignment exists, null is used.
   */
  final public int[][] t2sAlignmentIndex;

  /**
   * Foreign sentence to partial translation alignment index. By default, it is
   * set to null. It is constructed only if any featurizer implements
   * AlignmentFeaturizer.
   * 
   * Same guarantees as t2sAlignmentIndex
   */
  final public int[][] s2tAlignmentIndex;

  /**
   * For stateful featurizers. If multiple featurizers require access to this
   * variable, 'state' should probably reference a map or a list.
   */
  final private Object[] states;

  /**
	 * 
	 */
  @SuppressWarnings("unchecked")
  public Featurizable(Hypothesis<TK, FV> hypothesis, int sourceInputId,
      int nbStatefulFeaturizers) {
    this.sourceInputId = sourceInputId;
    done = hypothesis.isDone();
    option = hypothesis.translationOpt;
    TranslationOption<TK> transOpt = hypothesis.translationOpt.abstractOption;
    ConcreteTranslationOption<TK,FV> concreteOpt = hypothesis.translationOpt;
    sourcePhrase = transOpt.source;
    targetPhrase = transOpt.target;
    phraseTableName = concreteOpt.phraseTableName;
    translationScores = transOpt.scores;
    phraseScoreNames = transOpt.phraseScoreNames;
    targetPosition = hypothesis.insertionPosition;
    sourcePosition = concreteOpt.sourcePosition;
    linearDistortion = hypothesis.linearDistortion;

    Object[] tokens = retrieveTokens(hypothesis.length, hypothesis);
    targetPrefix = targetPrefixRaw = new RawSequence<TK>(
        (TK[]) tokens);
    sourceSentence = hypothesis.sourceSequence;
    untranslatedTokens = hypothesis.untranslatedTokens;
    prior = hypothesis.preceedingHyp.featurizable;
    if (prior != null) {
      if (constructAlignment) {
        t2sAlignmentIndex = copyOfIndex(prior.t2sAlignmentIndex,
            hypothesis.length);
        s2tAlignmentIndex = copyOfIndex(prior.s2tAlignmentIndex,
            prior.s2tAlignmentIndex.length);
      } else {
        t2sAlignmentIndex = s2tAlignmentIndex = null;
      }
      states = (nbStatefulFeaturizers > 0) ? new Object[nbStatefulFeaturizers]
          : null;
    } else {
      if (constructAlignment) {
        t2sAlignmentIndex = new int[hypothesis.length][];
        s2tAlignmentIndex = new int[sourceSentence.size()][];
      } else {
        t2sAlignmentIndex = s2tAlignmentIndex = null;
      }
      states = (nbStatefulFeaturizers > 0) ? new Object[nbStatefulFeaturizers]
          : null;
    }
    hyp = hypothesis;
    if (constructAlignment)
      augmentAlignments(concreteOpt);
  }

  @SuppressWarnings("unchecked")
  protected Featurizable(Hypothesis<TK, FV> hypothesis, int sourceInputId,
      int nbStatefulFeaturizers, Sequence<TK> targetPhrase,
      Object[] tokens, boolean hasPendingPhrases, boolean targetOnly) {
    this.sourceInputId = sourceInputId;
    done = hypothesis.isDone() && !hasPendingPhrases;
    option = hypothesis.translationOpt;
    TranslationOption<TK> transOpt = hypothesis.translationOpt.abstractOption;
    ConcreteTranslationOption<TK,FV> concreteOpt = hypothesis.translationOpt;
    sourcePhrase = transOpt.source;
    this.targetPhrase = targetPhrase;
    phraseTableName = concreteOpt.phraseTableName;
    if (targetOnly) {
      translationScores = nullScores;
      phraseScoreNames = nullNames;
    } else {
      translationScores = transOpt.scores;
      phraseScoreNames = transOpt.phraseScoreNames;
    }
    targetPosition = hypothesis.insertionPosition;
    sourcePosition = concreteOpt.sourcePosition;
    linearDistortion = hypothesis.linearDistortion;

    targetPrefix = targetPrefixRaw = new RawSequence<TK>(
        (TK[]) tokens);
    sourceSentence = hypothesis.sourceSequence;
    untranslatedTokens = hypothesis.untranslatedTokens;
    prior = hypothesis.preceedingHyp.featurizable;
    if (prior != null) {
      if (constructAlignment) {
        t2sAlignmentIndex = copyOfIndex(prior.t2sAlignmentIndex,
            hypothesis.length);
        s2tAlignmentIndex = copyOfIndex(prior.s2tAlignmentIndex,
            prior.s2tAlignmentIndex.length);
      } else {
        t2sAlignmentIndex = s2tAlignmentIndex = null;
      }
      states = (nbStatefulFeaturizers > 0) ? new Object[nbStatefulFeaturizers]
          : null;
    } else {
      if (constructAlignment) {
        t2sAlignmentIndex = new int[hypothesis.length][];
        s2tAlignmentIndex = new int[sourceSentence.size()][];
      } else {
        t2sAlignmentIndex = s2tAlignmentIndex = null;
      }
      states = (nbStatefulFeaturizers > 0) ? new Object[nbStatefulFeaturizers]
          : null;
    }
    hyp = hypothesis;
    if (constructAlignment)
      augmentAlignments(concreteOpt);
  }

  public Object getState(StatefulFeaturizer<TK, FV> f) {
    return states[f.getId()];
  }

  public void setState(StatefulFeaturizer<TK, FV> f, Object s) {
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
   * Avoid Arrays.copyOf and it's sluggish call to Class.getComponentType
   */
  private static int[][] copyOfIndex(int[][] index, int newLength) {
    int[][] newIndex = new int[newLength][];
    System.arraycopy(index, 0, newIndex, 0, Math.min(index.length, newLength));
    return newIndex;
  }

  /**
	 * 
	 */
  public Featurizable(Sequence<TK> sourceSequence,
      ConcreteTranslationOption<TK,FV> concreteOpt, int sourceInputId) {
    this.sourceInputId = sourceInputId;
    option = concreteOpt;
    done = false;
    TranslationOption<TK> transOpt = concreteOpt.abstractOption;
    sourcePhrase = transOpt.source;
    targetPhrase = transOpt.target;
    phraseTableName = concreteOpt.phraseTableName;
    translationScores = transOpt.scores;
    phraseScoreNames = transOpt.phraseScoreNames;
    targetPosition = 0;
    sourcePosition = concreteOpt.sourcePosition;
    targetPrefix = targetPhrase;
    targetPrefixRaw = null;
    sourceSentence = sourceSequence;
    untranslatedTokens = sourceSequence.size() - sourcePhrase.size();
    prior = null;
    states = null;
    linearDistortion = Integer.MAX_VALUE;
    t2sAlignmentIndex = new int[targetPhrase != null ? targetPhrase
        .size() : 0][];
    s2tAlignmentIndex = new int[sourceSentence.size()][];
    if (constructAlignment)
      augmentAlignments(concreteOpt);
    hyp = null;
  }

  protected Featurizable(Sequence<TK> sourceSequence,
      ConcreteTranslationOption<TK,FV> concreteOpt, int sourceInputId,
      Sequence<TK> targetPhrase) {
    assert (concreteOpt.abstractOption.getClass().equals(DTUOption.class));
    this.sourceInputId = sourceInputId;
    option = concreteOpt;
    done = false;
    TranslationOption<TK> transOpt = concreteOpt.abstractOption;
    sourcePhrase = transOpt.source;
    this.targetPhrase = targetPhrase;
    phraseTableName = concreteOpt.phraseTableName;
    translationScores = nullScores;
    // translationScores = transOpt.scores;
    phraseScoreNames = nullNames;
    // phraseScoreNames = transOpt.phraseScoreNames;
    targetPosition = 0;
    sourcePosition = concreteOpt.sourcePosition;
    targetPrefix = targetPhrase;
    targetPrefixRaw = null;
    sourceSentence = sourceSequence;
    untranslatedTokens = sourceSequence.size() - sourcePhrase.size();
    prior = null;
    states = null;
    linearDistortion = Integer.MAX_VALUE;
    t2sAlignmentIndex = new int[targetPhrase.size()][];
    s2tAlignmentIndex = new int[sourceSentence.size()][];
    if (constructAlignment)
      augmentAlignments(concreteOpt);
    hyp = null;
  }

  /**
	 * 
	 */
  protected void augmentAlignments(ConcreteTranslationOption<TK,FV> concreteOpt) {
    if (concreteOpt.abstractOption.target == null)
      return;
    int targetSz = concreteOpt.abstractOption.target.elements.length;
    int sourceSz = Phrasal.withGaps ?
    // MG2009: these two lines should achieve the same result for phrases
    // without gaps,
    // though the first one is slower:
    concreteOpt.sourceCoverage.length()
        - concreteOpt.sourceCoverage.nextSetBit(0)
        : concreteOpt.abstractOption.source.elements.length;
    int limit;
    int[] range = new int[2];
    range[PHRASE_START] = sourcePosition;
    range[PHRASE_END] = sourcePosition + sourceSz;
    limit = targetPosition + targetSz;
    for (int i = targetPosition; i < limit; i++) {
      t2sAlignmentIndex[i] = range;
    }

    range = new int[2];
    range[PHRASE_START] = targetPosition;
    range[PHRASE_END] = targetPosition + targetSz;
    limit = sourcePosition + sourceSz;
    for (int i = sourcePosition; i < limit; i++) {
      if (concreteOpt.sourceCoverage.get(i))
        s2tAlignmentIndex[i] = range;
    }
  }

  protected static <TK, FV> Object[] retrieveTokens(int sz, Hypothesis<TK, FV> h) {
    Object[] tokens = new Object[sz];
    int pos = 0;
    Featurizable<TK, FV> preceedingF = h.preceedingHyp.featurizable;
    if (preceedingF != null) {
      Object[] preceedingTokens = preceedingF.targetPrefixRaw.elements;
      System.arraycopy(preceedingTokens, 0, tokens, 0,
          pos = preceedingTokens.length);
    }

    ConcreteTranslationOption<TK,FV> concreteOpt = h.translationOpt;
    Object[] newTokens = concreteOpt.abstractOption.target.elements;
    System.arraycopy(newTokens, 0, tokens, pos, newTokens.length);
    return tokens;
  }

  private static final float[] nullScores = new float[0];
  private static final String[] nullNames = new String[0];

  private static boolean constructAlignment = false;

  public static void enableAlignments() {
    constructAlignment = true;
  }

}
