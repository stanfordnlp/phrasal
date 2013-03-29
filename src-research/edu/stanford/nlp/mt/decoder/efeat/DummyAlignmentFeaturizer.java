package edu.stanford.nlp.mt.decoder.efeat;

import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.UnknownWordPhraseGenerator;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.AlignmentFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;
import edu.stanford.nlp.util.Index;

/**
 * Does nothing expect checking that each abstract option has an alignment
 * available.
 * 
 * @author Michel Galley
 */
public class DummyAlignmentFeaturizer implements
    IncrementalFeaturizer<IString, String>, AlignmentFeaturizer {

  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    if (f.option.phraseTableName
        .equals(UnknownWordPhraseGenerator.PHRASE_TABLE_NAMES))
      // Skip f.option if not in the phrase table (i.e., unknown input words)
      return null;
    // Check if phrase internal alignment is available:
    if (!f.option.abstractOption.alignment.hasAlignment()) {
      throw new RuntimeException(String.format(
          "Alignment missing for phrase:\n"
              + "Featurizer: %s\nOption: %s\nAbstract option: %s\n", f,
          f.option, f.option.abstractOption));
    }
    return null;
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

  public void reset() {
  }
}
