package edu.stanford.nlp.mt.pt;

import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.mt.pt.TranslationModelVocabulary.Vocabulary;
import edu.stanford.nlp.mt.util.HasIntegerIdentity;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.RawSequence;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.TokenUtils;
import edu.stanford.nlp.util.Generics;

/**
 * Unknown word model. Generates synthetic rules for unknown words.
 *
 * @author danielcer
 * @author Spence Green
 *
 * @param <TK>
 */
public class UnknownWordPhraseGenerator<TK extends HasIntegerIdentity, FV> extends
    AbstractPhraseGenerator<TK, FV> implements DynamicPhraseGenerator<TK,FV> {

  public static final String PHRASE_TABLE_NAME = UnknownWordPhraseGenerator.class.getName();
  public static final String UNK_FEATURE_NAME = "TM.UNK";

  private static final PhraseAlignment DEFAULT_ALIGNMENT = PhraseAlignment
      .getPhraseAlignment(PhraseAlignment.MONOTONE_ALIGNMENT);

  private final boolean dropUnknownWords;
  private final RawSequence<TK> empty = new RawSequence<TK>();
  private final String[] featureNames = { UNK_FEATURE_NAME };
  private final float[] featureValues = { (float) 1.0 };
  
  private final Vocabulary sourceVocab = TranslationModelVocabulary.getSourceInstance();
  
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

  @SuppressWarnings("unchecked")
  @Override
  public List<Rule<TK>> query(Sequence<TK> sequence) {
    if (sequence.size() > longestSourcePhrase()) {
      throw new RuntimeException("Source phrase too long: " + String.valueOf(sequence.size()));
    }
    List<Rule<TK>> list = Generics.newLinkedList();

    // Check to see if this word is unknown
    Sequence<IString> seq = (Sequence<IString>) sequence;
    if ( ! sourceVocab.contains(seq)) {
      RawSequence<TK> raw = new RawSequence<TK>(sequence);
      final String word = sequence.get(0).toString();

      if (dropUnknownWords && !isTranslateable(word)) {
        // Deletion rule
        list.add(new Rule<TK>(featureValues, featureNames, empty, raw,
            DEFAULT_ALIGNMENT));

      } else {
        // Identity translation rule
        list.add(new Rule<TK>(featureValues, featureNames, raw, raw,
            DEFAULT_ALIGNMENT));
      }
    }
    return list;
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
  public int longestSourcePhrase() {
    // DO NOT change this value unless you know what you're doing.
    return 1;
  }

  @Override
  public int longestTargetPhrase() {
    return 1;
  }
}
