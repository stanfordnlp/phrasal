package mt.base;

import mt.decoder.util.Hypothesis;
import mt.decoder.feat.StatefulFeaturizer;


/**
 * Packages information about a newly constructed hypothesis
 * for use by the incremental featurizers.
 * 
 * @author danielcer
 *
 * @param <TK>
 */
public class Featurizable<TK,FV> {
	
	/**
	 * Unique id associated with the current hypothesis
	 */
	public final int translationId;
	
	/**
	 * Most recently translated foreign phrase
	 */
	public final Sequence<TK> foreignPhrase;
	
	/**
	 * Translated Phrase produced for the most recently translated foreign phrase 
	 */
	public final Sequence<TK> translatedPhrase;
	
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
	
	public final ConcreteTranslationOption<TK> option;
	
	/**
	 * 
	 */
	public final int translationPosition;
	
	/**
	 * First position in the source sequence covered by the most recently phrase 
	 */
	public final int foreignPosition;
	
	/**
	 * Number of tokens in the source sequence that are still untranslated
	 */
	public final int untranslatedTokens;
	
	/**
	 * Partial Target sequence associated with the current hypothesis
	 */
	public final Sequence<TK> partialTranslation; 
	
	/**
	 * Source sequence
	 */
	public final Sequence<TK> foreignSentence;

  /**
   * Coverage of the source sentence
   */
  public final CoverageSet foreignCoverage;

  /**
	 * Degree of linear distortion associated with the most recently translated phrase
	 */
	public final int linearDistortion;
	
	/**
	 * Indicates whether or not the current hypothesis provides a finished translation of the source sentence
	 */
	public final boolean done;
	
	
	public final Hypothesis<TK, FV> hyp;
	
	/**
	 * While, internally, I want to be able to take advantage of the 
	 * fact that partial translations are represented using RawSequence,
	 * I don't want to make that part of the API for people involved with
	 * writing features. 
	 */
	 private final RawSequence<TK> partialTranslationRaw;
	 
	/**
	 * Featurizable associated with the partial hypothesis that was used to 
	 * generate the current hypothesis.
	 * 
	 * It is recommended that Featurizers that need to make use of internal 
	 * state information as a hypothesis is being constructed maintain an 
	 * internal Map from previously seen Featurizable<TK> objects to 
	 * appropriate state objects.
	 */
	public final Featurizable<TK,FV> prior;
	
	/**
	 * For a given sequence position in t2fAlignmentIndex or f2tAlignment, the index PHRASE_START is
	 * used to retrieve the start position of the aligned phrase. 
	 */
	public static final int PHRASE_START = 0;
	
	/** 
	 * For a given sequence position in t2fAlignmentIndex or f2tAlignment, the index PHRASE_END is
	 * used to retrieve the end position of the aligned phrase.
	 */
	public static final int PHRASE_END = 1;
	
	
	/**
	 * Partial translation to foreign sentence alignment index.
	 * 
	 * Guarantees that ranges corresponding to the same phrase
	 * are represented with the same int[] in order to allow
	 * '==' to be used over the ranges within a given index.
	 * 
	 * For positions where no alignment exists, null is used.
	 */
	final public int[][] t2fAlignmentIndex;
	
	/**
	 * Foreign sentence to partial translation alignment index.
	 * 
	 * Same guarantees as t2fAlignmentIndex
	 */
	final public int[][] f2tAlignmentIndex;

  /**
   * For stateful featurizers. If multiple featurizers require
   * access to this variable, 'state' should probably reference
   * a map or a list.
   */
  final private Object[] states;

	/**
	 * 
	 * @param hypothesis
	 */
	@SuppressWarnings("unchecked")
	public Featurizable(Hypothesis<TK,FV> hypothesis, int translationId, int nbStatefulFeaturizers) {
		this.translationId = translationId;
		done = hypothesis.isDone();
		option = hypothesis.translationOpt;
		TranslationOption<TK> transOpt = hypothesis.translationOpt.abstractOption;
		ConcreteTranslationOption<TK> concreteOpt = hypothesis.translationOpt;
		foreignPhrase = transOpt.foreign;
		translatedPhrase = transOpt.translation;
		phraseTableName = concreteOpt.phraseTableName;
		translationScores = transOpt.scores;
		phraseScoreNames = transOpt.phraseScoreNames;
		translationPosition = hypothesis.insertionPosition;
		foreignPosition = concreteOpt.foreignPos;
		linearDistortion = hypothesis.linearDistortion;

    Object[] tokens = new Object[hypothesis.length];
		retreiveTokens(tokens, hypothesis);
		partialTranslation = partialTranslationRaw = new RawSequence<TK>((TK[])tokens);
		foreignSentence = hypothesis.foreignSequence;
		untranslatedTokens = hypothesis.untranslatedTokens;	
		prior = hypothesis.preceedingHyp.featurizable;
    if (prior != null) {
      foreignCoverage = prior.foreignCoverage.clone();
      t2fAlignmentIndex = copyOfIndex(prior.t2fAlignmentIndex, hypothesis.length);
			f2tAlignmentIndex = copyOfIndex(prior.f2tAlignmentIndex, prior.f2tAlignmentIndex.length);
      states = new Object[nbStatefulFeaturizers];
      //states = prior.states.clone();
    } else {
      foreignCoverage = new CoverageSet();
			t2fAlignmentIndex = new int[hypothesis.length][];
			f2tAlignmentIndex = new int[foreignSentence.size()][];
      states = new Object[nbStatefulFeaturizers];
    }
    foreignCoverage.or(hypothesis.foreignCoverage);
		hyp = hypothesis;
		augmentAlignments(concreteOpt);
	}

  public Object getState(StatefulFeaturizer<IString,String> f) {
    return states[f.getId()];
  }

  public void setState(StatefulFeaturizer<IString,String> f, Object s) {
    states[f.getId()] = s;
  }

  /**
	 * Avoid Arrays.copyOf and it's sluggish call to Class.getComponentType
	 * @return
	 */
	private int[][] copyOfIndex(int[][] index, int newLength) {
		int[][] newIndex = new int[newLength][];
		System.arraycopy(index, 0, newIndex, 0, Math.min(index.length, newLength));
		return newIndex;
	}
	
	/**
	 * 
	 * @param foreignSequence
	 * @param concreteOpt
	 */
	public Featurizable(Sequence<TK> foreignSequence, ConcreteTranslationOption<TK> concreteOpt, int translationId) {
		this.translationId = translationId;
		option = concreteOpt;
		done = false;
    foreignCoverage = null;
    TranslationOption<TK> transOpt = concreteOpt.abstractOption;
		foreignPhrase = transOpt.foreign;
		translatedPhrase = transOpt.translation;
		phraseTableName = concreteOpt.phraseTableName;
		translationScores = transOpt.scores;
		phraseScoreNames = transOpt.phraseScoreNames;
		translationPosition = 0;
		foreignPosition = concreteOpt.foreignPos;
		partialTranslation = translatedPhrase;
		partialTranslationRaw = null;
		foreignSentence = foreignSequence;
		untranslatedTokens = foreignSequence.size() - foreignPhrase.size();
		prior = null;
    states = null;
    linearDistortion = Integer.MAX_VALUE;
		t2fAlignmentIndex = new int[translatedPhrase.size()][];
		f2tAlignmentIndex = new int[foreignSentence.size()][];
		augmentAlignments(concreteOpt);
		hyp = null;
	}

	/**
	 * 
	 * @param concreteOpt
	 */
	private void augmentAlignments(ConcreteTranslationOption<TK> concreteOpt) {
		int transSz = concreteOpt.abstractOption.translation.elements.length;
		int foreignSz = concreteOpt.abstractOption.foreign.elements.length;
		int limit;
		int[] range =  new int[2];
		range[PHRASE_START] = foreignPosition;
		range[PHRASE_END]   = foreignPosition + foreignSz;
		limit = translationPosition+transSz; 
		for (int i = translationPosition; i < limit; i++) {
			t2fAlignmentIndex[i] = range;
		}
		
		range =  new int[2];
		range[PHRASE_START] = translationPosition;
		range[PHRASE_END]   = translationPosition+transSz;
		limit = foreignPosition+foreignSz;
		for (int i = foreignPosition; i < limit; i++) {
			f2tAlignmentIndex[i] = range;
		}	
	}
	
	
	private void retreiveTokens(Object[] tokens, Hypothesis<TK,FV> h) {
		int pos = 0;
		Featurizable<TK,FV> preceedingF = h.preceedingHyp.featurizable; 
		if (preceedingF != null) {
			Object[] preceedingTokens = preceedingF.partialTranslationRaw.elements;
			System.arraycopy(preceedingTokens, 0, tokens, 0, pos=preceedingTokens.length);
		}
		
		ConcreteTranslationOption<TK> concreteOpt = h.translationOpt; 
		Object[] newTokens = concreteOpt.abstractOption.translation.elements;
		System.arraycopy(newTokens, 0, tokens, pos, newTokens.length);
	}
	
}
