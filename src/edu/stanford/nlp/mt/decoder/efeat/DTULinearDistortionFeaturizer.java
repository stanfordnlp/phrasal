package edu.stanford.nlp.mt.decoder.efeat;

import java.util.List;
import java.util.ArrayList;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.StatefulFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.LinearFutureCostFeaturizer;
import edu.stanford.nlp.mt.train.DTUPhraseExtractor;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.DTUFeaturizable;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption.LinearDistortionType;

/**
 * @author Michel Galley
 */
@SuppressWarnings("unused")
public class DTULinearDistortionFeaturizer extends StatefulFeaturizer<IString, String> {

  public static final String EXP_PROPERTY = "ExpDTU";
  public static final boolean EXP = Boolean.parseBoolean(System.getProperty(EXP_PROPERTY, "false"));

	public static final String DEBUG_PROPERTY = "DebugDTULinearDistortionFeaturizer";
	public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
	public static final String FEATURE_NAME = "LinearDistortion";
  public static final String GAP_FEATURE_NAME = "GapLinearDistortion";

  public static final String EOS_DISTORTION = "EOSDistortion";
  public static final String NO_GAP_AT_END = "NoGapAtEnd";
  public static final int GAP_AT_END_COST = 100;

  public static final float DEFAULT_FUTURE_COST_DELAY = Float.parseFloat(System.getProperty("dtuFutureCostDelay","1f"));

  private final LinearDistortionType[] featureTypes;
  private final String[] featureNames;

  public final float futureCostDelay;

  private boolean addEOS;
  private boolean noGapAtEnd;

  @SuppressWarnings("unused")
  public DTULinearDistortionFeaturizer() {
    featureTypes = new LinearDistortionType[0];
    featureNames = new String[0];
    addEOS = true;
    noGapAtEnd = false;
    futureCostDelay = DEFAULT_FUTURE_COST_DELAY;
    System.err.println("Future cost delay: "+futureCostDelay);
  }

  @SuppressWarnings("unused")
  public DTULinearDistortionFeaturizer(String... args) {
    List<String> featureTypesL = new ArrayList<String>();
    for (String arg : args) {
      if (arg.equals(EOS_DISTORTION)) {
        addEOS = true;
        System.err.println("addEOS: true");
      } else if (arg.equals(NO_GAP_AT_END)) {
        noGapAtEnd = true;
        System.err.println("noGapAtEnd: true");
      } else {
        featureTypesL.add(arg);
      }
    }
    int sz = featureTypesL.size();
    featureTypes = new LinearDistortionType[sz];
    featureNames = new String[sz];
    for (int i=0; i<sz; ++i) {
      featureTypes[i] = LinearDistortionType.valueOf(args[i]);
      featureNames[i] = FEATURE_NAME+":"+featureTypes[i].name();
    }
    futureCostDelay = DEFAULT_FUTURE_COST_DELAY;
    System.err.println("Future cost delay: "+futureCostDelay);
  }

  @Override
	public FeatureValue<String> featurize(Featurizable<IString,String> f) {
    return null;
  }

	@Override
	public List<FeatureValue<String>> listFeaturize(Featurizable<IString,String> f) {

    if (f instanceof DTUFeaturizable)
      if (((DTUFeaturizable)f).targetOnly) {
        return null;
      }

    ///////////////////////////////////////////
    // (1) Source gaps:
    ///////////////////////////////////////////

    //System.err.printf("done: %s size: %s\n", f.done, f.untranslatedTokens);
    //if(!f.done) {
    //  assert(f.option.foreignCoverage.cardinality() < f.foreignSentence.size());
    //}

    List<FeatureValue<String>> list = new ArrayList<FeatureValue<String>>(featureTypes.length+1);
    int span = f.option.foreignCoverage.length()-f.option.foreignCoverage.nextSetBit(0);
    int totalSz = 0;
    for(IString fw : f.foreignPhrase)
      if(fw.id != DTUPhraseExtractor.GAP_STR.id)
        ++totalSz;
    int gapSz = span-totalSz;

    if(noGapAtEnd && !f.done) {
      //if(f.option.foreignCoverage.length() == f.foreignSentence.size()) {
        /*
        System.err.println("hyp span: "+f.hyp.foreignCoverage);
        System.err.println("opt span: "+f.option.foreignCoverage);
        System.err.println("opt text: "+f.option.abstractOption.foreign);
        System.err.println("opt text: "+f.option.abstractOption.translation);
        System.err.println("len: "+f.option.foreignCoverage.length());
        System.err.println("size: "+f.foreignSentence.size());
         */
        if(gapSz > 0) {
          if(f.option.foreignCoverage.length() == f.foreignSentence.size()) {
            assert(!f.done);
            gapSz += GAP_AT_END_COST;
            //System.err.println("hyp span: "+f.hyp.foreignCoverage);
            //System.err.println("opt span: "+f.option.foreignCoverage);
            //System.err.println("opt text: "+f.option.abstractOption.foreign);
            //System.err.println("opt text: "+f.option.abstractOption.translation);
          }
        }
        //System.err.println("gap cost: "+gapSz);
      //}
    }
    list.add(new FeatureValue<String>(GAP_FEATURE_NAME, -1.0*gapSz));

    ///////////////////////////////////////////
    // (2) Standard linear distortion features:
    ///////////////////////////////////////////

    if (featureTypes.length == 0) {
      int linearDistortion = f.linearDistortion;
      if (addEOS)
        linearDistortion += LinearFutureCostFeaturizer.getEOSDistortion(f);
      float oldFutureCost = f.prior != null ? ((Float) f.prior.getState(this)) : 0.0f;
      float futureCost;
      if(f.done) {
        futureCost = 0.0f;
      } else {
        futureCost = (1.0f-futureCostDelay)*LinearFutureCostFeaturizer.futureCost(f) + futureCostDelay*oldFutureCost;
        f.setState(this, futureCost);
      }
      float deltaCost = futureCost - oldFutureCost;
      list.add(new FeatureValue<String>(FEATURE_NAME, -1.0*(linearDistortion+deltaCost)));
    } else {
      // Experimental code:
      assert (EXP);
      ConcreteTranslationOption<IString>
        priorOpt = (f.prior != null) ? f.prior.option : null,
        currentOpt = f.option;
      for (int i=0; i<featureTypes.length; ++i) {
        int linearDistortion = (priorOpt == null) ? f.option.foreignPos : priorOpt.linearDistortion(currentOpt, featureTypes[i]);
        if (addEOS)
          linearDistortion += LinearFutureCostFeaturizer.getEOSDistortion(f);
        list.add(new FeatureValue<String>(featureNames[i], -1.0*linearDistortion));
      }
    }

    return list;
  }

  @Override
	public void initialize(List<ConcreteTranslationOption<IString>> options,
			Sequence<IString> foreign) {
	}

	public void reset() { }
}
