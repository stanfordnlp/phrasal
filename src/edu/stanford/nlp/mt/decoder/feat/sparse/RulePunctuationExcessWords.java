package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.TokenUtils;

/**
 * Rule punctuation excess word features.
 * 
 * Fires if one side is a single-token punctuation with a mismatch on the other side.
 * 
 * @author Joern Wuebker
 *
 */
public class RulePunctuationExcessWords implements RuleFeaturizer<IString, String> {

  public static final String FEATURE_PREFIX = "PNEXWD";
  public static final String INCONSISTENT_PUNCTUATION = "INCONSISTENT";
  public static final int MAX_EXCESS = 3;

  /**
   * Constructor.
   * 
   * @param args
   */
  public RulePunctuationExcessWords(String...args) {}

  @Override
  public void initialize() {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    if(f.sourcePhrase.size() == 0 || f.targetPhrase.size() == 0) return null;
    
    IString srcWord = f.sourcePhrase.get(0);
    IString tgtWord = f.targetPhrase.get(0);
    
    boolean isSrcPunct = (f.sourcePhrase.size() == 1 && TokenUtils.isPunctuation(srcWord.toString()));
    boolean isTgtPunct = (f.targetPhrase.size() == 1 && TokenUtils.isPunctuation(tgtWord.toString()));

    if(isSrcPunct && isTgtPunct) {
      if(srcWord.equals(tgtWord)) return null;
      else return Collections.singletonList(new FeatureValue<>(FEATURE_PREFIX + ":" + INCONSISTENT_PUNCTUATION, 1.0));
    }
    
    boolean hasPunct = false;
    boolean samePunct = false;
    
    IString word = srcWord;
    Sequence<IString> phrase = f.targetPhrase;
    
    if(isTgtPunct) {
      word = tgtWord;
      phrase = f.sourcePhrase;
    }

    for(IString token : phrase) {
      if(TokenUtils.isPunctuation(token.toString())) {
        hasPunct = true;
        if(word.equals(token)) samePunct = true;
      }
    }
    
    int numExcessWords = phrase.size();
    if(hasPunct) numExcessWords--;
    numExcessWords = Math.max(MAX_EXCESS, numExcessWords);
    
    String featureName = FEATURE_PREFIX + ":" +
                         (isSrcPunct ? "SRC:" : "TGT:") + 
                         (samePunct ? "" : INCONSISTENT_PUNCTUATION + ":") 
                         + numExcessWords;
    
    return Collections.singletonList(new FeatureValue<>(featureName, 1.0));
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
