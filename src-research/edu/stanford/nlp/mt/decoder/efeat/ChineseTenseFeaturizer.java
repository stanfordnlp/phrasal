package edu.stanford.nlp.mt.decoder.efeat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.NeedsInternalAlignments;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.process.DistSimClassifier;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

/**
 * 
 * Checks for explicit temporal words (from a list) in the Chinese source sentence 
 * and tensed items in the English target derivation to push things towards proper tensing.
 * (i.e., if we see '明天' in the sentence we're probably talking about something in the future, etc.)
 * 
 * @author robvoigt
 * 
 */
public class ChineseTenseFeaturizer implements
    DerivationFeaturizer<IString, String>, NeedsInternalAlignments {

  Set<String> zhTenseWords;
  List<String> foundTemporals; // source-side sentence tense-signaling words
  
  // hacky - taken from a list of the most common verbs in a POS-tagged corpus, plus a some common time-signaling adverbs   
  Set<String> enTenseWords = new HashSet<String>(Arrays.asList(
      "is","am","are","was","will","had","has","have","been","be",
      "do","did","say","said","says","were","\'s","\'re","get",
      "make","does","fell","rose","sell","buy","take","go","made",
      "n\'t","as","when","now","still","recently","earlier","already",
      "previously","often","again","currently","never","always",
      "once","soon","first","usually","generally","earlier","before",
      "after","meanwhile")); 

  public static final String ZH_TENSE_FILE = "/user/robvoigt/scr/tense/timewords"; // simply a list of tense words
  public static final String FEATURE_NAME = "ChineseTense";

  public ChineseTenseFeaturizer() {
    zhTenseWords = new HashSet<String>();
    for (String line : ObjectBank.getLineIterator(ZH_TENSE_FILE, "utf-8")) {
      zhTenseWords.add(line.replaceAll("\\s", ""));
    }
  }
  
  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString, String>> options, 
      Sequence<IString> foreign) {
    foundTemporals = new ArrayList<String>();
    for (IString word : foreign) {
      if (zhTenseWords.contains(word.toString())) {
        foundTemporals.add(word.toString());
      }
    }
  }

  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    
    if (f.sourcePhrase == null || f.targetPhrase == null) {
      return null;
    }

    Integer tgtLength = f.targetPhrase.size();

    for (int i = 0; i < tgtLength; ++i) {
      String tgtWord = f.targetPhrase.get(i).toString();
      if (enTenseWords.contains(tgtWord) || 
          tgtWord.endsWith("ing") || tgtWord.endsWith("ed")) { // also hacky - get gerunds and many past-tense verbs
        for (String srcWord : foundTemporals) {
          String feature = FEATURE_NAME + "-" + srcWord + "-" + tgtWord;
          features.add(new FeatureValue<String>(feature, 1.0));
        }
      }
    }
    
    return features;
  }
}

