package mt.decoder.efeat;

import java.util.*;
import java.io.*;

import mt.base.ARPALanguageModel;
import mt.base.ConcreteTranslationOption;
import mt.base.CoverageSet;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.ExtendedLexicalReorderingTable;
import mt.base.IOTools;
import mt.base.Sequence;
import mt.base.SimpleSequence;
import mt.base.ExtendedLexicalReorderingTable.ReorderingTypes;
import mt.decoder.feat.IncrementalFeaturizer;
import mt.decoder.feat.LexicalReorderingFeaturizer;
import mt.train.AlignmentGrid;
import mt.discrimreorder.DepUtils;
import mt.discrimreorder.TrainingExamples;

import edu.stanford.nlp.util.*;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.util.IString;

/**
 * Featurizer for a discriminative reordering model that uses typed dependency
 * features from the source (Chinese) side.
 * 
 * The classifier is built with the package: mt.discrimreorder
 * For details, look at the package.html for that package.
 * 
 * @author Pi-Chuan Chang
 *
 * @see mt.discrimreorder.ReorderingClassifier
 */
public class DiscrimTypedDependencyReorderingFeaturizer implements IncrementalFeaturizer<IString, String> {

  public static final String DEBUG_PROPERTY = "DebugDiscrimTypedDependencyReorderingFeaturizer";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  public static final String DETAILED_DEBUG_PROPERTY = "DetailedDebugDiscrimTypedDependencyReorderingFeaturizer";
  public static final boolean DETAILED_DEBUG = Boolean.parseBoolean(System.getProperty(DETAILED_DEBUG_PROPERTY, "false"));

  private Boolean useBoundaryOnly = false;
  private LineNumberReader pathReader = null;
  private List<TwoDimensionalMap<Integer,Integer,String>> pathMaps = null;
  private LinearClassifier lc = null;

  private int WINDOW = 1;

  public DiscrimTypedDependencyReorderingFeaturizer(String... args) throws IOException {
    if(args.length < 1 || args.length > 3)
      throw new RuntimeException
        ("Usage: DiscrimTypedDependencyReorderingFeaturizer(pathFile,classifierFile,useBoundaryOnly?)");
    if (args.length > 2) {
      useBoundaryOnly = Boolean.parseBoolean(args[2]);
    }

    lc = LinearClassifier.readClassifier(args[1]);
    pathReader = IOTools.getReaderFromFile(args[0]);
    pathMaps = new ArrayList<TwoDimensionalMap<Integer,Integer,String>>();

    try {
      String pLine;
      while ((pLine = pathReader.readLine()) != null) {
        System.err.printf("line %d read from path reader\n", pathReader.getLineNumber());
        //System.err.printf("(%d) %s\n", pathReader.getLineNumber(), pLine);
        TwoDimensionalMap<Integer,Integer,String> pathMap = new TwoDimensionalMap<Integer,Integer,String>();
        DepUtils.addPathsToMap(pLine, pathMap);
        pathMaps.add(pathMap);
      }
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException();
    }

    System.err.println("DiscrimTypedDependencyReorderingFeaturizer path file = "+args[0]);
    System.err.println("DiscrimTypedDependencyReorderingFeaturizer classifier file = "+args[1]);
    System.err.println("DiscrimTypedDependencyReorderingFeaturizer useBoundaryOnly? = "+useBoundaryOnly);
    System.err.printf("DiscrimTypedDependencyReorderingFeaturizer path file has %d entries\n", pathMaps.size());
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<IString>> options, Sequence<IString> foreign) { 
    System.err.println("DiscrimTypedDependencyReorderingFeaturizer: initialize.");
  } 

  int sentId = -1;
  public void reset() { 
    System.err.println("DiscrimTypedDependencyReorderingFeaturizer: reset.");
    sentId++;
    System.err.println("sentId="+sentId);
  }

  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) { return null; }

  @Override
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> values = new LinkedList<FeatureValue<String>>();
    List<String> features = new ArrayList<String>();

    TwoDimensionalMap<Integer,Integer,String> pathMap = pathMaps.get(sentId);

    CoverageSet fCoverage = f.hyp.foreignCoverage;
    int flen = f.foreignPhrase.size();
    int prevflen = 1;
    System.err.printf("----\n");
    int currC = f.foreignPosition;
    int prevC = -1;
    if (f.prior != null) {
      prevC = f.prior.foreignPosition;
      prevflen = f.prior.foreignPhrase.size();
    }

    System.err.printf("Partial translation (pos=%d): %s\n", f.translationPosition, f.partialTranslation);
    System.err.printf("Foreign sentence (pos=%d): %s\n", f.foreignPosition, f.foreignSentence);
    System.err.printf("Coverage: %s (size=%d)\n", fCoverage, f.hyp.foreignCoverage.length());
    System.err.printf("%s(%d) => %s(%d)\n", f.foreignPhrase, f.foreignPosition, f.translatedPhrase, f.translationPosition);
    if (f.prior == null) System.err.printf("Prior <s> => <s>\n");
    else System.err.printf("Prior %s(%d) => %s(%d)\n",        f.prior.foreignPhrase, f.prior.foreignPosition, f.prior.translatedPhrase, f.prior.translationPosition);
    System.err.printf("j = %d, j' = %d, len1 = %d, len2 = %d\n", prevC, currC, prevflen, flen);

    List<String> feats = extractWordFeatures(f.foreignSentence, prevC, currC, prevflen, flen);
    for(String feat : feats) {
      System.err.println(" feat += "+feat);
    }
    features.addAll(feats);

    Datum<TrainingExamples.ReorderingTypes,String> d = new BasicDatum(features);
    System.err.printf("p(%s|d) = %f\n",
                       TrainingExamples.ReorderingTypes.ordered,
                       lc.scoreOf(d, TrainingExamples.ReorderingTypes.ordered));

    return values;
  }

  private IString getSourceWord(Sequence<IString> f, int idx) {
    if (idx < 0) return new IString("<s>");
    if (idx >= f.size()) return new IString("</s>");
    return f.get(idx);
  }

  private List<String> extractWordFeatures(Sequence<IString> f, int j, int j2, int len, int len2) {
    List<String> features = new ArrayList<String>();
    // SRCJ
    for (int J = j; J < j+len; J++) {
      features.addAll(extractFeatures_SRCJ(f, J));
    }

    // SRCJ2
    for (int J2 = j2; J2 < j2+len2; J2++) {
      features.addAll(extractFeatures_SRCJ2(f, J2));
    }
    
    return features;
    
  }

  private List<String> extractFeatures_SRCJ(Sequence<IString> f, int j) {
    List<String> features = new ArrayList<String>();
    // SRCJ
    for (int d = -WINDOW; d <= WINDOW; d++) {
      StringBuilder feature = new StringBuilder("SRCJ_");
      feature.append(d).append(":").append(getSourceWord(f, j+d));
      features.add(feature.toString());
    }
    return features;
  }

  private List<String> extractFeatures_SRCJ2(Sequence<IString> f, int j2) {
    List<String> features = new ArrayList<String>();
    // SRCJ2
    for (int d = -WINDOW; d <= WINDOW; d++) {
      StringBuilder feature = new StringBuilder("SRCJ2_");
      feature.append(d).append(":").append(getSourceWord(f, j2+d));
      features.add(feature.toString());
    }
    return features;
  }
}
