package edu.stanford.nlp.mt.decoder.efeat;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;

import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.ClonedFeaturizer;
import edu.stanford.nlp.mt.syntax.discrimreorder.DepUtils;
import edu.stanford.nlp.mt.syntax.discrimreorder.TrainingExamples;

import edu.stanford.nlp.util.*;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.stats.Counter;

/**
 * Featurizer for a discriminative reordering model that uses typed dependency
 * features from the source (Chinese) side.
 * 
 * The classifier is built with the package: edu.stanford.nlp.mt.discrimreorder
 * For details, look at the package.html for that package.
 * 
 * @author Pi-Chuan Chang
 * 
 * @see edu.stanford.nlp.mt.syntax.discrimreorder.ReorderingClassifier
 */
public class DiscrimTypedDependencyReorderingFeaturizer implements
    ClonedFeaturizer<IString, String> {

  public static final String DEBUG_PROPERTY = "DebugDiscrimTypedDependencyReorderingFeaturizer";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));

  public static final String DETAILED_DEBUG_PROPERTY = "DetailedDebugDiscrimTypedDependencyReorderingFeaturizer";
  public static final boolean DETAILED_DEBUG = Boolean.parseBoolean(System
      .getProperty(DETAILED_DEBUG_PROPERTY, "false"));

  private Boolean useSRCJ2 = false;
  private int useNClass = 2;
  private List<TwoDimensionalMap<Integer, Integer, String>> pathMaps = null;
  private LinearClassifier<TrainingExamples.ReorderingTypes, String> lc = null;
  private ThreeDimensionalMap<CoverageSet, CoverageSet, String, Double> featureCache;

  private Boolean usePathFile = true;

  private int WINDOW = 1;

  public static final String FEATURE_NAME = "DiscrimReorder:Path";

  @Override
  public Object clone() throws CloneNotSupportedException {
    System.err.println("cloned: " + this);
    DiscrimTypedDependencyReorderingFeaturizer featurizer = (DiscrimTypedDependencyReorderingFeaturizer) super
        .clone();
    featurizer.featureCache = new ThreeDimensionalMap<CoverageSet, CoverageSet, String, Double>();
    featurizer.pathMaps = new ArrayList<TwoDimensionalMap<Integer, Integer, String>>();
    return featurizer;
  }

  public DiscrimTypedDependencyReorderingFeaturizer(String... args)
      throws IOException {
    if (args.length != 4)
      throw new RuntimeException(
          "Usage: DiscrimTypedDependencyReorderingFeaturizer(pathFile,classifierFile,useSRCJ2?,useNclass)");
    useSRCJ2 = Boolean.parseBoolean(args[2]);
    useNClass = Integer.parseInt(args[3]);

    Runtime rt = Runtime.getRuntime();

    String pathFile = args[0];
    if (pathFile.equals(""))
      usePathFile = false;

    String classifierFile = args[1];

    long startTimeMillis = System.currentTimeMillis();
    long preTableLoadMemUsed = rt.totalMemory() - rt.freeMemory();

    lc = LinearClassifier.readClassifier(classifierFile);

    long postTableLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    long loadTimeMillis = System.currentTimeMillis() - startTimeMillis;

    System.err
        .printf(
            "\nDone loading discrim reorder classifier: %s (mem used: %d MiB time: %.3f s)\n",
            classifierFile, (postTableLoadMemUsed - preTableLoadMemUsed)
                / (1024 * 1024), loadTimeMillis / 1000.0);
    LineNumberReader pathReader = null;
    if (usePathFile)
      pathReader = IOTools.getReaderFromFile(pathFile);
    pathMaps = new ArrayList<TwoDimensionalMap<Integer, Integer, String>>();

    try {
      String pLine;
      if (usePathFile)
        while ((pLine = pathReader.readLine()) != null) {
          // System.err.printf("line %d read from path reader\n",
          // pathReader.getLineNumber());
          TwoDimensionalMap<Integer, Integer, String> pathMap = new TwoDimensionalMap<Integer, Integer, String>();
          DepUtils.addPathsToMap(pLine, pathMap);
          // System.err.println("pathM size="+pathMap.entrySet().size());
          pathMaps.add(pathMap);
        }
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException();
    }

    System.err
        .println("DiscrimTypedDependencyReorderingFeaturizer classifier file = "
            + classifierFile);
    System.err
        .println("DiscrimTypedDependencyReorderingFeaturizer useSRCJ2? = "
            + useSRCJ2);
    System.err
        .println("DiscrimTypedDependencyReorderingFeaturizer useNclass N = "
            + useNClass);
    if (usePathFile)
      System.err
          .println("DiscrimTypedDependencyReorderingFeaturizer path file = "
              + args[0]);
    else
      System.err
          .println("DiscrimTypedDependencyReorderingFeaturizer NOT using PATH features");
    if (usePathFile)
      System.err
          .printf(
              "DiscrimTypedDependencyReorderingFeaturizer path file has %d entries\n",
              pathMaps.size());
    if (DEBUG)
      System.err.printf("DEBUG mode is on\n");
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<IString>> options,
      Sequence<IString> foreign) {
    featureCache = new ThreeDimensionalMap<CoverageSet, CoverageSet, String, Double>();

  }

  int sentId = -1;

  public void reset() {
    sentId++;
    featureCache = new ThreeDimensionalMap<CoverageSet, CoverageSet, String, Double>();
  }

  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    List<String> features = new ArrayList<String>();

    CoverageSet cur = f.hyp.foreignCoverage;
    CoverageSet pre;
    if (f.prior != null)
      pre = f.prior.hyp.foreignCoverage;
    else
      pre = new CoverageSet();
    StringBuilder preTranslation = new StringBuilder();
    for (int i = 0; i < f.translatedPhrase.size(); i++) {
      preTranslation.append(f.translatedPhrase.get(i));
      if (i != f.translatedPhrase.size() - 1)
        preTranslation.append(" ");
    }
    String preTranslationStr = preTranslation.toString();

    Double featVal = featureCache.get(cur, pre, preTranslationStr);
    if (featVal != null) {
      if (DEBUG) {
        System.err.printf("Use cache: %s %s %s\n", cur, pre, preTranslationStr);
      }
      return new FeatureValue<String>(FEATURE_NAME, featVal);
    } else {
      if (DEBUG) {
        System.err.println("No cache");
      }
      TwoDimensionalMap<Integer, Integer, String> pathMap = null;
      if (usePathFile)
        pathMap = pathMaps.get(sentId);

      CoverageSet fCoverage = f.hyp.foreignCoverage;
      int flen = f.foreignPhrase.size();
      int prevflen = 1;
      int prevelen = 1;
      int currC = f.foreignPosition;
      int prevC = -1;
      int prevE = -1;
      if (f.prior != null) {
        prevC = f.prior.foreignPosition;
        prevflen = f.prior.foreignPhrase.size();
        prevE = f.prior.translationPosition;
        prevelen = f.prior.translatedPhrase.size();
      }
      if (DEBUG) {
        System.err.printf("----\n");
        System.err.printf("Partial translation (pos=%d): %s\n",
            f.translationPosition, f.partialTranslation);
        System.err.printf("Foreign sentence (pos=%d): %s\n", f.foreignPosition,
            f.foreignSentence);
        System.err.printf("Coverage: %s (size=%d)\n", fCoverage,
            f.hyp.foreignCoverage.length());
        System.err.printf("%s(%d) => %s(%d)\n", f.foreignPhrase,
            f.foreignPosition, f.translatedPhrase, f.translationPosition);

        if (f.prior == null)
          System.err.printf("Prior <s> => <s>\n");
        else
          System.err.printf("Prior %s(%d) => %s(%d)\n", f.prior.foreignPhrase,
              f.prior.foreignPosition, f.prior.translatedPhrase,
              f.prior.translationPosition);
        System.err.printf(
            "i = %d, j = %d, j' = %d, lenE = %d, lenC = %d, lenC2 = %d\n",
            prevE, prevC, currC, prevelen, prevflen, flen);
      }

      List<String> feats = extractWordFeatures(f.foreignSentence, prevC,
          prevflen, currC, flen, f.partialTranslation, prevE, prevelen);

      if (usePathFile)
        feats.addAll(extractPathFeatures(f.foreignSentence, prevC, prevflen,
            currC, flen, f.partialTranslation, prevE, prevelen, pathMap));
      if (DEBUG) {
        for (String feat : feats) {
          System.err.println(" feat += " + feat);
        }
      }

      features.addAll(feats);

      Datum<TrainingExamples.ReorderingTypes, String> d = new BasicDatum<TrainingExamples.ReorderingTypes, String>(
          features);
      Counter<TrainingExamples.ReorderingTypes> logPs = lc.logProbabilityOf(d);
      TrainingExamples.ReorderingTypes type;
      double logP;
      if (useNClass == 4) {
        if (prevC + 1 == currC) {
          type = TrainingExamples.ReorderingTypes.ordered;
        } else if (prevC == currC + 1) {
          type = TrainingExamples.ReorderingTypes.distorted;
        } else if (prevC < currC) {
          type = TrainingExamples.ReorderingTypes.ordered_disc;
        } else if (prevC > currC) {
          type = TrainingExamples.ReorderingTypes.distorted_disc;
        } else {
          throw new RuntimeException();
        }
      } else if (useNClass == 2) {
        if (prevC < currC) {
          type = TrainingExamples.ReorderingTypes.ordered;
        } else if (prevC > currC) {
          type = TrainingExamples.ReorderingTypes.distorted;
        } else {
          throw new RuntimeException();
        }
      } else if (useNClass == 3) {
        if (prevC + 1 == currC) {
          type = TrainingExamples.ReorderingTypes.ordered;
        } else if (prevC == currC + 1) {
          type = TrainingExamples.ReorderingTypes.distorted;
        } else if (prevC < currC) {
          type = TrainingExamples.ReorderingTypes.disc;
        } else if (prevC > currC) {
          type = TrainingExamples.ReorderingTypes.disc;
        } else {
          throw new RuntimeException();
        }
      } else {
        throw new RuntimeException();
      }
      logP = logPs.getCount(type);

      if (DEBUG) {
        System.err.printf("log p(%s|d) = %g (score = %g)\n", type, logP,
            lc.scoreOf(d, type));
      }

      featureCache.put(cur, pre, preTranslationStr, logP);
      return new FeatureValue<String>(FEATURE_NAME, logP);
    }
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(
      Featurizable<IString, String> f) {
    return null;
  }

  private static IString getWordInSequence(Sequence<IString> seq, int idx) {
    if (idx < 0)
      return new IString("<s>");
    if (idx >= seq.size())
      return new IString("</s>");
    return seq.get(idx);
  }

  private List<String> extractWordFeatures(Sequence<IString> f, int j,
      int lenC, int j2, int lenC2, Sequence<IString> e, int i, int lenE) {
    List<String> features = new ArrayList<String>();
    // SRCJ
    for (int J = j; J < j + lenC; J++) {
      features.addAll(extractFeatures_SRCJ(f, J));
    }

    if (useSRCJ2) {
      // SRCJ2
      for (int J2 = j2; J2 < j2 + lenC2; J2++) {
        features.addAll(extractFeatures_SRCJ2(f, J2));
      }
    }

    // TGTI
    for (int I = i; I < i + lenE; I++) {
      features.addAll(extractFeatures_TGTI(e, I));
    }
    return features;
  }

  private static List<String> extractPathFeatures(Sequence<IString> f, int j,
      int lenC, int j2, int lenC2, Sequence<IString> e, int i, int lenE,
      TwoDimensionalMap<Integer, Integer, String> pathMap) {
    List<String> features = new ArrayList<String>();

    // path feature
    StringBuilder path = new StringBuilder("PATH:");
    if (!pathMap.isEmpty()) {
      for (int J = j; J < j + lenC; J++) {
        for (int J2 = j2; J2 < j2 + lenC2; J2++) {
          path.append(DepUtils.getPathName(J, J2, pathMap));
          features.add(path.toString());
        }
      }
    }

    return features;
  }

  private List<String> extractFeatures_SRCJ2(Sequence<IString> f, int j2) {
    List<String> features = new ArrayList<String>();
    // SRCJ2
    for (int d = -WINDOW; d <= WINDOW; d++) {
      StringBuilder feature = new StringBuilder("SRCJ2_");
      feature.append(d).append(":").append(getWordInSequence(f, j2 + d));
      features.add(feature.toString());
    }
    return features;
  }

  private List<String> extractFeatures_SRCJ(Sequence<IString> f, int j) {
    List<String> features = new ArrayList<String>();
    // SRCJ
    for (int d = -WINDOW; d <= WINDOW; d++) {
      StringBuilder feature = new StringBuilder("SRCJ_");
      feature.append(d).append(":").append(getWordInSequence(f, j + d));
      features.add(feature.toString());
    }
    return features;
  }

  private List<String> extractFeatures_TGTI(Sequence<IString> e, int i) {
    List<String> features = new ArrayList<String>();
    // TGTI
    for (int d = -WINDOW; d <= WINDOW; d++) {
      StringBuilder feature = new StringBuilder("TGT_");
      IString srcWord = getWordInSequence(e, i + d);
      if (!srcWord.toString().equals("</s>"))
        feature.append(d).append(":").append(srcWord);
      features.add(feature.toString());
    }
    return features;
  }
}
