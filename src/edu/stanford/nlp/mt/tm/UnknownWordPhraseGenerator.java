package edu.stanford.nlp.mt.tm;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.decoder.util.RuleGrid;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.util.EmptySequence;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.RawSequence;
import edu.stanford.nlp.mt.util.Sequence;
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

  private final Sequence<TK> EMPTY_SEQUENCE = new EmptySequence<TK>();

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
    if (sequence.size() > maxLengthSource()) {
      throw new RuntimeException("Only single-word queries accepted. Query size: " + String.valueOf(sequence.size()));
    }
    List<Rule<TK>> list = new LinkedList<>();

    // Check to see if this word is unknown
    RawSequence<TK> sourceWord = new RawSequence<TK>(sequence);
    final String word = sequence.get(0).toString();

    if (dropUnknownWords && !isTranslateable(word)) {
      // Deletion rule
      list.add(new Rule<TK>(featureValues, featureNames, EMPTY_SEQUENCE, sourceWord,
          DEFAULT_ALIGNMENT));

    } else {
      // Identity translation rule
      list.add(new Rule<TK>(featureValues, featureNames, sourceWord, sourceWord,
          DEFAULT_ALIGNMENT));
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
  public int maxLengthSource() {
    // DO NOT change this value unless you know what you're doing.
    return 1;
  }

  @Override
  public int maxLengthTarget() {
    return 1;
  }

  @Override
  public RuleGrid<TK, FV> getRuleGrid(Sequence<TK> source,
      InputProperties sourceInputProperties, List<Sequence<TK>> targets,
      int sourceInputId, Scorer<FV> scorer) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }
}
