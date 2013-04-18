package edu.stanford.nlp.mt.decoder.efeat;

import java.io.IOException;
import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FastFeaturizableHash;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IBMModel1;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;
import edu.stanford.nlp.util.Index;

/**
 * 
 * @author danielcer
 * 
 */
public class IBMModel1SourceGivenTarget implements
    IncrementalFeaturizer<IString, String> {
  public static String FEATURE_NAME = "IBM1SGT";
  FastFeaturizableHash<IBMModel1.PartialSourceFeatureState> h;
  IBMModel1.PartialSourceFeatureState basePSFS;
  final IBMModel1 ibmModel1;

  public IBMModel1SourceGivenTarget(String filenameSourceTargetModel)
      throws IOException {
    ibmModel1 = IBMModel1.load(filenameSourceTargetModel);
  }

  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    IBMModel1.PartialSourceFeatureState psfs = h.get(f.prior);
    if (psfs == null)
      psfs = basePSFS;

    for (IString targetWord : f.targetPhrase) {
      psfs = psfs.appendSourceWord(targetWord);
    }
    h.put(f, psfs);

    return new FeatureValue<String>(FEATURE_NAME, psfs.score());
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteTranslationOption<IString,String>> options, Sequence<IString> foreign, Index<String> featureIndex) {
    h = new FastFeaturizableHash<IBMModel1.PartialSourceFeatureState>();
    basePSFS = ibmModel1.partialSourceFeatureState(foreign);
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(
      Featurizable<IString, String> f) {
    return null;
  }

  public void reset() {
  }
}
