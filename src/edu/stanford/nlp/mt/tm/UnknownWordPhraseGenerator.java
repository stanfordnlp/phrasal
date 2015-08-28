package edu.stanford.nlp.mt.tm;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.util.TokenUtils;

/**
 * Unknown word model. Generates synthetic rules for unknown words.
 *
 * @author danielcer
 * @author Spence Green
 *
 * @param <TK>
 */
public class UnknownWordPhraseGenerator<TK, FV> extends
    AbstractPhraseGenerator<TK, FV> {

  public static final String PHRASE_TABLE_NAME = UnknownWordPhraseGenerator.class.getName();
  public static final String UNK_FEATURE_NAME = "TM.UNK";

  private static final PhraseAlignment DEFAULT_ALIGNMENT = PhraseAlignment
      .getPhraseAlignment(PhraseAlignment.MONOTONE_ALIGNMENT);

  private final boolean dropUnknownWords;
  private final String[] featureNames = { UNK_FEATURE_NAME };
  private final float[] featureValues = { (float) 1.0 };
  
  /**
   * Constructor.
   * 
   * @param dropUnknownWords
   * @param phraseGenerator 
   * @param sourceIndex 
   */
  public UnknownWordPhraseGenerator(boolean dropUnknownWords) {
    super(null);
    this.dropUnknownWords = dropUnknownWords;
  }

  @Override
  public String getName() {
    return PHRASE_TABLE_NAME;
  }
  
  @Override
  public List<String> getFeatureNames() {
    return Arrays.asList(featureNames);
  }

  @Override
  public List<Rule<TK>> query(Sequence<TK> sequence) {
    if (sequence.size() != maxLengthSource()) {
      throw new RuntimeException("Only single-word queries accepted. Query size: " + String.valueOf(sequence.size()));
    }
    // Check to see if this word is unknown
    final String word = sequence.get(0).toString();

    if (dropUnknownWords && !isTranslateable(word)) {
      // Deletion rule
      return Collections.singletonList(new Rule<TK>(featureValues, featureNames, Sequences.emptySequence(), sequence,
          DEFAULT_ALIGNMENT, PHRASE_TABLE_NAME));

    } else {
      // Identity translation rule
      return Collections.singletonList(new Rule<TK>(featureValues, featureNames, sequence, sequence,
          DEFAULT_ALIGNMENT, PHRASE_TABLE_NAME));
    }
  }
  
  /**
   * Returns true if an identity translation rule should be generated the input word.
   * False otherwise.
   * 
   * @param sourceWord
   * @return
   */
  private boolean isTranslateable(String sourceWord) {
    return TokenUtils.isNumericOrPunctuationOrSymbols(sourceWord) || TokenUtils.isURL(sourceWord);
  }

  @Override
  public int maxLengthSource() {
    // DO NOT change this value unless you know what you're doing.
    return 1;
  }

  @Override
  public int maxLengthTarget() {
    return 1;
  }

  @Override
  public void setName(String name) {}
}
