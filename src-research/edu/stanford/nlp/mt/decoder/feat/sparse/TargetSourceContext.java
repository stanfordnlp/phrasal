package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeatureUtils;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.util.AbstractWordClassMap;
import edu.stanford.nlp.mt.util.FeatureValue;
import edu.stanford.nlp.mt.util.Featurizable;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.PhraseAlignment;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.SourceClassMap;
import edu.stanford.nlp.mt.util.TargetClassMap;
import edu.stanford.nlp.mt.util.TokenUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;

/**
 * Insert target item with source context. Inspired by the BBN neural network
 * LM paper.
 * 
 * @author Spence Green
 *
 */
public class TargetSourceContext extends DerivationFeaturizer<IString,String> {

  public static final String FEATURE_NAME = "TSC";
  
  private final int windowSize;
  private AbstractWordClassMap sourceMap;
  private final boolean targetClassFeature;
  private TargetClassMap targetMap;
  private static final int DEFAULT_WINDOW_SIZE = 3;

  /**
   * Constructor
   */
  public TargetSourceContext() {
    this.windowSize = DEFAULT_WINDOW_SIZE;
    this.targetClassFeature = false;
  }
  
  /**
   * Constructor.
   * 
   * @param args
   */
  public TargetSourceContext(String...args) {
    Properties options = FeatureUtils.argsToProperties(args);
    // source window size
    this.windowSize = PropertiesUtils.getInt(options, "windowSize", DEFAULT_WINDOW_SIZE);
    if (this.windowSize % 2 == 0) {
      // Window should be centered on the source item
      throw new RuntimeException("Window size must be odd: " + String.valueOf(windowSize));
    }
    // add target class feature
    this.targetClassFeature = PropertiesUtils.getBool(options, "targetClassFeature", false);
    if (targetClassFeature) targetMap = TargetClassMap.getInstance();
    // option coarse source class mapping
    boolean localSourceMap = options.containsKey("sourceMap");
    if (localSourceMap) {
      sourceMap = new CoarseSourceMap();
      sourceMap.load(options.getProperty("sourceMap"));
    } else {
      sourceMap = SourceClassMap.getInstance();
    }
  }
  
  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString, String>> ruleList, Sequence<IString> source) {
  }

  @Override
  public List<FeatureValue<String>> featurize(Featurizable<IString, String> f) {
    PhraseAlignment alignment = f.rule.abstractRule.alignment;
    final int range = windowSize / 2;
    List<FeatureValue<String>> features = Generics.newLinkedList();
    for (int i = 0, max = f.targetPhrase.size(); i < max; ++i) {
      int[] alignments = alignment.t2s(i);
      if (alignments != null && alignments.length == 1) {
        // Only consider word-to-word alignments
        int srcIndex = f.sourcePosition + alignments[0];
        // Walk back into the source and find the window
        StringBuilder sb = new StringBuilder();
        final int sourceLength = f.sourceSentence.size();
        for (int j = srcIndex - range; j <= srcIndex + range; ++j) {
          // Boundary symbols
          if (j < 0) {
            if (sb.length() == 0) {
              sb.append(TokenUtils.START_TOKEN.toString());
            }
            continue;
          } else if (j >= sourceLength) {
            sb.append("-").append(TokenUtils.END_TOKEN.toString());
            break;
          }
          if (sb.length() > 0) {
            sb.append("-");
          }
          IString srcClass = sourceMap.get(f.sourceSentence.get(j));
          sb.append(srcClass.toString());
        }
        
        // Add features
        IString targetToken = f.targetPhrase.get(i);
        String featurePrefix = String.format("%s:%s#", FEATURE_NAME, sb.toString());
        features.add(new FeatureValue<String>(featurePrefix + targetToken.toString(), 1.0));
        if (targetClassFeature) {
          String targetClass = targetMap.get(targetToken).toString();
          features.add(new FeatureValue<String>(featurePrefix + targetClass, 1.0));  
        }
      }
    }
    return features;
  }

  private static class CoarseSourceMap extends AbstractWordClassMap {
    public CoarseSourceMap() {
      wordToClass = Generics.newHashMap();
    }
  }
}
