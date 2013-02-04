package edu.stanford.nlp.mt.decoder.efeat;

import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.ThreadsafeCounter;
import edu.stanford.nlp.util.Index;

/**
 * 
 * @author danielcer
 * @author Spence Green
 * 
 */
public class DiscriminativeTargetLM implements
IncrementalFeaturizer<IString, String> {
  public static String FEATURE_NAME = "DiscLM";
  public static int DEFAULT_SIZE = 2;
  private static final double DEFAULT_UNSEEN_THRESHOLD = 2.0;

  private static final String START = "<s>";
  private static final String END = "</s>";

  private Counter<String> featureCounter;
  private Index<String> featureIndex;

  private final int size;
  private final double unseenThreshold;

  public DiscriminativeTargetLM() {
    size = DEFAULT_SIZE;
    unseenThreshold = DEFAULT_UNSEEN_THRESHOLD;
  }

  public DiscriminativeTargetLM(String... args) {
    size = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_SIZE;
    unseenThreshold = args.length > 1 ? Double.parseDouble(args[1]) : DEFAULT_UNSEEN_THRESHOLD;
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<IString,String>> options,
      Sequence<IString> foreign, Index<String> featureIndex) {
    this.featureIndex = featureIndex;
    featureCounter = featureIndex.isLocked() ? null : new ThreadsafeCounter<String>(100*featureIndex.size());
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(
      Featurizable<IString, String> f) {

    List<FeatureValue<String>> flist = new LinkedList<FeatureValue<String>>();
    Sequence<IString> targetPrefix = f.partialTranslation;
    int prefixLength = targetPrefix.size() + (f.done ? 1 : 0);
    int attachmentPosition = f.translationPosition;

    final int lastStartPos = prefixLength - size + 1;
    for (int startPos = Math.max(attachmentPosition - size + 1, -1); 
        startPos < lastStartPos; ++startPos) {
      StringBuilder sb = new StringBuilder();
      final int endPos = startPos + size;
      boolean isFirstToken = true;
      for (int i = startPos; i < endPos; ++i) {
        if ( ! isFirstToken) sb.append("-");
        isFirstToken = false;
        if (i < 0) {
          sb.append(START);
        } else if (i == prefixLength) {
          sb.append(END);
        } else {
          if (f.done && i == targetPrefix.size()) {
            sb.append(END);
          } else {
            sb.append(targetPrefix.get(i).toString());
          }
        }
      }
      flist.add(new FeatureValue<String>(makeFeatureString(FEATURE_NAME, sb.toString()), 1.0));
    }
    return flist;
  }

  private String makeFeatureString(String featurePrefix, String value) {
    String featureString = String.format("%s.%d:%s", featurePrefix, size, value);

    // Collect statistics and detect unseen events
    if (featureCounter == null) {
      // Test time
      if (featureIndex.indexOf(featureString) < 0) {
        // TODO(spenceg): Elaborate?
        featureString = String.format("%s.%d:%s", featurePrefix, size, "UNK");
      }

    } else {
      // Training time
      double count = featureCounter.incrementCount(featureString);
      if (count <= unseenThreshold) {
        // TODO(spenceg): Elaborate?
        featureString = String.format("%s.%d:%s", featurePrefix, size, "UNK");     
      }
    }
    return featureString;
  }

  public void reset() {
  }

  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    return null;
  }
}
