package edu.stanford.nlp.mt.decoder.efeat;

import java.util.*;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Featurizables;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;
import edu.stanford.nlp.util.Index;

/**
 * 
 * @author Pi-Chuan Chang
 * 
 */
public class VPRotationalFeaturizer implements
    IncrementalFeaturizer<IString, String> {
  static final String FEATURE_PREFIX = "VPRot:";

  static final boolean DEBUG = true;

  public VPRotationalFeaturizer() {
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
    int beg_foreignSwapPos = Featurizables.locationOfSwappedPhrase(f);
    if (beg_foreignSwapPos != -1) {
      // we have a swap
      int end_foreignSwapPos = Featurizables.endLocationOfSwappedPhrase(f);

      IString fBInitialWord = f.sourceSentence.get(beg_foreignSwapPos);
      IString fBFinalWord = f.sourceSentence.get(end_foreignSwapPos);

      IString fAFinalWord = f.sourceSentence.get(beg_foreignSwapPos - 1);
      IString fAInitialWord = f.sourceSentence.get(f.sourcePosition);

      if (DEBUG) {
        System.err.printf("t: %s\n", f.targetPrefix);
        System.err.printf("f: %s\n", f.sourceSentence);
        System.err.printf("tp: %s\n", f.targetPhrase);
        System.err.printf("fp: %s\n", f.sourcePhrase);
        System.err.printf("c: %s\n", f.hyp.sourceCoverage);
        System.err.printf("f pos: %d\n", f.sourcePosition);
        System.err.printf("t pos: %d\n", f.targetPosition);
        System.err.printf(
            "foreignSwap -- Translated: (%s-%s); Untrans: (%s-%s)\n",
            fBInitialWord, fBFinalWord, fAInitialWord, fAFinalWord);
      }
    }
    return null;
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
