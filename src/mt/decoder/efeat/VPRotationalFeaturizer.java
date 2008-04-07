package mt.decoder.efeat;

import java.util.*;
import mt.*;
import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.Featurizables;
import mt.base.IString;
import mt.base.Sequence;
import mt.decoder.feat.IncrementalFeaturizer;

/**
 *
 * @author Pi-Chuan Chang
 *
 */
public class VPRotationalFeaturizer implements IncrementalFeaturizer<IString, String>{
  static final String FEATURE_PREFIX = "VPRot:";
  
  static final boolean DEBUG = true;

  public VPRotationalFeaturizer() {
    if (DEBUG) System.err.printf("Debug Mode\n");
  }

  static final IString START_SEQ = new IString("<s>");

  /**
   *
   * T B   T A
   *    \ /
   *    / \
   * F A   F B
   */
  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    int beg_foreignSwapPos = Featurizables.locationOfSwappedPhrase(f);
    if (beg_foreignSwapPos != -1) {
      // we have a swap
      int end_foreignSwapPos = Featurizables.endLocationOfSwappedPhrase(f);
      
      IString fBInitialWord = f.foreignSentence.get(beg_foreignSwapPos);
      IString fBFinalWord = f.foreignSentence.get(end_foreignSwapPos);
      
      IString fAFinalWord = f.foreignSentence.get(beg_foreignSwapPos-1);
      IString fAInitialWord = f.foreignSentence.get(f.foreignPosition);
      
      if (DEBUG) {
        System.err.printf("t: %s\n", f.partialTranslation);
        System.err.printf("f: %s\n", f.foreignSentence);
        System.err.printf("tp: %s\n", f.translatedPhrase);
        System.err.printf("fp: %s\n", f.foreignPhrase);
        System.err.printf("c: %s\n", f.hyp.foreignCoverage);
        System.err.printf("f pos: %d\n", f.foreignPosition);
        System.err.printf("t pos: %d\n", f.translationPosition);
        System.err.printf(
          "foreignSwap -- Translated: (%s-%s); Untrans: (%s-%s)\n",
          fBInitialWord, fBFinalWord, fAInitialWord, fAFinalWord);
      }
    }
    return null;
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<IString>> options,
                         Sequence<IString> foreign) { }


  @Override
  public List<FeatureValue<String>> listFeaturize(
    Featurizable<IString, String> f) {

    return null;
  }

  @Override
  public void reset() { }

}