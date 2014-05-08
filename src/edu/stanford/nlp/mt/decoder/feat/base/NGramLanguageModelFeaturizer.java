package edu.stanford.nlp.mt.decoder.feat.base;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.lm.LMState;
import edu.stanford.nlp.mt.lm.LanguageModel;
import edu.stanford.nlp.mt.lm.LanguageModelFactory;
import edu.stanford.nlp.mt.pt.ConcreteRule;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.util.SimpleSequence;
import edu.stanford.nlp.mt.util.TargetClassMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;

/**
 * Featurizer for n-gram language models.
 * 
 * @author danielcer
 * @author Spence Green
 */
public class NGramLanguageModelFeaturizer extends DerivationFeaturizer<IString, String> implements
RuleFeaturizer<IString, String> {
  private static final boolean DEBUG = false;
  public static final String DEFAULT_FEATURE_NAME = "LM";

  private final String featureName;
  private final LanguageModel<IString> lm;
  private final IString startToken;
  private final IString endToken;

  private final boolean isClassBased;
  private final TargetClassMap targetClassMap;

  /**
   * Constructor.
   * 
   * @param lm
   */
  public NGramLanguageModelFeaturizer(LanguageModel<IString> lm) {
    this.lm = lm;
    featureName = DEFAULT_FEATURE_NAME;
    this.startToken = lm.getStartToken();
    this.endToken = lm.getEndToken();
    this.isClassBased = false;
    this.targetClassMap = null;
  }

  /**
   * Constructor called by Phrasal when NGramLanguageModelFeaturizer appears in
   * [additional-featurizers].
   * 
   * The first argument is always the language model filename and the second
   * argument is always the feature name.
   * 
   * Additional arguments are named parameters.
   */
  public NGramLanguageModelFeaturizer(String...args) throws IOException {
    if (args.length < 2) {
      throw new RuntimeException(
          "At least two arguments are needed: LM file name and LM feature name");
    }
    // Load the LM
    this.lm = LanguageModelFactory.load(args[0]);
    this.startToken = lm.getStartToken();
    this.endToken = lm.getEndToken();

    // Set the feature name
    this.featureName = args[1];

    // Named parameters
    Properties options = FeatureUtils.argsToProperties(args);
    this.isClassBased = PropertiesUtils.getBool(options, "classBased", false);
    this.targetClassMap = isClassBased ? TargetClassMap.getInstance() : null;
  }

  /**
   * Convert a lexical n-gram to a class-based n-gram.
   * 
   * @param targetSequence
   * @return
   */
  private Sequence<IString> toClassRepresentation(Sequence<IString> targetSequence) {
    if (targetSequence.size() == 0) return targetSequence;
    IString[] array = new IString[targetSequence.size()];
    for (int i = 0; i < array.length; ++i) {
      array[i] = targetClassMap.get(targetSequence.get(i));
    }
    return new SimpleSequence<IString>(true, array);
  }

  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    if (DEBUG) {
      System.err.printf("Sequence: %s%n\tNovel Phrase: %s%n",
          f.targetPrefix, f.targetPhrase);
      System.err.printf("Untranslated tokens: %d%n", f.numUntranslatedSourceTokens);
      System.err.println("ngram scoring:");
    }
    
    LMState priorState = f.prior == null ? null : (LMState) f.prior.getState(this);
    
    Sequence<IString> partialTranslation = isClassBased ? 
        toClassRepresentation(f.targetPhrase) : f.targetPhrase;
    int startIndex = 0;
    if (f.prior == null && f.done) {
      partialTranslation = Sequences.wrapStartEnd(
          partialTranslation, startToken, endToken);
      startIndex = 1;
    } else if (f.prior == null) {
      partialTranslation = Sequences.wrapStart(partialTranslation, startToken);
      startIndex = 1;
    } else if (f.done) {
      partialTranslation = Sequences.wrapEnd(partialTranslation, endToken);
    }
    
    LMState state = lm.score(partialTranslation, startIndex, priorState);

    List<FeatureValue<String>> features = Generics.newLinkedList();
    features.add(new FeatureValue<String>(featureName, state.getScore()));

    f.setState(this, state);
    
    if (DEBUG) {
      System.err.printf("Final score: %f%n", state.getScore());
      System.err.println("===================");
    }
    return features;
  }

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    assert (f.targetPhrase != null);
    double lmScore = lm.score(f.targetPhrase, 0, null).getScore();
    List<FeatureValue<String>> features = Generics.newLinkedList();
    features.add(new FeatureValue<String>(featureName, lmScore));
    return features;
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString,String>> options, Sequence<IString> foreign) {
  }

  @Override
  public void initialize() {}

  @Override
  public boolean isolationScoreOnly() {
    return true;
  }
}
