package edu.stanford.nlp.mt.decoder.efeat;

import static java.lang.System.*;
import java.util.*;
import java.io.*;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FastFeaturizableHash;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IBMModel1;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;
import edu.stanford.nlp.util.Index;

/**
 * 
 * @author danielcer
 * 
 */
public class IBMModel1TargetGivenSource implements
    IncrementalFeaturizer<IString, String>,
    IsolatedPhraseFeaturizer<IString, String> {
  public static String FEATURE_PREFIX = "IBM1TGS";
  public final String featureName;

  FastFeaturizableHash<IBMModel1.PartialTargetFeatureState> h;
  Map<Sequence<IString>, IBMModel1.PhrasePrecomputePTarget> precomputeMap;

  IBMModel1.PartialTargetFeatureState basePTFS;
  final IBMModel1 ibmModel1;
  final boolean fullModel1;
  static final boolean DEFAULT_FULL_MODEL1 = true;

  /**
   * 
   * @throws IOException
   */
  public IBMModel1TargetGivenSource(String... args) throws IOException {
    String filenameTargetSourceModel = args[0];
    fullModel1 = (args.length == 1 ? DEFAULT_FULL_MODEL1 : Boolean
        .parseBoolean(args[1]));
    err.printf("IBMModel1TargetGivenSource - file: %s fullModel1: %b\n",
        filenameTargetSourceModel, fullModel1);
    ibmModel1 = IBMModel1.load(filenameTargetSourceModel);
    featureName = String.format("%s:%s", FEATURE_PREFIX, (fullModel1 ? "full"
        : "tmo"));
  }

  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    IBMModel1.PartialTargetFeatureState priorPtfs = h.get(f.prior);
    if (priorPtfs == null)
      priorPtfs = basePTFS;

    IBMModel1.PartialTargetFeatureState ptfs = priorPtfs;

    IBMModel1.PhrasePrecomputePTarget precompute = precomputeMap
        .get(f.targetPhrase);

    ptfs = ptfs.appendPhrasePrecompute(precompute);

    h.put(f, ptfs);
    if (fullModel1) {
      return new FeatureValue<String>(featureName, ptfs.score()
          - (priorPtfs != basePTFS ? priorPtfs.score() : 0));
    } else {
      return new FeatureValue<String>(featureName, ptfs.scoreTMOnly()
          - (priorPtfs != basePTFS ? priorPtfs.scoreTMOnly() : 0));
    }
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteTranslationOption<IString,String>> options, Sequence<IString> foreign, Index<String> featureIndex) {
    h = new FastFeaturizableHash<IBMModel1.PartialTargetFeatureState>();
    basePTFS = ibmModel1.partialTargetFeatureState(foreign);
    precomputeMap = new HashMap<Sequence<IString>, IBMModel1.PhrasePrecomputePTarget>();
    for (ConcreteTranslationOption<IString,String> option : options) {
      if (precomputeMap.containsKey(option.abstractOption.target))
        continue;
      precomputeMap.put(option.abstractOption.target, ibmModel1
          .phrasePrecomputePTarget(option.abstractOption.target, foreign));
    }
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(
      Featurizable<IString, String> f) {
    return null;
  }

  @Override
  public FeatureValue<String> phraseFeaturize(Featurizable<IString, String> f) {
    if (fullModel1)
      return null;
    return new FeatureValue<String>(featureName, ibmModel1.scoreTMOnly(
        f.sourceSentence, f.targetPhrase));
  }

  @Override
  public List<FeatureValue<String>> phraseListFeaturize(
      Featurizable<IString, String> f) {
    return null;
  }

  public void reset() {
  }
  
  @Override
  public void initialize(Index<String> featureIndex) {
  }
}
