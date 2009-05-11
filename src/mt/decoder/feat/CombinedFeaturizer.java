package mt.decoder.feat;

import java.util.*;

import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.Sequence;

/**
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class CombinedFeaturizer<TK,FV> implements RichIncrementalFeaturizer<TK,FV>, IsolatedPhraseFeaturizer<TK, FV>, Cloneable {
	public List<IncrementalFeaturizer<TK,FV>> featurizers;

  @SuppressWarnings("unchecked")
  public CombinedFeaturizer<TK, FV> clone() {
    //System.err.println("cloned: "+this);
		try {
      CombinedFeaturizer featurizer = (CombinedFeaturizer<TK, FV>)super.clone();
      featurizer.featurizers = new LinkedList<IncrementalFeaturizer<TK,FV>>();
      for(IncrementalFeaturizer<TK,FV> f : featurizers) {
        //System.err.println("cloned: "+f);
        featurizer.featurizers.add(f instanceof ClonedFeaturizer ? ((ClonedFeaturizer)f).clone() : f);
      }
      return featurizer;
    } catch (CloneNotSupportedException e) { return null;  /* will never happen */ }
	}

  /**
	 * 
	 * @return
	 */
	public List<IncrementalFeaturizer<TK,FV>> getFeaturizers() {
		return new ArrayList<IncrementalFeaturizer<TK,FV>>(featurizers);
	}
	
	@SuppressWarnings("unchecked")
	public List<IncrementalFeaturizer<TK,FV>> getNestedFeaturizers() {
		List<IncrementalFeaturizer<TK,FV>> allFeaturizers = new LinkedList<IncrementalFeaturizer<TK,FV>>(featurizers);
		for (IncrementalFeaturizer<TK,FV> featurizer : featurizers) {
			if (featurizer instanceof CombinedFeaturizer) {
				allFeaturizers.addAll(((CombinedFeaturizer<TK,FV>)featurizer).getNestedFeaturizers());
			}
		}
		
		return allFeaturizers;
	}
	
	/**
	 * @param featurizers
	 */
	public CombinedFeaturizer(List<IncrementalFeaturizer<TK,FV>> featurizers) {
		this.featurizers = new ArrayList<IncrementalFeaturizer<TK,FV>>(featurizers); 
	}
	
	/**
	 * @param featurizers
	 */
	public CombinedFeaturizer(IncrementalFeaturizer<TK,FV>... featurizers) {
		this.featurizers = Arrays.asList(featurizers);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public List<FeatureValue<FV>> listFeaturize(Featurizable<TK,FV> f) {
		
		List<Object> featureValueLists = new ArrayList<Object>(featurizers.size());
		int featureCount = 0;
		for (IncrementalFeaturizer<TK,FV> featurizer : featurizers) {
	
			// if a single feature value is available from the method
			// featurizer#featurize, then insert it into the aggregate 
			// list
			FeatureValue<FV> singleFeatureValue = featurizer.featurize(f);
			if (singleFeatureValue != null) {
				featureValueLists.add(singleFeatureValue);
				featureCount++;
			}
			
			// if a list of feature values are available from the method
			// featurizer#listFeaturizer, then insert it into the aggregate
			// list
			List<FeatureValue<FV>> listFeatureValues = featurizer.listFeaturize(f);
			if (listFeatureValues != null) {
				featureValueLists.add(listFeatureValues);
				featureCount += listFeatureValues.size();
			}
		}
		ArrayList<FeatureValue<FV>> featureValues = new ArrayList<FeatureValue<FV>>(featureCount);
		for (Object o : featureValueLists) {
			if (o instanceof FeatureValue) { featureValues.add((FeatureValue)o); continue; }
			List<FeatureValue<FV>> listFeatureValues = (List<FeatureValue<FV>>)o;
			// profiling reveals that addAll is slow due to a buried call to clone() 
			for (FeatureValue<FV> fv : listFeatureValues) {
				featureValues.add(fv);
			}
		}
		return featureValues;
	}
	
	@Override
	public FeatureValue<FV> featurize(Featurizable<TK,FV> f) {
		return null;
	}


	@SuppressWarnings("unchecked")
	@Override
	public List<FeatureValue<FV>> phraseListFeaturize(Featurizable<TK, FV> f) {
		List<FeatureValue<FV>> featureValues = new LinkedList<FeatureValue<FV>>();
		for (IncrementalFeaturizer<TK,FV> featurizer : featurizers) {
			if (!(featurizer instanceof IsolatedPhraseFeaturizer)) continue;
			IsolatedPhraseFeaturizer<TK,FV> isoFeaturizer = (IsolatedPhraseFeaturizer<TK,FV>)featurizer;
			
			FeatureValue<FV> singleFeatureValue = isoFeaturizer.phraseFeaturize(f);
			if (singleFeatureValue != null) {
				featureValues.add(singleFeatureValue);
			}
			
			
			List<FeatureValue<FV>> listFeatureValues = isoFeaturizer.phraseListFeaturize(f);
			if (listFeatureValues != null) {
			  // profiling reveals that addAll is slow due to a buried call to clone()
				for (FeatureValue<FV> fv : listFeatureValues) {
					featureValues.add(fv);
				}
			}
		}
		return featureValues;
	}

	@Override
	public FeatureValue<FV> phraseFeaturize(Featurizable<TK, FV> f) {
		return null;
	}

	@Override
	public void initialize(List<ConcreteTranslationOption<TK>> options,
			Sequence<TK> foreign) {
		for (IncrementalFeaturizer<TK,FV> featurizer : featurizers) {
			featurizer.initialize(options, foreign);
		}
	}
	
	public void reset() {
		for (IncrementalFeaturizer<TK,FV> featurizer : featurizers) {
			featurizer.reset();
		}
	}

  @SuppressWarnings("unchecked")
  @Override
  public void debugBest(Featurizable<TK,FV> f) {
    for (IncrementalFeaturizer<TK,FV> featurizer : featurizers) {
      if (featurizer instanceof RichIncrementalFeaturizer) {
        ((RichIncrementalFeaturizer)featurizer).debugBest(f);
      }
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public void rerankingMode(boolean r) {
    for (IncrementalFeaturizer<TK,FV> featurizer : featurizers) {
      if (featurizer instanceof RichIncrementalFeaturizer) {
        ((RichIncrementalFeaturizer)featurizer).rerankingMode(r);
      }
    }
  }
}