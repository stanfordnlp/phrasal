package edu.stanford.nlp.mt.decoder.efeat;

import java.util.*;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;
import edu.stanford.nlp.util.Index;

/**
 * 
 * @author danielcer
 * 
 */
public class DiscriminativePhraseNGrams implements
    IncrementalFeaturizer<IString, String> {
  public static String FEATURE_PREFIX = "DPNG:";
  public static int DEFAULT_SIZE = 2;
  public static final Type DEFAULT_TYPE = Type.st;
  public final int size;

  public enum Type {
    s, t, st
  };

  public final Type type;

  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    return null;
  }

  public DiscriminativePhraseNGrams() {
    size = DEFAULT_SIZE;
    type = DEFAULT_TYPE;
  }

  public DiscriminativePhraseNGrams(String... args) {
    this.size = Integer.parseInt(args[0]);
    this.type = Type.valueOf(args[1]);
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<IString>> options,
      Sequence<IString> foreign, Index<String> featureIndex) {
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(
      Featurizable<IString, String> f) {
    StringBuilder sbuf = new StringBuilder();
    sbuf.append(FEATURE_PREFIX);
    sbuf.append(type).append(":");
    List<FeatureValue<String>> flist = new LinkedList<FeatureValue<String>>();
    for (int history = 0; history < size && f != null; history++) {
      if (history != 0)
        sbuf.append(";");
      if (type == Type.s || type == Type.st)
        sbuf.append(f.foreignPhrase.toString("_"));
      if (type == Type.st)
        sbuf.append("=>");
      if (type == Type.t || type == Type.st)
        sbuf.append(f.translatedPhrase.toString("_"));
      f = f.prior;
      flist.add(new FeatureValue<String>(sbuf.toString(), 1.0));
    }

    return flist;
  }

  public void reset() {
  }
}
