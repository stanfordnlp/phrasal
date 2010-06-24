package edu.stanford.nlp.mt.decoder.efeat;

import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.StatefulFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;

/**
 * @author Spence Green
 */
@SuppressWarnings("unused")
public class OldLinearFutureCostFeaturizer extends StatefulFeaturizer<IString, String> implements IncrementalFeaturizer<IString, String> {

  public static final String DEBUG_PROPERTY = "DebugDistortionFeaturizer";
  public static final boolean DEBUG = (System.getProperty(DEBUG_PROPERTY) != null);

  public static final String FEATURE_NAME = "LinearDistortion";

  //Specifies how much future cost to apply at each translation step
  // 0.0 - no cost (equal to Moses linear distortion)
  // 1.0 - all cost
  private float alpha = 1.0f;

  public OldLinearFutureCostFeaturizer(String... args) {

    assert args.length <= 1;
    if(args.length == 1)
      alpha = Float.parseFloat(args[0].trim());
  }

  @Override
  public FeatureValue<String> featurize(Featurizable<IString,String> f) {

    final float oldFutureCost = f.prior != null ? ((Float) f.prior.getState(this)) : 0.0f;

    float futureCost = 0.0f;    
    if(!f.done) {
      final int firstGapIndex = f.hyp.foreignCoverage.nextClearBit(0);
      if(firstGapIndex < f.foreignPosition) {
        //Jump back
        futureCost = -1.0f * Math.abs(f.foreignPosition + f.foreignPhrase.size() - firstGapIndex);

        //Known forward jumps
        final int cSize = f.hyp.foreignCoverage.size();
        float numCoveredPositions; // = 0.0f;
        
        if(f.hyp.foreignCoverage.size() == f.foreignSentence.size())
          numCoveredPositions = (float) f.hyp.foreignCoverage.get(firstGapIndex, f.foreignPosition).cardinality();
        else
          numCoveredPositions = (float) f.hyp.foreignCoverage.get(firstGapIndex, cSize).cardinality();

        futureCost += -1.0f * numCoveredPositions;

        //Apply alpha
        futureCost = alpha*futureCost + (1.0f - alpha)*oldFutureCost;      
      }
    }

    f.setState(this, futureCost);

    double deltaCost = futureCost - oldFutureCost;

    return new FeatureValue<String>(FEATURE_NAME, (-1.0f * f.linearDistortion) + deltaCost);
  }


  //Unused methods
  @Override
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString,String> f) {return null;}
  @Override
  public void initialize(List<ConcreteTranslationOption<IString>> options, Sequence<IString> foreign) {}
  public void reset() {}
}
