package edu.stanford.nlp.mt.decoder.efeat;

import java.util.List;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.NeedsInternalAlignments;
import edu.stanford.nlp.mt.decoder.feat.CombinationFeaturizer;

/**
 * Adds features to the MT system based on the combination of source
 * side POS tags and the target side words.  For example, dog in
 * English is tagged as NN, and in Chinese would be 狗, so the
 * resulting feature would be TAGGER-NN-狗.
 *
 *@author John Bauer
 */
public class SourceSideTaggerFeaturizer implements CombinationFeaturizer<IString, String>, NeedsInternalAlignments {
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

  /**
   * Initialize on a new translation.  Will run the tagger over the source side text.
   */
  @Override
  public void initialize(int sourceInputId,
                         List<ConcreteRule<IString, String>> options, Sequence<IString> foreign, Index<String> featureIndex) {
    List<Word> sentence = Generics.newArrayList();
    for (IString word : foreign) {
      sentence.add(new Word(word.toString()));
    }
    tagged = tagger.tagSentence(sentence);
  }

  /**
   * Return a set of features for the tagged sentence.
   * Each feature will be of the form TAGGER-sourcetag-targetword
   */  
  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
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
