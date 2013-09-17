package edu.stanford.nlp.mt.base;

import java.util.List;

import edu.stanford.nlp.util.Characters;
import edu.stanford.nlp.util.Generics;

/**
 * Generates synthetic rules for unknown words.
 *
 * @author danielcer
 * @author Spence Green
 *
 * @param <TK>
 */
public class UnknownWordPhraseGenerator<TK extends HasIntegerIdentity, FV> extends
    AbstractPhraseGenerator<TK, FV> implements DynamicPhraseGenerator<TK,FV> {

  public static final String PHRASE_TABLE_NAMES = "IdentityPhraseGenerator(Dyn)";
  public static final String[] DEFAULT_SCORE_NAMES = { "p_i(t|f)" };
  public static final float[] SCORE_VALUES = { (float) 1.0 };
  public static final String DEBUG_PROPERTY = "UnknownWordPhraseGeneratorDebug";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));

  // do we need to account for "(0) (1)", etc?
  public static final PhraseAlignment DEFAULT_ALIGNMENT = PhraseAlignment
      .getPhraseAlignment(PhraseAlignment.PHRASE_ALIGNMENT);

  private final String[] scoreNames = DEFAULT_SCORE_NAMES;
  private final boolean dropUnknownWords;
  private final RawSequence<TK> empty = new RawSequence<TK>();
  private final IntegerArrayIndex sourceIndex;

  /**
   * Constructor.
   * 
   * @param dropUnknownWords
   * @param sourceIndex 
   */
  public UnknownWordPhraseGenerator(boolean dropUnknownWords, IntegerArrayIndex sourceIndex) {
    super(null);
    this.dropUnknownWords = dropUnknownWords;
    this.sourceIndex = sourceIndex;
  }

  @Override
  public String getName() {
    return PHRASE_TABLE_NAMES;
  }

  @Override
  public List<Rule<TK>> query(Sequence<TK> sequence) {
    if (sequence.size() > longestSourcePhrase()) {
      throw new RuntimeException("Source phrase too long: " + String.valueOf(sequence.size()));
    }
    List<Rule<TK>> list = Generics.newLinkedList();

    // Check to see if this word is unknown
    int[] foreignInts = Sequences.toIntArray(sequence);
    int sIndex = sourceIndex.indexOf(foreignInts);
    if (sIndex < 0) {
      RawSequence<TK> raw = new RawSequence<TK>(sequence);
      final String word = sequence.get(0).toString();

      if (dropUnknownWords && !isNumericOrPunctuationOrSymbols(word)) {
        // Deletion rule
        list.add(new Rule<TK>(SCORE_VALUES, scoreNames, empty, raw,
            DEFAULT_ALIGNMENT));

      } else {
        // Identity translation rule
        list.add(new Rule<TK>(SCORE_VALUES, scoreNames, raw, raw,
            DEFAULT_ALIGNMENT));
      }
    }
    return list;
  }

  /**
   * Returns true if a string consists entirely of numbers, punctuation, 
   * and/or symbols.
   * 
   * @param word
   * @return
   */
  private static boolean isNumericOrPunctuationOrSymbols(String word) {
    int len = word.length();
    for (int i = 0; i < len; ++i) {
      char c = word.charAt(i);
      if ( !(Character.isDigit(c) || Characters.isPunctuation(c) || Characters.isSymbol(c))) {
        return false;
      }
    }
    return true;
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
