package edu.stanford.nlp.mt.decoder.efeat;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.DependencyLM;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
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
  public static final String FEATURE_NAME_UNK = "UnkDepCnt";
  public static final String FEATURE_NAME_POSTAGLM = "DepPOSTagLM";

  public final boolean USE_WORDLM;
  public final boolean USE_UNK_DEP;
  public final boolean USE_POSTAGLM;

  public final DependencyLM lm;

  public DepLMFeaturizer(String... args){
    lm = new DependencyLM();
    if(args.length < 3) throw new RuntimeException("Wrong number of arguments: "+args.length);
    USE_WORDLM = true;
    USE_UNK_DEP = true;
    USE_POSTAGLM = true;
  }

  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> featurizable) {
    return null;
  }

  private double getDepWordsLMScore(TypedDependency dependency) {
    IString[] str = new IString[2];
    str[0] = new IString(dependency.gov().label().get(TextAnnotation.class));
    str[1] = new IString(dependency.dep().label().get(TextAnnotation.class));

    Sequence<IString> depNgram = new RawSequence<IString>(str);

    return lm.score(depNgram);
  }
  private double getDepPOSTagsLMScore(TypedDependency dependency) {
    IString[] str = new IString[2];
    str[0] = new IString(dependency.gov().label().get(PartOfSpeechAnnotation.class));
    str[1] = new IString(dependency.dep().label().get(PartOfSpeechAnnotation.class));

    Sequence<IString> depNgram = new RawSequence<IString>(str);

    return lm.scorePOSLM(depNgram);
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> feats = new ArrayList<FeatureValue<String>>();

    double wordLMScore = 0;
    double posTagLMScore = 0;
    int unkCnt = 0;
    LinkedStack<TypedDependency> previousDeps = null;
    LinkedStack<TypedDependency> currentDeps = null;
    for(Annotator<IString> annotator : f.hyp.preceedingHyp.annotators){
      if(annotator.getClass().getName().equals("edu.stanford.nlp.mt.decoder.annotators.TargetDependencyAnnotator")) {
        previousDeps = ((TargetDependencyAnnotator)annotator).struct.getDependencies();
      }
    }
    for(Annotator<IString> annotator : f.hyp.annotators){
      if(annotator.getClass().getName().equals("edu.stanford.nlp.mt.decoder.annotators.TargetDependencyAnnotator")) {
        currentDeps = ((TargetDependencyAnnotator)annotator).struct.getDependencies();
      }
    }
    Object[] newDeps = currentDeps.peekN(currentDeps.size() - previousDeps.size());
    for(Object dep : newDeps){
      TypedDependency dependency = (TypedDependency) dep;
      double score = getDepWordsLMScore(dependency);
      wordLMScore += score;
      posTagLMScore += getDepPOSTagsLMScore(dependency);
      if(score==0) unkCnt++;
    }

    if(USE_WORDLM) feats.add(new FeatureValue<String>(FEATURE_NAME, wordLMScore));
    if(USE_POSTAGLM) feats.add(new FeatureValue<String>(FEATURE_NAME_POSTAGLM, posTagLMScore));
    if(USE_UNK_DEP) feats.add(new FeatureValue<String>(FEATURE_NAME_UNK, unkCnt));

    return feats;
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<IString>> options,
      Sequence<IString> foreign) {
  }

  @Override
  public void reset() {
  }
}
