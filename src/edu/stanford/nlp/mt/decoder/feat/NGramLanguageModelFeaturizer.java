package edu.stanford.nlp.mt.decoder.feat;

import java.io.IOException;
import java.util.*;

import edu.stanford.nlp.mt.base.*;
import edu.stanford.nlp.util.Index;

/**
 *
 * @author danielcer
 */
public class NGramLanguageModelFeaturizer implements
    IncrementalFeaturizer<IString, String>, IsolatedPhraseFeaturizer<IString, String> {
  public static final String FEATURE_PREFIX = "LM:";
  public static final String FEATURE_NAME = "LM";
  public static final String DEBUG_PROPERTY = "ngramLMFeaturizerDebug";
  final String featureName;
  final String[][] featureNames;
  private final String featureNameWithColon;
  final boolean ngramReweighting;
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
    return new NGramLanguageModelFeaturizer(lm, args[1], false);
  }

  public NGramLanguageModelFeaturizer(LanguageModel<IString> lm) {
    this.lm = lm;
    featureName = FEATURE_NAME;
    featureNameWithColon = featureName + ":";
    this.ngramReweighting = false;
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

  public NGramLanguageModelFeaturizer(LanguageModel<IString> lm, String featureName,
      boolean ngramReweighting) {
    this.lm = lm;
    this.featureName = featureName;
    featureNameWithColon = featureName + ":";
    this.ngramReweighting = ngramReweighting;
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
    this.ngramReweighting = false;
    this.lmOrder = lm.order();
    if (lmLabeled) {
      featureName = String.format("%s%s", FEATURE_PREFIX, lm.getName());
    } else {
      featureName = FEATURE_NAME;
    }
    featureNameWithColon = featureName + ":";
    this.lengthNorm = false;
    featureNames = new String[2][];
    featureNames[0] = new String[lmOrder];
    featureNames[1] = new String[lmOrder];
    for (int i = 0; i < lmOrder; i++) {
      featureNames[0][i] = String.format("%s:%d:freq", featureName, i+1);
      featureNames[1][i] = String.format("%s:%d:nomc", featureName, i+1);
    }
  }

  private static final String NGRAM_REWEIGHTING_PREFIX = "dnr";

  /**
   * Constructor called by Phrasal when NGramLanguageModelFeaturizer appears in
   * [additional-featurizers].
   */
  public NGramLanguageModelFeaturizer(String... args) throws IOException {
    if (args.length == 1) {
      this.lmOrder = Integer.parseInt(args[0]);
      this.ngramReweighting = true;
      featureName = NGRAM_REWEIGHTING_PREFIX;
      featureNameWithColon = featureName + ":";
      this.featureNames = null;
      this.lm = new IndicatorFunctionLM(lmOrder);
      this.lengthNorm = false;
      return;
    }
    if (args.length < 2 || args.length > 3)
      throw new RuntimeException(
          "Two arguments are needed: LM file name and LM ID");
    featureName = args[1];
    featureNameWithColon = featureName + ":";
    this.ngramReweighting = false;
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
   *
   */
  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> featurizable) {
    if (ngramReweighting || lm instanceof MultiScoreLanguageModel) {
      return null;
    }

    if (DEBUG) {
      System.out.printf("Sequence: %s\n\tNovel Phrase: %s\n",
          featurizable.targetPrefix, featurizable.targetPhrase);
      System.out.printf("Untranslated tokens: %d\n",
          featurizable.untranslatedTokens);
      System.out.println("ngram scoring:");
      System.out.println("===================");
    }

    IString startToken = lm.getStartToken();
    IString endToken = lm.getEndToken();

    Sequence<IString> partialTranslation;
    int startPos = featurizable.targetPosition + 1;
    if (featurizable.done) {
      partialTranslation = new InsertedStartEndToken<IString>(
          featurizable.targetPrefix, startToken, endToken);
    } else {
      partialTranslation = new InsertedStartToken<IString>(
          featurizable.targetPrefix, startToken);
    }
    int limit = partialTranslation.size();

    double lmScore = getScore(startPos, limit, partialTranslation);

    if (DEBUG) {
      System.out.printf("Final score: %f\n", lmScore);
    }
    if (SVMNORM) {
      return new FeatureValue<String>(featureName, lmScore / 2.0);
    } else if (lengthNorm) {
      double v;
      synchronized (rawLMScoreHistory) {
        double lastLMSent = (featurizable.prior == null ? 0 : rawLMScoreHistory
            .get(featurizable.prior));
        double lastFv = (featurizable.prior == null ? 0 : lastLMSent
            / featurizable.prior.targetPrefix.size());
        double currentLMSent = lastLMSent + lmScore;
        double currentFv = currentLMSent
            / featurizable.targetPrefix.size();
        v = currentFv - lastFv;
        rawLMScoreHistory.put(featurizable, currentLMSent);
      }
      return new FeatureValue<String>(featureName, v);
    } else {
      return new FeatureValue<String>(featureName, lmScore);
    }
  }

  private double[][] getMultiScore(int startPos, int limit, Sequence<IString> translation) {
    double[][] multiScore;
    int order = lmOrder;
    MultiScoreLanguageModel<IString> mlm = (MultiScoreLanguageModel<IString>)lm;
    multiScore = new double[2][];
    multiScore[0] = new double[order];
    multiScore[1] = new double[order];

    for (int pos = startPos; pos < limit; pos++) {
      int seqStart = pos - order + 1;
      if (seqStart < 0)
        seqStart = 0;
      Sequence<IString> ngram = translation.subsequence(seqStart, pos + 1);
      double[] ngramScore = mlm.multiScore(ngram);
      for (int i = 0; i < order; i++)  {
        if (ngramScore[i] == Double.NEGATIVE_INFINITY) {
          multiScore[1][i] ++;
        } else {
          multiScore[0][i] += ngramScore[i];
        }
      }
    }
    return multiScore;
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
      double ngramScore = lm.score(ngram);
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
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) {
    if (ngramReweighting) {
      IString startToken = lm.getStartToken();
      IString endToken = lm.getEndToken();

      Sequence<IString> partialTranslation;
      int startPos = f.targetPosition + 1;
      if (f.done) {
        partialTranslation = new InsertedStartEndToken<IString>(f.targetPrefix,
            startToken, endToken);
      } else {
        partialTranslation = new InsertedStartToken<IString>(f.targetPrefix,
            startToken);
      }
      int limit = partialTranslation.size();
      return getFeatureList(startPos, limit, partialTranslation);
    } else if (lm instanceof MultiScoreLanguageModel) {
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

      double[][] lmScore = getMultiScore(startPos, limit, partialTranslation);
      List<FeatureValue<String>> feats = new ArrayList<FeatureValue<String>>(
          2*lmOrder);
      for (int i = 0; i < lmOrder; i++) {
        feats.add(new FeatureValue<String>(featureNames[0][i], lmScore[0][i]));
        feats.add(new FeatureValue<String>(featureNames[1][i], lmScore[1][i]));
      }
      return feats;
    } else {
      return null;
    }
  }

  /**
   *
   */
  private List<FeatureValue<String>> getFeatureList(int startPos, int limit,
      Sequence<IString> translation) {
    int maxOrder = lmOrder;
    int guessSize = (limit - startPos) * maxOrder;
    List<FeatureValue<String>> feats = new ArrayList<FeatureValue<String>>(
        guessSize);
    if (DEBUG) {
      System.err.printf("getFeatureList(%d,%d,%s) order:%d guessSize: %d\n",
          startPos, limit, translation, maxOrder, guessSize);
    }

    for (int endWordPos = startPos; endWordPos < limit; endWordPos++) {
      for (int order = 0; order < maxOrder; order++) {
        int beginWordPos = endWordPos - order;
        if (beginWordPos < 0)
          break;
        Sequence<IString> ngram = translation.subsequence(beginWordPos,
            endWordPos + 1);
        String featName = ngram.toString(featureNameWithColon, "_");
        if (DEBUG) {
          System.err.printf("dlm(%d,%d): %s\n", beginWordPos, endWordPos + 1,
              featName);
        }
        feats.add(new FeatureValue<String>(featName, lm.score(ngram)));
      }
    }

    return feats;
  }

  @Override
  public FeatureValue<String> phraseFeaturize(Featurizable<IString, String> f) {
    if (ngramReweighting || (lm instanceof MultiScoreLanguageModel)) {
      return null;
    }

    assert (f.targetPhrase != null);
    double lmScore = getScore(0, f.targetPhrase.size(), f.targetPhrase);
    if (SVMNORM) {
      return new FeatureValue<String>(featureName, lmScore / 2.0);
    } else if (lengthNorm) {
      return new FeatureValue<String>(featureName, lmScore
          / f.targetPhrase.size());
    } else {
      return new FeatureValue<String>(featureName, lmScore);
    }
  }

  @Override
  public List<FeatureValue<String>> phraseListFeaturize(
      Featurizable<IString, String> f) {
    if (ngramReweighting) {
      return getFeatureList(0, f.targetPhrase.size(), f.targetPhrase);
    } else if (lm instanceof MultiScoreLanguageModel) {
      int limit = f.targetPhrase.size();
      double[][] lmScore = getMultiScore(0, limit, f.targetPhrase);
      List<FeatureValue<String>> feats = new ArrayList<FeatureValue<String>>(
          2*lmOrder);
      for (int i = 0; i < lmOrder; i++) {
        feats.add(new FeatureValue<String>(featureNames[0][i], lmScore[0][i]));
        feats.add(new FeatureValue<String>(featureNames[1][i], lmScore[1][i]));
      }
      return feats;
    } else {
      return null;
    }
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<IString,String>> options,
      Sequence<IString> foreign, Index<String> featureIndex) {
    rawLMScoreHistory.clear();
  }

  @Override
  public void reset() {
  }

  @Override
  public void initialize(Index<String> featureIndex) {
  }

}
