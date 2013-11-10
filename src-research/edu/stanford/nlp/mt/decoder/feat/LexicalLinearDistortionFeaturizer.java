package edu.stanford.nlp.mt.decoder.feat;

import java.util.*;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;

/**
 * 
 * @author danielcer
 * 
 */
public class LexicalLinearDistortionFeaturizer extends
    DerivationFeaturizer<IString, String> {
  public static final String FEATURE_PREFIX = "LLD";
  public static final String ABSOLUTE = ":a";
  public static final String LEFT_SHIFT = ":l";
  public static final String RIGHT_SHIFT = ":r";
  public static final String SOURCE = ":s";
  public static final String TARGET = ":t";
  public static final String CURRENT = ":c";
  public static final String PRIOR = ":p";
  public static final String SOURCE_CONJ_TARGET = ":st";

  public static final boolean DEFAULT_USE_LRDISTANCE = true;
  public static final boolean DEFAULT_DO_SOURCE = true;
  public static final boolean DEFAULT_DO_TARGET = true;
  public static final boolean DEFAULT_DO_SOURCE_CONJ_TARGET = true;
  public static final boolean DEFAULT_DO_PRIOR = true;
  public static final Sequence<IString> INIT_PHRASE = new SimpleSequence<IString>(
      new IString("<s>"));

  final boolean lrDistance;
  final boolean doSource;
  final boolean doTarget;
  final boolean doSourceConjTarget;
  final boolean doPrior;

  public LexicalLinearDistortionFeaturizer() {
    lrDistance = DEFAULT_USE_LRDISTANCE;
    doSource = DEFAULT_DO_SOURCE;
    doTarget = DEFAULT_DO_TARGET;
    doSourceConjTarget = DEFAULT_DO_SOURCE_CONJ_TARGET;
    doPrior = DEFAULT_DO_PRIOR;
  }

  public LexicalLinearDistortionFeaturizer(String... args) {
    lrDistance = Boolean.parseBoolean(args[0]);
    doSource = Boolean.parseBoolean(args[1]);
    doTarget = Boolean.parseBoolean(args[2]);
    doSourceConjTarget = Boolean.parseBoolean(args[3]);
    doPrior = Boolean.parseBoolean(args[4]);
  }
  
  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString,String>> options, Sequence<IString> foreign) {
  }

  @Override
  public List<FeatureValue<String>> featurize(
      Featurizable<IString, String> f) {

    if (f.linearDistortion == 0)
      return null;

    List<FeatureValue<String>> flist = new LinkedList<FeatureValue<String>>();
    String prefix;
    if (lrDistance) {
      int signedLinearDistortion = (f.prior == null ? -f.sourcePosition
          : f.prior.derivation.rule
              .signedLinearDistortion(f.derivation.rule));
      String type = (signedLinearDistortion < 0 ? LEFT_SHIFT : RIGHT_SHIFT);
      prefix = FEATURE_PREFIX + type;
    } else {
      prefix = FEATURE_PREFIX + ABSOLUTE;
    }

    for (int i = 0; i < 2; i++) {
      if (i == 1 && !doPrior)
        break;
      Sequence<IString> foreignPhrase = (i == 0 ? f.sourcePhrase
          : f.prior != null ? f.prior.sourcePhrase : INIT_PHRASE);
      Sequence<IString> translatedPhrase = (i == 0 ? f.targetPhrase
          : f.prior != null ? f.prior.targetPhrase : INIT_PHRASE);
      String type = (i == 0 ? CURRENT : PRIOR);
      if (doSource)
        for (IString sourceWord : foreignPhrase) {
          flist.add(new FeatureValue<String>(prefix + type + SOURCE + ":"
              + sourceWord, f.linearDistortion));
        }

      if (doTarget)
        for (IString targetWord : translatedPhrase) {
          flist.add(new FeatureValue<String>(prefix + type + TARGET + ":"
              + targetWord, f.linearDistortion));
        }

      if (doSourceConjTarget)
        for (IString sourceWord : foreignPhrase) {
          for (IString targetWord : translatedPhrase) {
            flist.add(new FeatureValue<String>(prefix + type
                + SOURCE_CONJ_TARGET + ":" + sourceWord + ">" + targetWord,
                f.linearDistortion));
          }
        }
    }
    return flist;
  }

  public void reset() {
  }
}
