package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.lm.LMState;
import edu.stanford.nlp.mt.lm.LanguageModel;
import edu.stanford.nlp.mt.lm.LanguageModelFactory;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.util.SimpleSequence;
import edu.stanford.nlp.mt.util.TargetClassMap;
import edu.stanford.nlp.util.PropertiesUtils;


/**
 * Featurizer for n-gram dependency language models.
 * 
 * @author Sebastian Schuster
 */
public class BBNDependencyLanguageModelFeaturizer extends AbstractDependencyLanguageModelFeaturizer {

  private static LanguageModel<IString> leftLM ;
  private static LanguageModel<IString> rightLM;
  private static LanguageModel<IString> rootLM;
  
  public BBNDependencyLanguageModelFeaturizer() {
  
  }
  
  public BBNDependencyLanguageModelFeaturizer(String...args) throws IOException {
    Properties options = FeatureUtils.argsToProperties(args);
    if (!options.containsKey("leftLM")) {
        throw new RuntimeException(
              this.getClass().getName() + ": ERROR No left dependency LM file was specified!");
    }

    if (!options.containsKey("rightLM")) {
      throw new RuntimeException(
            this.getClass().getName() + ": ERROR No right dependency LM file was specified!");
    }
    
    if (!options.containsKey("rootLM")) {
      throw new RuntimeException(
            this.getClass().getName() + ": ERROR No root dependency LM file was specified!");
    }
    
    if (!options.containsKey("parses")) {
      throw new RuntimeException(
            this.getClass().getName() + ": ERROR No dependency parses file was specified!");
    }
    
    this.useClasses = PropertiesUtils.getBool(options, "classBased", false);
    this.useFragPenalty = PropertiesUtils.getBool(options, "fragPenalty", false);
    this.targetClassMap = useClasses ? TargetClassMap.getInstance() : null;
    
     
    
    assert leftLM == null;
    leftLM = LanguageModelFactory.load(options.getProperty("leftLM"));
    assert rightLM == null;
    rightLM = LanguageModelFactory.load(options.getProperty("rightLM"));
    assert rootLM == null;
    rootLM = LanguageModelFactory.load(options.getProperty("rootLM"));

    loadDependencies(options.getProperty("parses"));

  }

  public void scoreFrag(List<FeatureValue<String>> features, IString token, boolean scoreEmptyChildren) {
    String str = token.word() + FRAG_SUFFIX;
    Sequence<IString> seq = new SimpleSequence<IString>(new IString(str));
    seq = Sequences.wrapStartEnd(seq, rootLM.getStartToken(), rootLM.getEndToken());
    double rootScore = rootLM.score(seq, 1, null).getScore();
    features.add(new FeatureValue<String>(FEAT_NAME, rootScore));
    features.add(new FeatureValue<String>(FEAT_NAME_WORD_PENALTY, -1.0));
    if (this.useFragPenalty)
      features.add(new FeatureValue<String>(FEAT_NAME_FRAG_PENALTY, -1.0));

    if (scoreEmptyChildren) {
      String headStr = token.word() + HEAD_SUFFIX;
      Sequence<IString> childSeq = new SimpleSequence<IString>(new IString(headStr));
      childSeq = Sequences.wrapStartEnd(childSeq, rootLM.getStartToken(), rootLM.getEndToken());
      double leftScore = leftLM.score(childSeq, 2, null).getScore();
      double rightScore = rightLM.score(childSeq, 2, null).getScore();
      features.add(new FeatureValue<String>(FEAT_NAME, leftScore));
      features.add(new FeatureValue<String>(FEAT_NAME, rightScore));
    }
  }

  public void scoreRight(List<FeatureValue<String>> features, IString token, DepLMSubState subState) {
    Sequence<IString> seq = new SimpleSequence<IString>(token);
    int start = 0;
    if (subState.getRightLMState() == null) {
      seq = Sequences.wrapStart(seq, new IString(subState.getHeadToken().word() + HEAD_SUFFIX));
      seq = Sequences.wrapStart(seq, rightLM.getStartToken());
      start = 2;
    }
    LMState lmState = rightLM.score(seq, start, subState.getRightLMState());
    double rightScore = lmState.getScore();
    subState.setRightLMState(lmState);
    features.add(new FeatureValue<String>(FEAT_NAME, rightScore));
    if ( ! token.equals(rightLM.getEndToken()))
        features.add(new FeatureValue<String>(FEAT_NAME_WORD_PENALTY, -1.0));
  }
  
  public void scoreRoot(List<FeatureValue<String>> features, IString token) {
    String str = token.word() + ROOT_SUFFIX;
    Sequence<IString> seq = new SimpleSequence<IString>(new IString(str));
    seq = Sequences.wrapStartEnd(seq, rootLM.getStartToken(), rootLM.getEndToken());
    double rootScore = rootLM.score(seq, 1, null).getScore();
    features.add(new FeatureValue<String>(FEAT_NAME, rootScore));
    features.add(new FeatureValue<String>(FEAT_NAME_WORD_PENALTY, -1.0));
  }
  
  public void scoreLeft(List<FeatureValue<String>> features, IString token, DepLMSubState subState) {
    Collections.reverse(subState.getLeftChildren());
    Sequence<IString> seq = new SimpleSequence<IString>(subState.getLeftChildren());
    seq = Sequences.wrapStart(seq, new IString(token.word() + "<HEAD>"));
    seq = Sequences.wrapStartEnd(seq, leftLM.getStartToken(), leftLM.getEndToken());
    double leftScore = leftLM.score(seq, 2, null).getScore();
    features.add(new FeatureValue<String>(FEAT_NAME, leftScore));
    features.add(new FeatureValue<String>(FEAT_NAME_WORD_PENALTY, -1.0 * subState.getLeftChildren().size()));
    subState.getLeftChildren().clear();
  }

  @Override
  public void scoreRightEnd(List<FeatureValue<String>> features,
      DepLMSubState subState) {
    scoreRight(features, rightLM.getEndToken(), subState);
  }

  @Override
  public void scoreUnaligned(List<FeatureValue<String>> features, IString token) {
    scoreFrag(features, token, true);
  }
  
}
