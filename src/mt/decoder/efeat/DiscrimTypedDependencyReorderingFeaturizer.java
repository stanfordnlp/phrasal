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

import edu.stanford.nlp.util.*;
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

  private Boolean useBoundaryOnly = true;
  private LineNumberReader pathReader = null;
  private List<TwoDimensionalMap<Integer,Integer,String>> pathMaps = null;

  public DiscrimTypedDependencyReorderingFeaturizer(String... args) throws IOException {
    if(args.length < 1 || args.length > 2)
      throw new RuntimeException
        ("Usage: DiscrimTypedDependencyReorderingFeaturizer(pathFile,useBoundaryOnly?)");
    if (args.length > 1) {
      useBoundaryOnly = Boolean.parseBoolean(args[1]);
    }

    pathReader = IOTools.getReaderFromFile(args[0]);
    pathMaps = new ArrayList<TwoDimensionalMap<Integer,Integer,String>>();

    try {
      String pLine;
      while ((pLine = pathReader.readLine()) != null) {
        System.err.printf("line %d read from path reader\n", pathReader.getLineNumber());
        System.err.printf("(%d) %s\n", pathReader.getLineNumber(), pLine);
        TwoDimensionalMap<Integer,Integer,String> pathMap = new TwoDimensionalMap<Integer,Integer,String>();
        DepUtils.addPathsToMap(pLine, pathMap);
        pathMaps.add(pathMap);
      }
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException();
    }

    System.err.println("DiscrimTypedDependencyReorderingFeaturizer path file = "+args[0]);
    System.err.println("DiscrimTypedDependencyReorderingFeaturizer useBoundaryOnly? = "+useBoundaryOnly);
    System.err.printf("DiscrimTypedDependencyReorderingFeaturizer path file has %d entries\n", pathMaps.size());
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<IString>> options, Sequence<IString> foreign) { 
    System.err.println("DiscrimTypedDependencyReorderingFeaturizer: initialize.");
  } 

  public void reset() { 
    System.err.println("DiscrimTypedDependencyReorderingFeaturizer: reset.");
  }

  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) { return null; }

  @Override
  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) {
    List<FeatureValue<String>> values = new LinkedList<FeatureValue<String>>();
    return values;
  }
}
