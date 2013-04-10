package edu.stanford.nlp.mt.decoder.efeat;

import java.util.List;

import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;

/**
 * Note: This should only be used for phrases extracted using one-to-one
 * alignments, i.e., using the Berkeley "intersection."
 * 
 * @author Spence Green
 * 
 */
public class AlignPenalty implements IncrementalFeaturizer<IString, String> {

  public static final String FEATURE_NAME = "AlignPenalty";

  private static Pair<Integer, Integer> internalFeaturize(
      Featurizable<IString, String> f) {
    final int tOptLen = f.targetPhrase.size();

    int numAlignments = 0;
    int numNullAlignments = 0;
    if (f.option.abstractOption.alignment.hasAlignment()) {
      for (int i = 0; i < tOptLen; i++) {

        final int[] sIndices = f.option.abstractOption.alignment.e2f(i);
        if (sIndices == null)
          numNullAlignments++;
        else if (sIndices.length != 1)
          throw new RuntimeException(
              String
                  .format(
                      "Many-to-one alignment...Shouldn't happen with intersect heuristic (sIndices %d)",
                      sIndices.length));
        else
          numAlignments++;
      }
    } else
      numNullAlignments += f.targetPhrase.size();

    return new Pair<Integer, Integer>(numAlignments, numNullAlignments);
  }

  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {

    Pair<Integer, Integer> featVals = internalFeaturize(f);

    return new FeatureValue<String>(FEATURE_NAME, -1.0 * featVals.first());
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(
      Featurizable<IString, String> f) {
    return null;
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<IString,String>> options,
      Sequence<IString> foreign, Index<String> featureIndex) {
  }

  @Override
  public void reset() {
  }

}
