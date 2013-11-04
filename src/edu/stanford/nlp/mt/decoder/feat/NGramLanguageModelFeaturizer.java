package edu.stanford.nlp.mt.decoder.feat;

import java.io.IOException;
import java.util.List;
import java.util.WeakHashMap;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.InsertedStartEndToken;
import edu.stanford.nlp.mt.base.InsertedStartToken;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.lm.ARPALanguageModel;
import edu.stanford.nlp.mt.lm.LMState;
import edu.stanford.nlp.mt.lm.LanguageModel;
import edu.stanford.nlp.mt.lm.LanguageModels;
import edu.stanford.nlp.util.Generics;

/**
 * Featurizer for n-gram language models.
 * 
 * @author danielcer
 */
public class NGramLanguageModelFeaturizer implements
    DerivationFeaturizer<IString, String>, RuleIsolationScoreFeaturizer<IString, String> {
  public static final String FEATURE_PREFIX = "LM:";
  public static final String FEATURE_NAME = "LM";
  public static final String DEBUG_PROPERTY = "ngramLMFeaturizerDebug";
  final String featureName;
  final String[][] featureNames;
  final boolean lengthNorm;
  final WeakHashMap<Featurizable<IString, String>, Double> rawLMScoreHistory = new WeakHashMap<Featurizable<IString, String>, Double>();
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));
  public static final boolean SVMNORM = Boolean.parseBoolean(System
      .getProperty("SVMNORM", "false"));

  public final LanguageModel<IString> lm;
  final int lmOrder;

  public static final double MOSES_LM_UNKNOWN_WORD_SCORE = -100; // in sri lm
                                                                 // -99 is
                                                                 // -infinity

  public static NGramLanguageModelFeaturizer fromFile(String... args)
      throws IOException {
    if (args.length < 2 || args.length > 3)
      throw new RuntimeException(
          "Two arguments are needed: LM file name and LM ID");
    LanguageModel<IString> lm = args.length == 3 ? LanguageModels.load(
        args[0], args[2]) : ARPALanguageModel.load(args[0]);
    return new NGramLanguageModelFeaturizer(lm, args[1]);
  }

  public NGramLanguageModelFeaturizer(LanguageModel<IString> lm) {
    this.lm = lm;
    featureName = FEATURE_NAME;
    this.lmOrder = lm.order();
    this.lengthNorm = false;
    featureNames = new String[2][];
    featureNames[0] = new String[lmOrder];
    featureNames[1] = new String[lmOrder];
    for (int i = 0; i < lmOrder; i++) {
      featureNames[0][i] = String.format("%s:%d:freq", featureName, i+1);
      featureNames[1][i] = String.format("%s:%d:nomc", featureName, i+1);
    }
  }

  /**
   *
   */
  public int order() {
    return lm.order();
  }

  public NGramLanguageModelFeaturizer(LanguageModel<IString> lm, String featureName) {
    this.lm = lm;
    this.featureName = featureName;
    this.lmOrder = lm.order();
    this.lengthNorm = false;
    featureNames = new String[2][];
    featureNames[0] = new String[lmOrder];
    featureNames[1] = new String[lmOrder];
    for (int i = 0; i < lmOrder; i++) {
      featureNames[0][i] = String.format("%s:%d:freq", featureName, i+1);
      featureNames[1][i] = String.format("%s:%d:nomc", featureName, i+1);
    }
  }

  /**
   *
   */
  public NGramLanguageModelFeaturizer(LanguageModel<IString> lm, boolean lmLabeled) {
    this.lm = lm;
    this.lmOrder = lm.order();
    if (lmLabeled) {
      featureName = String.format("%s%s", FEATURE_PREFIX, lm.getName());
    } else {
      featureName = FEATURE_NAME;
    }
    this.lengthNorm = false;
    featureNames = new String[2][];
    featureNames[0] = new String[lmOrder];
    featureNames[1] = new String[lmOrder];
    for (int i = 0; i < lmOrder; i++) {
      featureNames[0][i] = String.format("%s:%d:freq", featureName, i+1);
      featureNames[1][i] = String.format("%s:%d:nomc", featureName, i+1);
    }
  }

  /**
   * Constructor called by Phrasal when NGramLanguageModelFeaturizer appears in
   * [additional-featurizers].
   */
  public NGramLanguageModelFeaturizer(String... args) throws IOException {
    if (args.length < 2 || args.length > 3)
      throw new RuntimeException(
          "Two arguments are needed: LM file name and LM ID");
    featureName = args[1];
    if (args.length == 3) {
      if (args[2].equals("true") || args[2].equals("false")) {
        this.lm = LanguageModels.load(args[0]);
        this.lengthNorm = Boolean.parseBoolean(args[2]);
      } else {
        this.lm = LanguageModels.load(args[0], args[2]);
        this.lengthNorm = false;
      }
    } else {
      this.lm = LanguageModels.load(args[0]);
      this.lengthNorm = false;
    }
    this.lmOrder = lm.order();
    featureNames = new String[2][];
    featureNames[0] = new String[lmOrder];
    featureNames[1] = new String[lmOrder];
    for (int i = 0; i < lmOrder; i++) {
      featureNames[0][i] = String.format("%s:%d:freq", featureName, i+1);
      featureNames[1][i] = String.format("%s:%d:nomc", featureName, i+1);
    }
  }

  /**
	 *
	 */
  private double getScore(int startPos, int limit, Sequence<IString> translation) {
    double lmSumScore = 0;
    int order = lmOrder;

    for (int pos = startPos; pos < limit; pos++) {
      int seqStart = pos - order + 1;
      if (seqStart < 0)
        seqStart = 0;
      Sequence<IString> ngram = translation.subsequence(seqStart, pos + 1);
      LMState state = lm.score(ngram);
      double ngramScore = state.getScore();
      if (ngramScore == Double.NEGATIVE_INFINITY || ngramScore != ngramScore) {
        lmSumScore += MOSES_LM_UNKNOWN_WORD_SCORE;
        continue;
      }
      lmSumScore += ngramScore;
      if (DEBUG) {
        System.out.printf("\tn-gram: %s score: %f\n", ngram, ngramScore);
      }
    }
    return lmSumScore;
  }

  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    if (DEBUG) {
      System.out.printf("Sequence: %s\n\tNovel Phrase: %s\n",
          f.targetPrefix, f.targetPhrase);
      System.out.printf("Untranslated tokens: %d\n",
          f.numUntranslatedSourceTokens);
      System.out.println("ngram scoring:");
      System.out.println("===================");
    }

    IString startToken = lm.getStartToken();
    IString endToken = lm.getEndToken();

    Sequence<IString> partialTranslation;
    int startPos = f.targetPosition + 1;
    if (f.done) {
      partialTranslation = new InsertedStartEndToken<IString>(
          f.targetPrefix, startToken, endToken);
    } else {
      partialTranslation = new InsertedStartToken<IString>(
          f.targetPrefix, startToken);
    }
    int limit = partialTranslation.size();

    double lmScore = getScore(startPos, limit, partialTranslation);

    if (DEBUG) {
      System.out.printf("Final score: %f\n", lmScore);
    }
    List<FeatureValue<String>> features = Generics.newLinkedList();
    if (SVMNORM) {
      features.add(new FeatureValue<String>(featureName, lmScore / 2.0));
    } else if (lengthNorm) {
      double v;
      synchronized (rawLMScoreHistory) {
        double lastLMSent = (f.prior == null ? 0 : rawLMScoreHistory
            .get(f.prior));
        double lastFv = (f.prior == null ? 0 : lastLMSent
            / f.prior.targetPrefix.size());
        double currentLMSent = lastLMSent + lmScore;
        double currentFv = currentLMSent
            / f.targetPrefix.size();
        v = currentFv - lastFv;
        rawLMScoreHistory.put(f, currentLMSent);
      }
      features.add(new FeatureValue<String>(featureName, v));
    } else {
      features.add(new FeatureValue<String>(featureName, lmScore));
    }
    return features;
  }

  @Override
  public List<FeatureValue<String>> ruleFeaturize(
      Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    assert (f.targetPhrase != null);
    double lmScore = getScore(0, f.targetPhrase.size(), f.targetPhrase);
    if (SVMNORM) {
      features.add(new FeatureValue<String>(featureName, lmScore / 2.0));
    } else if (lengthNorm) {
      features.add(new FeatureValue<String>(featureName, lmScore
          / f.targetPhrase.size()));
    } else {
      features.add(new FeatureValue<String>(featureName, lmScore));
    }
    return features;
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString,String>> options, Sequence<IString> foreign) {
    rawLMScoreHistory.clear();
  }

  @Override
  public void initialize() {}
}
