package edu.stanford.nlp.mt.tune.optimizers;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.stanford.nlp.mt.metrics.SentenceLevelMetric;
import edu.stanford.nlp.mt.tune.OnlineOptimizer;
import edu.stanford.nlp.mt.tune.OnlineUpdateRule;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.RichTranslation;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

/**
 * Abstract class that makes it simpler and easier to implement the OnlineOptimizer interface
 * 
 * @author daniel cer
 * @author Spence Green
 *
 */
abstract public class AbstractOnlineOptimizer implements OnlineOptimizer<IString, String> {
  public static final double DEFAULT_SIGMA = 0.1;
  public static final double DEFAULT_RATE = 0.1;
  public static final String DEFAULT_UPDATER = "sgd";
  public static final double DEFAULT_L1 = 0;

  private final int tuneSetSize;

  private final double learningRate;
  private final String updaterType;

  // Regularization fields
  private final double L1lambda;
  private boolean l2Regularization;
  private final String regconfig;
  private final String fixedFeaturesFile;

  private static final Logger logger = LogManager.getLogger(AbstractOnlineOptimizer.class.getName());

  private final int expectedNumFeatures;

  final double sigmaSq;

  public AbstractOnlineOptimizer(int tuneSetSize, int expectedNumFeatures,
      String...args) {
    this(tuneSetSize, expectedNumFeatures,
        args != null && args.length > 0 ? Double.parseDouble(args[0]) : DEFAULT_SIGMA, 
            args != null && args.length > 1 ? Double.parseDouble(args[1]) : DEFAULT_RATE, 
                args != null && args.length > 2 ? args[2] : DEFAULT_UPDATER,
                    args != null && args.length > 3 ? Double.parseDouble(args[3]) : DEFAULT_L1, 
                        args != null && args.length > 4 ? args[4] : null,
                            args != null && args.length > 5 ? args[5] : null);
  }

  public AbstractOnlineOptimizer(int tuneSetSize, int expectedNumFeatures,
      double sigma, double rate, String updaterType, double L1lambda,
      String regconfig, String fixedFeaturesFile) {

    this.expectedNumFeatures = expectedNumFeatures;
    this.tuneSetSize = tuneSetSize;
    this.learningRate = rate;
    this.updaterType = updaterType;

    // L1 regularization
    this.L1lambda = L1lambda;
    this.regconfig = regconfig != null && regconfig.trim().length() == 0 ? null : regconfig;

    // L2 regularization
    this.l2Regularization = !Double.isInfinite(sigma);
    this.sigmaSq = l2Regularization ? sigma * sigma : 0.0;

    this.fixedFeaturesFile = fixedFeaturesFile != null && fixedFeaturesFile.trim().length() == 0 ? null : fixedFeaturesFile;
    
    logger.info("tuneSetSize: {}  learningRate: {}  L1lambda: {}  l2Regularization: {}  sigmaSq: {}",
        tuneSetSize, learningRate, L1lambda, l2Regularization, sigmaSq);
  }

  @Override
  public OnlineUpdateRule<String> newUpdater() {
    if (this.updaterType.equalsIgnoreCase("adagrad")) {
      return new AdaGradUpdater(learningRate, expectedNumFeatures);
    }
    
    Counter<String> customl1 = new ClassicCounter<String>();
    if (regconfig != null) {
      try{
        LineNumberReader reader = IOTools.getReaderFromFile(regconfig);
        for (String line; (line = reader.readLine()) != null;) {
          String[] fields = line.trim().split("\\s+");
          assert fields.length == 2 : "Malformed regularization specification: " + line;
          customl1.incrementCount(fields[0], Double.parseDouble(fields[1]));
        }
        reader.close();
        System.out.println("Using custom L1: "+customl1);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    
    Set<String> fixedFeatures = new HashSet<String>();
    if (fixedFeaturesFile != null) {
      try{
        LineNumberReader reader = IOTools.getReaderFromFile(fixedFeaturesFile);
        for (String line; (line = reader.readLine()) != null;) {
          String[] fields = line.trim().split("\\s+");
          assert fields.length == 1 : "Malformed specification for fixed features: " + line;
          fixedFeatures.add(fields[0]);
        }
        reader.close();
        System.out.println("Keeping the following features fixed: " + fixedFeatures);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    
    if (this.updaterType.equalsIgnoreCase("adagradl1"))
      return new AdaGradFOBOSUpdater(learningRate, expectedNumFeatures,
          L1lambda, AdaGradFOBOSUpdater.Norm.LASSO, customl1, fixedFeatures);
    if (this.updaterType.equalsIgnoreCase("adagradElitistLasso"))
      return new AdaGradFOBOSUpdater(learningRate, expectedNumFeatures,
          L1lambda, AdaGradFOBOSUpdater.Norm.aeLASSO, customl1, fixedFeatures);
    if (this.updaterType.equalsIgnoreCase("adagradl1f")) {
      return new AdaGradFastFOBOSUpdater(learningRate, expectedNumFeatures,
          L1lambda, customl1, fixedFeatures);
    }
    return new SGDUpdater(learningRate);
  }

  @Override
  public Counter<String> getGradient(Counter<String> weights,
      Sequence<IString> source, int sourceId,
      List<RichTranslation<IString, String>> translations,
      List<Sequence<IString>> references, double[] referenceWeights,
      SentenceLevelMetric<IString, String> scoreMetric) {
    return getBatchGradient(weights, Arrays.asList(source), new int[]{sourceId}, Arrays.asList(translations), Arrays.asList(references), referenceWeights, scoreMetric);
  }

  @Override
  public Counter<String> getBatchGradient(Counter<String> weights,
      List<Sequence<IString>> sources, int[] sourceIds,
      List<List<RichTranslation<IString, String>>> translations,
      List<List<Sequence<IString>>> references,
      double[] referenceWeights,
      SentenceLevelMetric<IString, String> scoreMetric) {
    Counter<String> batchGradient = new ClassicCounter<String>();

    for (int i = 0; i < sourceIds.length; i++) {
      if (translations.get(i).size() > 0) {
        // Skip decoder failures.
        Counter<String> unregularizedGradient = getUnregularizedGradient(weights, sources.get(i), sourceIds[i], translations.get(i), references.get(i), referenceWeights, scoreMetric);
        batchGradient.addAll(unregularizedGradient);
      }
    }

    // Add L2 regularization directly into the derivative
    if (this.l2Regularization) {
      final Set<String> features = new HashSet<String>(weights.keySet());
      features.addAll(weights.keySet());
      final double dataFraction = sourceIds.length /(double) tuneSetSize;
      final double scaledInvSigmaSquared = dataFraction/(2*sigmaSq);
      for (String key : features) {
        double x = weights.getCount(key);
        batchGradient.incrementCount(key, x * scaledInvSigmaSquared);
      }
    }

    return batchGradient;
  }

  abstract public Counter<String> getUnregularizedGradient(Counter<String> weights,
      Sequence<IString> source, int sourceId,
      List<RichTranslation<IString, String>> translations,
      List<Sequence<IString>> references, double[] referenceWeights,
      SentenceLevelMetric<IString, String> scoreMetric);
}
