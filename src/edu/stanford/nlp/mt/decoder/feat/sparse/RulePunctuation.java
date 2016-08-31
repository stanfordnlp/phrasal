package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.TokenUtils;

/**
 * Rule punctuation features.
 * 
 * @author Spence Green
 *
 */
public class RulePunctuation implements RuleFeaturizer<IString, String> {

  public static final String FEATURE_PREFIX = "RPN";
  public static final String INCONSISTENT = FEATURE_PREFIX + ":inconsistent";
  public static final String SRC_EXC = FEATURE_PREFIX + ":srcExc";
  public static final String TGT_EXC = FEATURE_PREFIX + ":tgtExc";
  
  private final boolean shape;
  private final boolean consistency;
  private final boolean excessWords;

  /**
   * Constructor.
   * 
   * @param args
   */
  public RulePunctuation(String...args) {
    Properties options = FeatureUtils.argsToProperties(args);
    this.shape = options.containsKey("shape");
    this.consistency = options.containsKey("consistency");
    this.excessWords = options.containsKey("excessWords");
  }
  
  public RulePunctuation() {
    this.shape = false;
    this.consistency = false;
    this.excessWords = false;
  }

  @Override
  public void initialize() {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    int numSourcePunc = 0;
    for (IString token : f.sourcePhrase)
      if (TokenUtils.isPunctuation(token.toString())) ++numSourcePunc;
    int numTargetPunc = 0;
    for (IString token : f.targetPhrase)
      if (TokenUtils.isPunctuation(token.toString())) ++numTargetPunc;
    final int puncDiff = Math.abs(numTargetPunc - numSourcePunc); 
    List<FeatureValue<String>> features = new ArrayList<>(2);
    if (puncDiff > 0) features.add(new FeatureValue<>(FEATURE_PREFIX, Math.log(puncDiff)));
    if (shape && (numSourcePunc > 0 || numTargetPunc > 0)) features.add(
        new FeatureValue<>(FEATURE_PREFIX + ":" + numSourcePunc + "-" + numTargetPunc, 1.0));
    if(consistency && (puncDiff == 0 || !checkConsistent(f.sourcePhrase, f.targetPhrase)) ) {
      features.add(new FeatureValue<>(INCONSISTENT, 1.0));
    }
    // only fires if one side contains only punctuation
    if(excessWords) {
      if(numSourcePunc == f.sourcePhrase.size()) {
        if(f.targetPhrase.size() > numTargetPunc)
          features.add(new FeatureValue<>(TGT_EXC, f.targetPhrase.size() - numTargetPunc));
      }
      else if(numTargetPunc == f.targetPhrase.size()) {
        features.add(new FeatureValue<>(SRC_EXC, f.sourcePhrase.size() - numSourcePunc));
      }
    }
    return features;
  }
  
  // returns true only if src and target phrase contain the same punctuation tokens in the same order
  private boolean checkConsistent(Sequence<IString> src, Sequence<IString> tgt) {
    int i = 0;
    for(IString token : src) {
      if(!TokenUtils.isPunctuation(token.toString())) continue;
      while(i < tgt.size() && !TokenUtils.isPunctuation(tgt.get(i).toString())) ++i;
      if(i == tgt.size() || !token.equals(tgt.get(i))) return false;
    }
    for(; i < tgt.size(); ++i) {
      if(TokenUtils.isPunctuation(tgt.get(i).toString())) return false;
    }
    
    return true;
  }

  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
