package edu.stanford.nlp.mt.decoder.efeat;

import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.util.Index;

/**
 * Indicator features for each rule in a derivation.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 */
public class DiscriminativePhraseTable implements RuleFeaturizer<IString, String> {
  
  private static final String FEATURE_NAME = "DiscPT";
  private static final String SOURCE = "src";
  private static final String TARGET = "trg";
  private static final String SOURCE_AND_TARGET = "s+t";

  private static final double DEFAULT_FEATURE_VALUE = 1.0;
  
  private final boolean doSource;
  private final boolean doTarget;
  private final double featureValue;
  
  public DiscriminativePhraseTable() {
    doSource = true;
    doTarget = true;
    featureValue = DEFAULT_FEATURE_VALUE;
  }

  public DiscriminativePhraseTable(String... args) {
    doSource = args.length > 0 ? Boolean.parseBoolean(args[0]) : true;
    doTarget = args.length > 1 ? Boolean.parseBoolean(args[1]) : true;
    featureValue = args.length > 2 ? Double.parseDouble(args[2]) : DEFAULT_FEATURE_VALUE;
  }
  
  @Override
  public void initialize(Index<String> featureIndex) {}

  @Override
  public List<FeatureValue<String>> ruleFeaturize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> fvalues = new LinkedList<FeatureValue<String>>();

    String srcPhrase = f.sourcePhrase.toString("_");
    String tgtPhrase = f.targetPhrase.toString("_");
    
    if (doSource && doTarget) {
      String suffix = srcPhrase + ">"
          + tgtPhrase;
      fvalues.add(new FeatureValue<String>(
          makeFeatureString(FEATURE_NAME, SOURCE_AND_TARGET, suffix), 
          featureValue));

    } else if (doSource) {
      fvalues.add(new FeatureValue<String>(
          makeFeatureString(FEATURE_NAME, SOURCE, srcPhrase), 
          featureValue));

    } else if (doTarget) {
      fvalues.add(new FeatureValue<String>(
          makeFeatureString(FEATURE_NAME, TARGET, tgtPhrase), 
          featureValue));
    }
    return fvalues;
  }

  private String makeFeatureString(String featurePrefix, String featureType, String value) {
    return String.format("%s.%s:%s", featurePrefix, featureType, value);
  }
}
