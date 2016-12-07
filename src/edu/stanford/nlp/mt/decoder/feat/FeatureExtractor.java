package edu.stanford.nlp.mt.decoder.feat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.InputProperty;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Container class for featurizers.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 * @param <TK>
 * @param <FV>
 */
public class FeatureExtractor<TK, FV> extends 
    DerivationFeaturizer<TK, FV> 
    implements RuleFeaturizer<TK, FV>, RerankingFeaturizer<TK, FV>, 
    Cloneable {
  
  private List<Featurizer<TK, FV>> featurizers;
  private final int numDerivationFeaturizers;
  private final int numRerankingFeaturizers;
  private int featureAugmentationMode = -1;
  private ConcurrentHashMap<String, String> prefixFeatMap = null;
  private ConcurrentHashMap<String, String> straddleFeatMap = null;
  private ConcurrentHashMap<String, String> afterPrefixFeatMap = null;
  
  /**
   * Constructor.
   * 
   * @param featurizers
   */
  public FeatureExtractor(List<Featurizer<TK, FV>> featurizers) {
    this(featurizers, null);
  }
  
  /**
   * Constructor with optional feature splitting mode.
   * 
   * @param allFeaturizers
   * @param featureAugmentationMode
   */
  public FeatureExtractor(List<Featurizer<TK, FV>> featurizers,
      String featureAugmentationMode) {
    this.featurizers = new ArrayList<>(featurizers);
    int id = -1;
    int numRerankers = 0;
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof DerivationFeaturizer) {
        DerivationFeaturizer<TK, FV> sfeaturizer = (DerivationFeaturizer<TK, FV>) featurizer;
        sfeaturizer.setId(++id);
      }
      else if (featurizer instanceof RerankingFeaturizer) {
        numRerankers++;
      }
    }
    this.numDerivationFeaturizers = id + 1;
    this.numRerankingFeaturizers= numRerankers;
    
    setFeatureAugmentationMode(featureAugmentationMode);
    
    // Initialize rule featurizers
    initialize();
  }

  
  public boolean setFeatureAugmentationMode(String featureAugmentationMode) {
    if (featureAugmentationMode != null) {
      if (featureAugmentationMode.equals("all")) {
        this.featureAugmentationMode = 0;
      } else if (featureAugmentationMode.equals("dense")) {
        this.featureAugmentationMode = 1;
      } else if (featureAugmentationMode.equals("extended")) {
        this.featureAugmentationMode = 2;
      } else if (featureAugmentationMode.equals("prefixAndGenre")) {
        this.featureAugmentationMode = 3;
        initPrefixFeatMaps();
      } else if (featureAugmentationMode.equals("prefix")) {
        this.featureAugmentationMode = 4;
        initPrefixFeatMaps();
      }
      return true;
    }
    return false;
  }
  
  private void initPrefixFeatMaps() {
    prefixFeatMap = new ConcurrentHashMap<>();
    straddleFeatMap = new ConcurrentHashMap<>();
    afterPrefixFeatMap = new ConcurrentHashMap<>();
  }
  
  /**
   * Remove feature templates give a <code>Set</code> of class names.
   * 
   * @param disabledFeaturizers
   */
  public void deleteFeaturizers(Set<String> disabledFeaturizers) {
    System.err.println("Featurizers to disable: " + disabledFeaturizers);
    Set<String> foundFeaturizers = new HashSet<String>();
    List<Featurizer<TK, FV>> filteredFeaturizers = new ArrayList<>();
    for (Featurizer<TK, FV> f : featurizers) {
      String className = f.getClass().getName();
      if (f instanceof FeatureExtractor)
        ((FeatureExtractor<?, ?>) f).deleteFeaturizers(disabledFeaturizers);
      if (!disabledFeaturizers.contains(className)) {
        System.err.println("Keeping featurizer: " + f);
        filteredFeaturizers.add(f);
      } else {
        System.err.println("Disabling featurizer: " + f);
        foundFeaturizers.add(className);
      }
    }
    for (String f : disabledFeaturizers)
      if (!foundFeaturizers.contains(f))
        System.err.println("No featurizer to disable for class: " + f);
    featurizers = filteredFeaturizers;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object clone() throws CloneNotSupportedException {
    FeatureExtractor<TK, FV> featurizer = (FeatureExtractor<TK, FV>) super
        .clone();
    featurizer.featurizers = new ArrayList<>();
    for (Featurizer<TK, FV> f : featurizers) {
      if(f instanceof NeedsCloneable) {
        if(f instanceof RerankingFeaturizer) {
          featurizer.featurizers.add((RerankingFeaturizer<TK, FV>) ((NeedsCloneable<TK, FV>) f).clone());
        }
        else {
          featurizer.featurizers.add((DerivationFeaturizer<TK, FV>) ((NeedsCloneable<TK, FV>) f).clone());
        }
      }
      else 
        featurizer.featurizers.add(f);
          
    }
    return featurizer;
  }

  /**
   * Get all feature templates in this feature extractor. Recursively extracts
   * feature templates from nested <code>FeatureExtractor</code>s.
   * 
   * @return
   */
  public List<Featurizer<TK, FV>> getFeaturizers() {
    List<Featurizer<TK, FV>> allFeaturizers = new ArrayList<>(
        featurizers);
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof FeatureExtractor) {
        allFeaturizers.addAll(((FeatureExtractor<TK, FV>) featurizer)
            .getFeaturizers());
      }
    }
    return allFeaturizers;
  }

  /**
   * Returns the number of <code>DerivationFeaturizer</code>s.
   * 
   * @return
   */
  public int getNumDerivationFeaturizers() {
    return numDerivationFeaturizers;
  }

  /**
   * Returns the number of <code>RerankingFeaturizer</code>s.
   * 
   * @return
   */
  public int getNumRerankingFeaturizers() {
    return numRerankingFeaturizers;
  }

  
  /**
   * Extract derivation features.
   */
  @Override
  public List<FeatureValue<FV>> featurize(Featurizable<TK, FV> f) {
    List<FeatureValue<FV>> featureValues = new ArrayList<>();
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof DerivationFeaturizer) {
        List<FeatureValue<FV>> listFeatureValues = 
            ((DerivationFeaturizer<TK,FV>) featurizer).featurize(f);
        if (listFeatureValues != null) {
          for (FeatureValue<FV> fv : listFeatureValues) {
            featureValues.add(fv);
          }
        }
      }
    }
    
    if(featureAugmentationMode >= 0) augmentFeatures(f, featureValues);
    
    return featureValues;
  }

  /**
   * Extract rule features.
   */
  @Override
  public List<FeatureValue<FV>> ruleFeaturize(Featurizable<TK, FV> f) {
    List<FeatureValue<FV>> featureValues = new ArrayList<>();
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof RuleFeaturizer) {
        RuleFeaturizer<TK, FV> ruleFeaturizer = (RuleFeaturizer<TK, FV>) featurizer;
        List<FeatureValue<FV>> listFeatureValues = 
            ruleFeaturizer.ruleFeaturize(f);
        if (listFeatureValues != null) {
          boolean doNotCache = ruleFeaturizer.isolationScoreOnly();
          for (FeatureValue<FV> fv : listFeatureValues) {
            fv.doNotCache = doNotCache;
            featureValues.add(fv);
          }
        }
      }
    }
    
    if(featureAugmentationMode >= 0) augmentFeatures(f, featureValues);
    
    return featureValues;
  }
  
  /**
   * Extract reranking features.
   */
  public List<List<FeatureValue<FV>>> rerankingFeaturize(List<Featurizable<TK, FV>> f) {
    List<List<FeatureValue<FV>>> featureValues = new ArrayList<>();
    for(int i = 0; i < f.size(); ++i) {
      featureValues.add(new ArrayList<>());
    }
    
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof RerankingFeaturizer) {
        List<List<FeatureValue<FV>>> listFeatureValues = 
            ((RerankingFeaturizer<TK,FV>) featurizer).rerankingFeaturize(f);
        
        if (listFeatureValues != null) {
          for(int i = 0; i < f.size(); ++i) {
            if (listFeatureValues.get(i) != null) {
              featureValues.get(i).addAll(listFeatureValues.get(i));
            }
          }
        }
      }
    }
    
    if(featureAugmentationMode >= 0) {
      for(int i = 0; i < f.size(); ++i) {
        augmentFeatures(f.get(i), featureValues.get(i), true);
      }
    }
    
    return featureValues;
  }

 
  private static final String[] NO_GENRE = new String[]{""};
  private static final String PREFIX = "PRF";
  private static final String PREFIX_BOUNDARY_STRADDLE = PREFIX + "-" + "STR";
  private static final String AFTER_PREFIX = PREFIX + "-" + "AFT";

  /**
   * Feature space augmentation a la Daume III (2007).
   * 
   * @param f
   * @param featureValues
   */
  @SuppressWarnings("unchecked")
  private void augmentFeatures(Featurizable<TK, FV> f, List<FeatureValue<FV>> featureValues) {
    augmentFeatures(f, featureValues, false);
  }
  
  /**
   * Feature space augmentation a la Daume III (2007).
   * 
   * @param f
   * @param featureValues
   */
  @SuppressWarnings("unchecked")
  private void augmentFeatures(Featurizable<TK, FV> f, List<FeatureValue<FV>> featureValues, boolean noPrefix) {
    String[] genres = (String[]) f.sourceInputProperties.get(InputProperty.Domain);
    if (genres == null) genres = NO_GENRE;
    
    if (featureAugmentationMode < 4) {
      for(int i = 0, sz = featureValues.size(); i < sz; ++i) {
        FeatureValue<FV> fv = featureValues.get(i);
        if (featureAugmentationMode == 0 ||
            featureAugmentationMode == 3 ||
            (featureAugmentationMode == 1 && fv.isDenseFeature) ||
            (featureAugmentationMode == 2 && ! fv.isDenseFeature)) {
          for(String genre : genres) {
            String featureValue = "aug-" +  genre + "-" + fv.name.toString();
            featureValues.add(new FeatureValue<>((FV) featureValue, fv.value, fv.isDenseFeature));
          }
        }
      }
    } 
    if (!noPrefix && featureAugmentationMode >= 3) {
      // Prefix mode
      for(int i = 0, sz = featureValues.size(); i < sz; ++i) {
        FeatureValue<FV> fv = featureValues.get(i);
        final boolean inPrefix = f.targetSequence != null && f.derivation != null && 
            f.derivation.insertionPosition < f.derivation.prefixLength;
        final boolean straddle = inPrefix && f.derivation.length > f.derivation.prefixLength;
        final boolean afterPrefix = f.derivation != null && f.derivation.prefixLength > 0 && !inPrefix;
        if (inPrefix) {
          String featureValue = getAugFeatureName(fv.name.toString(), prefixFeatMap, PREFIX);
          featureValues.add(new FeatureValue<>((FV) featureValue, fv.value, fv.isDenseFeature));
        }
        else if(afterPrefix) {
          String featureValue = getAugFeatureName(fv.name.toString(), afterPrefixFeatMap, AFTER_PREFIX);
          featureValues.add(new FeatureValue<>((FV) featureValue, fv.value, fv.isDenseFeature));
        }
        if (straddle) {
          String featureValue = getAugFeatureName(fv.name.toString(), straddleFeatMap, PREFIX_BOUNDARY_STRADDLE);
          featureValues.add(new FeatureValue<>((FV) featureValue, fv.value, fv.isDenseFeature));
        }
      }
    }
  }
  
  private String getAugFeatureName(String baseFeatureName, ConcurrentHashMap<String, String> map, String augLabel) {
    String result = map.get(baseFeatureName);
    if(result != null) return result;
    
    result = "aug-" + augLabel + "-" + baseFeatureName;
    map.put(baseFeatureName, result);
    return result;
  }
  
  @SuppressWarnings("unchecked")
  public List<FeatureValue<FV>> nonLocalAugmentRuleFeatures(List<FeatureValue<FV>> ruleFeatures, Derivation<TK, FV> derivation) {
    List<FeatureValue<FV>> rv = null;
    if (featureAugmentationMode >= 3) {
      rv = new ArrayList<>();
      if(derivation.insertionPosition < derivation.prefixLength) {
        boolean straddle = derivation.length > derivation.prefixLength;
        // Prefix mode
        for(FeatureValue<FV> fv : ruleFeatures) {
          if(fv.name.toString().startsWith("aug-")) continue;
          String featureValue = getAugFeatureName(fv.name.toString(), prefixFeatMap, PREFIX);
          rv.add(new FeatureValue<>((FV) featureValue, fv.value, fv.isDenseFeature));
          if(straddle) {
            featureValue = getAugFeatureName(fv.name.toString(), straddleFeatMap, PREFIX_BOUNDARY_STRADDLE);
            rv.add(new FeatureValue<>((FV) featureValue, fv.value, fv.isDenseFeature));
          }
        }
      }
      else if(derivation.prefixLength > 0) {
        for(FeatureValue<FV> fv : ruleFeatures) {
          if(fv.name.toString().startsWith("aug-")) continue;
          String featureValue = getAugFeatureName(fv.name.toString(), afterPrefixFeatMap, AFTER_PREFIX);
          rv.add(new FeatureValue<>((FV) featureValue, fv.value, fv.isDenseFeature));
        }
      }
    }
    return rv;
  }

  @Override
  public void initialize(int sourceInputId,
      Sequence<TK> sourceSequence) {
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof DerivationFeaturizer) {
        ((DerivationFeaturizer<TK,FV>) featurizer).initialize(sourceInputId, sourceSequence);
      }
      else if (featurizer instanceof RerankingFeaturizer) {
        ((RerankingFeaturizer<TK,FV>) featurizer).initialize(sourceInputId, sourceSequence, null);
      }
    }
  }
  

  @Override
  public void initialize(int sourceInputId,
      Sequence<TK> sourceSequence, Sequence<TK> targetPrefix) {
    for (Featurizer<TK, FV> featurizer : featurizers) {
      if (featurizer instanceof DerivationFeaturizer) {
        ((DerivationFeaturizer<TK,FV>) featurizer).initialize(sourceInputId, sourceSequence);
      }
      else if (featurizer instanceof RerankingFeaturizer) {
        ((RerankingFeaturizer<TK,FV>) featurizer).initialize(sourceInputId, sourceSequence, targetPrefix);
      }
    }
  }

  @Override
  public void initialize() {
    for (Featurizer<TK,FV> featurizer : featurizers) {
      if (featurizer instanceof RuleFeaturizer) {
        ((RuleFeaturizer<TK,FV>) featurizer).initialize();
      }
    }
  }
 
  @Override
  public boolean isolationScoreOnly() {
    return false;
  }
}
