package edu.stanford.nlp.mt.base;

import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.util.Characters;
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

  public static final String PHRASE_TABLE_NAME = "IdentityPhraseGenerator(Dyn)";
  public static final String UNK_FEATURE_NAME = "TM.UNK";

  // do we need to account for "(0) (1)", etc?
  public static final PhraseAlignment DEFAULT_ALIGNMENT = PhraseAlignment
      .getPhraseAlignment(PhraseAlignment.PHRASE_ALIGNMENT);

  private final boolean dropUnknownWords;
  private final RawSequence<TK> empty = new RawSequence<TK>();
  private final IntegerArrayIndex sourceIndex;
  private final String[] featureNames = { UNK_FEATURE_NAME };
  private final float[] featureValues = { (float) 1.0 };

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
    return PHRASE_TABLE_NAME;
  }
  
  @Override
  public List<String> getFeatureNames() {
    return Arrays.asList(featureNames);
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

      if (dropUnknownWords && !isNumericOrPunctuationOrSymbols(word) && !asciiHeuristic(word)) {
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
  
  /** 
   * Returns true if all letter and number characters are ASCII
   * 
   * @param word
   * @return true/false all letter and number characters are ASCII 
   */
  private static boolean asciiHeuristic(String word) {
     int len = word.length();
     for (int i = 0; i < len; ++i) {
       char c = word.charAt(i);
       if (Character.isAlphabetic(c) && (int)c>>7 != 0) {
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
