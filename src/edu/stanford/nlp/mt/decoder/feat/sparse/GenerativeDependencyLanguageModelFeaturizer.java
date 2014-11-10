package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.lm.LanguageModel;
import edu.stanford.nlp.mt.lm.LanguageModelFactory;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.SimpleSequence;
import edu.stanford.nlp.mt.util.TargetClassMap;
import edu.stanford.nlp.util.PropertiesUtils;


/**
 * Featurizer for n-gram dependency language models.
 * 
 * @author Sebastian Schuster
 */
public class GenerativeDependencyLanguageModelFeaturizer extends AbstractDependencyLanguageModelFeaturizer {

  private static LanguageModel<IString> depLM ;
  
  private static String HEAD_SUFFIX = "<HEAD>";
  private static String SIBLING_SUFFIX = "<SIB>";



  private static final IString ROOT_TOKEN = new IString("<ROOT>");
  private static final IString FRAG_TOKEN = new IString("<FRAG>");
  private static final IString START_TOKEN = new IString("<START>");
  private static final IString END_TOKEN = new IString("<END>");

  private static final IString ROOT_DIR_TOKEN = new IString("0<DIR>");
  private static final IString LEFT_DIR_TOKEN = new IString("1<DIR>");
  private static final IString RIGHT_DIR_TOKEN = new IString("2<DIR>");
  
  private boolean disableEndToken = false;
  
  
  public GenerativeDependencyLanguageModelFeaturizer() {
  
  }
  
  public GenerativeDependencyLanguageModelFeaturizer(String...args) throws IOException {
    Properties options = FeatureUtils.argsToProperties(args);
    if (!options.containsKey("depLM")) {
        throw new RuntimeException(
              this.getClass().getName() + ": ERROR No dependency LM file was specified!");
    }
    
    if (!options.containsKey("parses")) {
      throw new RuntimeException(
            this.getClass().getName() + ": ERROR No dependency parses file was specified!");
    }
    
    
    
    this.useClasses = PropertiesUtils.getBool(options, "classBased", false);
    this.disableEndToken = PropertiesUtils.getBool(options, "disableEndToken", false);
    this.disableTransitivity = PropertiesUtils.getBool(options, "disableTransitivity", false);
    this.useFragPenalty = PropertiesUtils.getBool(options, "fragPenalty", false);
    this.targetClassMap = useClasses ? TargetClassMap.getInstance() : null;
    
    assert depLM == null;
    depLM = LanguageModelFactory.load(options.getProperty("depLM"));
   
    loadDependencies(options.getProperty("parses"));
    

    System.err.println("Loaded DepLM: " + options.getProperty("depLM"));
    System.err.println("Options:");
    if (this.useClasses) System.err.println("useClasses");
    if (this.disableEndToken) System.err.println("disableEndToken");
    if (this.useFragPenalty) System.err.println("fragPenalty");
    if (this.disableTransitivity) System.err.println("disableTransitivity"); 
  }
  
  private static double score(IString child, IString sibling, IString head, IString direction) {
    head = new IString(head + HEAD_SUFFIX);
    sibling = new IString(sibling + SIBLING_SUFFIX);
    List<IString> tokens = new LinkedList<IString>();
    tokens.add(sibling);
    tokens.add(direction);
    tokens.add(head);
    tokens.add(child);
    Sequence<IString> seq = new SimpleSequence<IString>(tokens);
    double score = depLM.score(seq, 3, null).getScore();
    return score;
  }

  @Override
  public void scoreFrag(List<FeatureValue<String>> features, List<Double> lmScores, IString token, int tokenIndex, boolean scoreEmptyChildren) {
    
    double score = score(token, START_TOKEN, FRAG_TOKEN, ROOT_DIR_TOKEN);
    if ( ! this.disableEndToken)
      score += score(END_TOKEN, token, FRAG_TOKEN, ROOT_DIR_TOKEN);
    
    lmScores.add(score);
    features.add(new FeatureValue<String>(FEAT_NAME_WORD_PENALTY, 1.0));
    this.state.getProjectedDependencies().put(tokenIndex, -2);
    
    if (this.useFragPenalty)
      features.add(new FeatureValue<String>(FEAT_NAME_FRAG_PENALTY, 1.0));

    if ( ! this.disableEndToken && scoreEmptyChildren) {
      double leftScore = score(END_TOKEN, START_TOKEN, token, LEFT_DIR_TOKEN);
      double rightScore = score(END_TOKEN, START_TOKEN, token, RIGHT_DIR_TOKEN);
      lmScores.add(leftScore);
      lmScores.add(rightScore);
    }
  }

  @Override
  public void scoreRight(List<FeatureValue<String>> features, List<Double> lmScores, IString token, int tokenIndex, DepLMSubState subState) {
    if (subState.getRightSibling() == null) {
      subState.setRightSibling(START_TOKEN);
    }
    double rightScore = score(token, subState.getRightSibling(), subState.getHeadToken(), RIGHT_DIR_TOKEN);
    subState.setRightSibling(token);
    lmScores.add(rightScore);
    features.add(new FeatureValue<String>(FEAT_NAME_WORD_PENALTY, 1.0));
    /* Set tokenIndex to -1 to disable adding another dependency. Used by scoreRightEnd. */
    if (tokenIndex > -1) 
      this.state.getProjectedDependencies().put(tokenIndex, subState.getHeadTokenIndex());

  }
  
  @Override
  public void scoreRoot(List<FeatureValue<String>> features, List<Double> lmScores, IString token, int tokenIndex) {
    double score = score(token, START_TOKEN, ROOT_TOKEN, ROOT_DIR_TOKEN);
    if ( ! this.disableEndToken)
      score += score(END_TOKEN, token, ROOT_TOKEN, ROOT_DIR_TOKEN);
    
    
    this.state.getProjectedDependencies().put(tokenIndex, -1);

    lmScores.add(score);
    features.add(new FeatureValue<String>(FEAT_NAME_WORD_PENALTY, 1.0));
  }
  
  public void scoreLeft(List<FeatureValue<String>> features, List<Double> lmScores, IString headToken, int headTokenIndex, DepLMSubState subState) {
    
    Collections.reverse(subState.getLeftChildren());

    int leftChildrenCount = subState.getLeftChildren().size();

    double leftScore = 0.0;

    for (int i = 0; i <= leftChildrenCount; i++) {
      IString depToken1 =  (i > 0) ?  subState.getLeftChildren().get(i-1) : START_TOKEN;
      IString depToken2 =  (i < leftChildrenCount) ?  subState.getLeftChildren().get(i) : END_TOKEN;      
      if ( i < leftChildrenCount ||  ! this.disableEndToken) {
        leftScore += score(depToken2, depToken1, headToken, LEFT_DIR_TOKEN);
        /* Add dependencies to the state */
        if (i < leftChildrenCount) {
          Integer depToken2Index = subState.getLeftChildrenIndices().get(i);
          this.state.getProjectedDependencies().put(depToken2Index, headTokenIndex);
        }
      }
    }
    
    lmScores.add(leftScore);
    features.add(new FeatureValue<String>(FEAT_NAME_WORD_PENALTY, 1.0 * (subState.getLeftChildren().size())));
    subState.getLeftChildren().clear();
  }

  @Override
  public void scoreRightEnd(List<FeatureValue<String>> features, List<Double> lmScores, DepLMSubState subState) {
    if ( ! this.disableEndToken)
      scoreRight(features, lmScores, END_TOKEN, -1, subState);
  }

  @Override
  public void scoreUnaligned(List<FeatureValue<String>> features, List<Double> lmScores, IString token, int tokenIndex) {
    scoreFrag(features, lmScores, token, tokenIndex, true);
  }
  
}
