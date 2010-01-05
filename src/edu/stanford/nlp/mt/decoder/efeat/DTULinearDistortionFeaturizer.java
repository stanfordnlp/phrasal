package edu.stanford.nlp.mt.decoder.efeat;

import java.util.List;
import java.util.ArrayList;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;
import edu.stanford.nlp.mt.decoder.util.DTUHypothesis;
import edu.stanford.nlp.mt.train.DTUPhraseExtractor;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.DTUFeaturizable;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption.LinearDistortionType;

/**
 * 
 * @author Michel Galley
 */
public class DTULinearDistortionFeaturizer implements IncrementalFeaturizer<IString, String> {

	public static final String DEBUG_PROPERTY = "DebugDTULinearDistortionFeaturizer";
	public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
	public static final String FEATURE_NAME = "LinearDistortion";
  public static final String GAP_FEATURE_NAME = "GapLinearDistortion";

  public static final String EOS_DISTORTION = "EOSDistortion";
  public static final String NO_GAP_AT_END = "NoGapAtEnd";
  public static final int GAP_AT_END_COST = 100;

  private final LinearDistortionType[] featureTypes;
  private final String[] featureNames;

  private boolean addEOS;
  private boolean noGapAtEnd;

  public DTULinearDistortionFeaturizer() {
    // Disable "standard" LinearDistortion (hack):
    edu.stanford.nlp.mt.decoder.feat.LinearDistortionFeaturizer.ACTIVE = false;
    featureTypes = new LinearDistortionType[0];
    featureNames = new String[0];
    addEOS = false;
    noGapAtEnd = false;
  }

  public DTULinearDistortionFeaturizer(String... args) {
    // Disable "standard" LinearDistortion (hack):
    edu.stanford.nlp.mt.decoder.feat.LinearDistortionFeaturizer.ACTIVE = false;
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
  }

  @Override
	public FeatureValue<String> featurize(Featurizable<IString,String> f) {
    return null;
  }

	@Override
	public List<FeatureValue<String>> listFeaturize(Featurizable<IString,String> f) {

    if(f instanceof DTUFeaturizable)
      if(((DTUFeaturizable)f).targetOnly) {
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

    if(featureTypes.length == 0) {
      int linearDistortion = f.linearDistortion + getEOSDistortion(f);
      list.add(new FeatureValue<String>(FEATURE_NAME, -1.0*linearDistortion));
    } else {
      ConcreteTranslationOption<IString>
        priorOpt = (f.prior != null) ? f.prior.option : null,
        currentOpt = f.option;
      for (int i=0; i<featureTypes.length; ++i) {
        int linearDistortion = (priorOpt == null) ? f.option.foreignPos : priorOpt.linearDistortion(currentOpt, featureTypes[i]);
        linearDistortion += getEOSDistortion(f);
        list.add(new FeatureValue<String>(featureNames[i], -1.0*linearDistortion));
      }
    }

    return list;
  }

  private int getEOSDistortion(Featurizable<IString,String> f) {
    if(f.done && addEOS) {
      int endGap = f.foreignSentence.size() - f.option.foreignCoverage.length();
      //System.err.println("hyp span: "+f.hyp.foreignCoverage);
      //System.err.println("opt span: "+f.option.foreignCoverage);
      //System.err.println("opt text: "+f.option.abstractOption.foreign);
      //System.err.println("len: "+f.option.foreignCoverage.length());
      //System.err.println("size: "+f.foreignSentence.size());
      //System.err.println("endGap: "+endGap);
      assert(endGap >= 0);
      return endGap;
    }
    return 0;
  }

  @Override
	public void initialize(List<ConcreteTranslationOption<IString>> options,
			Sequence<IString> foreign) {
	}

	public void reset() { }
}
