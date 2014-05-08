/**
 * 
 */
package edu.stanford.nlp.mt.decoder.feat.base;

import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.lm.NNLM;
import edu.stanford.nlp.mt.pt.ConcreteRule;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.TokenUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;

/**
 * Abstract class for an NNLM featurizer.
 * 
 * @author Thang Luong
 *
 */
public abstract class NNLMFeaturizer  extends DerivationFeaturizer<IString, String> implements
  RuleFeaturizer<IString, String> {
  protected String DEFAULT_FEATURE_NAME;
  
  protected final IString startToken;
  protected final IString endToken;

  protected String nnlmFile;
  protected int cacheSize;
  protected String featureName;
  protected NNLM nnlm;
  protected Sequence<IString> srcSent;
  protected int tgtOrder;
  

  public String helpMessage(){
    return featureName + "Featurizer(nnlm=<string>,cache=<int>,id=<string>). kenlm is optional, for back-off LM.";
  }
  
  public NNLMFeaturizer(String...args) {
    Properties options = FeatureUtils.argsToProperties(args);
    this.nnlmFile = PropertiesUtils.getString(options, "nnlm", null);
    this.cacheSize = PropertiesUtils.getInt(options, "cache", 0);
    this.featureName = PropertiesUtils.getString(options, "id", DEFAULT_FEATURE_NAME);

    if(nnlmFile==null || featureName==null) {
      throw new RuntimeException(
          "At least 2 arguments are needed: nnlm and id. " + helpMessage());
    }
    System.err.println("# " + DEFAULT_FEATURE_NAME + ": nnlmFile=" + featureName + ", cacheSize=" + cacheSize);
    
    this.startToken = TokenUtils.START_TOKEN;
    this.endToken = TokenUtils.END_TOKEN;
  }
  
  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = Generics.newLinkedList();
    features.add(new FeatureValue<String>(featureName, 0.0));
    return features;
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString,String>> options, Sequence<IString> foreign) {
    this.srcSent = foreign;
  }

  @Override
  public void initialize() {}

  @Override
  public boolean isolationScoreOnly() {
    return true;
  }
}
