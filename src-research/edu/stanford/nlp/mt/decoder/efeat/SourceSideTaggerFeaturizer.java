package edu.stanford.nlp.mt.decoder.efeat;

import java.util.List;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.AlignmentFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;

/**
 * Adds features to the MT system based on the combination of source
 * side POS tags and the target side words.  For example, dog in
 * English is tagged as NN, and in Chinese would be 狗, so the
 * resulting feature would be TAGGER-NN-狗.
 *
 *@author John Bauer
 */
public class SourceSideTaggerFeaturizer implements IncrementalFeaturizer<IString, String>, AlignmentFeaturizer {
  /**
   * Tagger to use on the source side
   */
  MaxentTagger tagger;

  /**
   * Tagging results of the current sentence to translate
   */
  List<TaggedWord> tagged;

  /**
   * All features will start with this prefix
   */
  public static final String FEATURE_NAME = "TAGGER-";

  public SourceSideTaggerFeaturizer() {
    this(MaxentTagger.DEFAULT_NLP_GROUP_MODEL_PATH);
  }

  public SourceSideTaggerFeaturizer(String ... args) {
    tagger = new MaxentTagger(args[0]);
  }

  @Override
  public void reset() {}

  /**
   * Initialize on a new translation.  Will run the tagger over the source side text.
   */
  @Override
  public void initialize(List<ConcreteTranslationOption<IString, String>> options,
                         Sequence<IString> foreign, Index<String> featureIndex) {
    List<Word> sentence = Generics.newArrayList();
    for (IString word : foreign) {
      sentence.add(new Word(word.toString()));
    }
    tagged = tagger.tagSentence(sentence);
  }

  /**
   * We care about the features produced by the list of words, so
   * listFeaturize returns results and featurize does not.
   */
  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    return null;
  }

  /**
   * Return a set of features for the tagged sentence.
   * Each feature will be of the form TAGGER-sourcetag-targetword
   */  
  @Override
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newArrayList();
    
    for (int i = 0; i < tagged.size(); ++i) {
      if (f.s2tAlignmentIndex[i] == null) {
        // this word is not aligned to anything yet
        continue;
      }

      for (int j = 0; j < f.s2tAlignmentIndex[i].length; ++j) {
        int sourceIndex = i;
        int targetIndex = f.s2tAlignmentIndex[i][j];

        String sourceTag = tagged.get(sourceIndex).tag();
        String targetWord = f.targetPrefix.get(targetIndex).toString();
        String feature = FEATURE_NAME + sourceTag + "-" + targetWord;
        // no attempt to look for repeated features; 
        // the system will find and sum those for us
        features.add(new FeatureValue<String>(feature, 1.0));
      }
    }

    return features;
  }
}
