package edu.stanford.nlp.mt.decoder.efeat;

import java.util.HashMap;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.DependencyLM;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.LanguageModel;
import edu.stanford.nlp.mt.base.RawSequence;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.annotators.Annotator;
import edu.stanford.nlp.mt.decoder.annotators.TargetDependencyAnnotator;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;
import edu.stanford.nlp.mt.parser.LinkedStack;
import edu.stanford.nlp.trees.TypedDependency;

/**
 * 
 * @author Heeyoung Lee
 * 
 * @param <TK>
 */
public class DepLMFeaturizer implements IncrementalFeaturizer<IString, String> {

  public static final String DEBUG_PROPERTY = "DepLMFeaturizerDebug";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
  public static final String FEATURE_NAME = "DepLM";
  private final HashMap<Featurizable<IString, String>, Double> depLMScoreHistory = new HashMap<Featurizable<IString, String>, Double>();
  public final LanguageModel<IString> lm;

  public DepLMFeaturizer(){
    lm = new DependencyLM();
  }

  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> featurizable) {
    double depLMScore = getScore(featurizable);
    return new FeatureValue<String>(FEATURE_NAME, depLMScore);
  }

  private double getScore(Featurizable<IString, String> featurizable) {
    double previousScore = getPreviousScore(featurizable);
    double additionalScore = getAdditionalScore(featurizable);
    double depLMSumScore = previousScore + additionalScore;
    depLMScoreHistory.put(featurizable, depLMSumScore);
    return depLMSumScore;
  }

  private double getPreviousScore(Featurizable<IString, String> featurizable) {
    Double previous = depLMScoreHistory.get(featurizable.prior);
    if(previous==null) return 0;
    else return previous;
  }

  @SuppressWarnings("unchecked")
  private double getAdditionalScore(Featurizable<IString, String> featurizable) {
    double additionalScore = 0;
    LinkedStack<TypedDependency> previousDeps = null;
    LinkedStack<TypedDependency> currentDeps = null;
    for(Annotator<IString> annotator : featurizable.hyp.preceedingHyp.annotators){
      if(annotator.getClass().getName().equals("edu.stanford.nlp.mt.decoder.annotators.TargetDependencyAnnotator")) {
        previousDeps = ((TargetDependencyAnnotator)annotator).struct.getDependencies();
      }
    }
    for(Annotator<IString> annotator : featurizable.hyp.annotators){
      if(annotator.getClass().getName().equals("edu.stanford.nlp.mt.decoder.annotators.TargetDependencyAnnotator")) {
        currentDeps = ((TargetDependencyAnnotator)annotator).struct.getDependencies();
      }
    }
    Object[] newDeps = currentDeps.peekN(currentDeps.size() - previousDeps.size());
    for(Object dep : newDeps){
      TypedDependency dependency = (TypedDependency) dep;
      additionalScore += getDepScore(dependency);
    }
    return additionalScore;
  }

  private double getDepScore(TypedDependency dependency) {
    IString[] str = new IString[2];
    str[0] = new IString(dependency.gov().label().get(TextAnnotation.class));
    str[1] = new IString(dependency.dep().label().get(TextAnnotation.class));

    Sequence<IString> depNgram = new RawSequence<IString>(str);

    return lm.score(depNgram);
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) {
    return null;
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<IString>> options,
      Sequence<IString> foreign) {
  }

  @Override
  public void reset() {
  }
}
