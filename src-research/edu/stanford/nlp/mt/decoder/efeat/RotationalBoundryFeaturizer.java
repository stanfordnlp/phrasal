package edu.stanford.nlp.mt.decoder.efeat;

import java.util.*;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Featurizables;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;
import edu.stanford.nlp.util.Index;

/**
 * 
 * @author danielcer
 * 
 */
public class RotationalBoundryFeaturizer implements
    IncrementalFeaturizer<IString, String> {
  static final String FEATURE_PREFIX = "RotB:";

  static final Set<IString> EnglishSwapCues = new HashSet<IString>();
  static final Set<IString> ForeignSwapCues = new HashSet<IString>();

  static {
    EnglishSwapCues.add(new IString("of"));
    EnglishSwapCues.add(new IString("off"));
    EnglishSwapCues.add(new IString("that"));
    EnglishSwapCues.add(new IString("which"));
    EnglishSwapCues.add(new IString("who"));

    ForeignSwapCues.add(new IString("的"));
  }

  static final IString noCue = new IString("<none>");

  static final boolean DEBUG = false;

  public RotationalBoundryFeaturizer() {
    System.err.printf("EnglishSwapCues: %s\n", EnglishSwapCues);
    System.err.printf("ForeignSwapCues: %s\n", ForeignSwapCues);
    if (DEBUG)
      System.err.printf("Debug Mode\n");
  }

  static final IString START_SEQ = new IString("<s>");

  /**
   * 
   * T B T A \ / / \ F A F B
   */
  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    int foreignSwapPos = Featurizables.locationOfSwappedPhrase(f);
    if (DEBUG) {
      System.err.printf("foreignSwapPos: %d\n", foreignSwapPos);
    }

    IString englishSwapCue;
    IString foreignSwapCue;
    String type;
    if (foreignSwapPos != -1) {
      // we have a swap
      type = "swp:";
      IString tAInitialWord = f.targetPhrase.get(0);
      IString fBInitialWord = f.sourceSentence.get(foreignSwapPos);

      IString tBFinalWord = f.targetPrefix.get(f.targetPosition - 1);
      IString fAFinalWord = f.sourceSentence.get(foreignSwapPos - 1);

      englishSwapCue = EnglishSwapCues.contains(tAInitialWord) ? tAInitialWord
          : EnglishSwapCues.contains(tBFinalWord) ? tBFinalWord : noCue;
      foreignSwapCue = ForeignSwapCues.contains(fBInitialWord) ? fBInitialWord
          : ForeignSwapCues.contains(fAFinalWord) ? fAFinalWord : noCue;

      if (DEBUG) {
        System.err.printf("t: %s\n", f.targetPrefix);
        System.err.printf("f: %s\n", f.sourceSentence);
        System.err.printf("tp: %s\n", f.targetPhrase);
        System.err.printf("fp: %s\n", f.sourcePhrase);
        System.err.printf("c: %s\n", f.hyp.sourceCoverage);
        System.err.printf("f pos: %d\n", f.sourcePosition);
        System.err.printf("t pos: %d\n", f.targetPosition);
        System.err.printf(
            "englishSwapCue: %s (%s-%s) foreignSwapCue: %s (%s-%s)\n",
            englishSwapCue, tAInitialWord, tBFinalWord, foreignSwapCue,
            fBInitialWord, fAFinalWord);
      }
    } else {
      type = "cmp:";
      IString tAInitialWord = f.targetPhrase.get(0);
      IString fAInitialWord = f.sourcePhrase.get(0);

      IString tBFinalWord = (f.targetPosition > 0 ? f.targetPrefix
          .get(f.targetPosition - 1) : START_SEQ);
      IString fBFinalWord = (f.sourcePosition > 0 ? f.sourceSentence
          .get(f.sourcePosition - 1) : START_SEQ);
      // we have no swap
      englishSwapCue = EnglishSwapCues.contains(tAInitialWord) ? tAInitialWord
          : EnglishSwapCues.contains(tBFinalWord) ? tBFinalWord : noCue;
      foreignSwapCue = ForeignSwapCues.contains(fAInitialWord) ? fAInitialWord
          : ForeignSwapCues.contains(fBFinalWord) ? fBFinalWord : noCue;
    }

    if (englishSwapCue == noCue && foreignSwapCue == noCue)
      return null;

    String featureString = FEATURE_PREFIX + type + englishSwapCue.toString()
        + ":" + foreignSwapCue.toString();
    if (DEBUG) {
      System.err.printf("Feature string: %s\n", featureString);
    }

    return new FeatureValue<String>(featureString, 1.0);
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<IString,String>> options,
      Sequence<IString> foreign, Index<String> featureIndex) {
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(
      Featurizable<IString, String> f) {

    return null;
  }

  @Override
  public void reset() {
  }

}
