package edu.stanford.nlp.mt.tune.optimizers;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Logger;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.metrics.SentenceLevelMetric;
import edu.stanford.nlp.mt.tune.OnlineTuner;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

abstract public class AbstractOnlineOptimizer implements
      OnlineOptimizer<IString, String> {
   public static final int DEFAULT_MIN_FEATURE_SEGMENT_COUNT = 3;
   public static final double DEFAULT_SIGMA = 0.1;
   public static final double DEFAULT_RATE = 0.1;
   public static final String DEFAULT_UPDATER = "sgd";
   public static final double DEFAULT_L1 = 0;
   public static final String DEFAULT_REGCONFIG = "";

   // PRO sampling and feature filtering
   private final int minFeatureSegmentCount;
   private final int tuneSetSize;

   private final double learningRate;
   private final String updaterType;

   // Regularization fields
   private final double L1lambda;
   private boolean l2Regularization;
   private final String regconfig;

   private final Logger logger;
   private final int expectedNumFeatures;

   final double sigmaSq;
   final Random random;

   public AbstractOnlineOptimizer(int tuneSetSize, int expectedNumFeatures,
         String... args) {
      this(tuneSetSize, expectedNumFeatures,
            args != null && args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_MIN_FEATURE_SEGMENT_COUNT, 
            args != null && args.length > 1 ? Double.parseDouble(args[1]) : DEFAULT_SIGMA, 
            args != null && args.length > 2 ? Double.parseDouble(args[2]) : DEFAULT_RATE, 
            args != null && args.length > 3 ? args[3] : DEFAULT_UPDATER,
            args != null && args.length > 4 ? Double.parseDouble(args[4]) : DEFAULT_L1, 
            args != null && args.length > 5 ? args[5] : DEFAULT_REGCONFIG);
   }

   public AbstractOnlineOptimizer(int tuneSetSize, int expectedNumFeatures,
         int minFeatureSegmentCount, double sigma, double rate, String updaterType, double L1lambda,
         String regconfig) {
      if (minFeatureSegmentCount < 1)
         throw new RuntimeException("Feature segment count must be >= 1: "
               + minFeatureSegmentCount);

      this.expectedNumFeatures = expectedNumFeatures;
      this.minFeatureSegmentCount = minFeatureSegmentCount;
      this.tuneSetSize = tuneSetSize;
      this.learningRate = rate;
      this.updaterType = updaterType;
      random = new Random();

      // L1 regularization
      this.L1lambda = L1lambda;
      this.regconfig = regconfig;

      // L2 regularization
      this.l2Regularization = !Double.isInfinite(sigma);
      this.sigmaSq = l2Regularization ? sigma * sigma : 0.0;

      // Setup the logger
      logger = Logger.getLogger(PairwiseRankingOptimizerSGD.class
            .getCanonicalName());
      OnlineTuner.attach(logger);
   }

   @Override
   public OnlineUpdateRule<String> newUpdater() {
      if (this.updaterType.equalsIgnoreCase("adagrad"))
         return new AdaGradUpdater(learningRate, expectedNumFeatures);
      Counter<String> customl1 = new ClassicCounter<String>();
      try {
         Scanner scanner = new Scanner(new FileReader(regconfig));
         while (scanner.hasNextLine()) {
            String[] columns = scanner.nextLine().split(" ");
            customl1.incrementCount(columns[0], Double.parseDouble(columns[1]));
         }
         scanner.close();
         System.out.println("Using custom L1: " + customl1);
      } catch (FileNotFoundException ex) {
         System.out.println("Not using custom L1");
      }

      if (this.updaterType.equalsIgnoreCase("adagradl1"))
         return new AdaGradFOBOSUpdater(learningRate, expectedNumFeatures,
               L1lambda, AdaGradFOBOSUpdater.Norm.LASSO, customl1);
      if (this.updaterType.equalsIgnoreCase("adagradElitistLasso"))
         return new AdaGradFOBOSUpdater(learningRate, expectedNumFeatures,
               L1lambda, AdaGradFOBOSUpdater.Norm.aeLASSO, customl1);
      if (this.updaterType.equalsIgnoreCase("adagradl1f")) {
         return new AdaGradFastFOBOSUpdater(learningRate, expectedNumFeatures,
               L1lambda, customl1);
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
         Counter<String> unregularizedGradient = getUnregularizedGradiant(weights, sources.get(i), sourceIds[i], translations.get(i), references.get(i), referenceWeights, scoreMetric);
         batchGradient.addAll(unregularizedGradient);      
      }
      
      // Add L2 regularization directly into the derivative
      if (this.l2Regularization) {
        final Set<String> features = new HashSet<String>(weights.keySet());
        features.addAll(weights.keySet());
        final double dataFraction = sourceIds.length / ((double) 2*tuneSetSize);
        final double scaledSigmaSquared = sigmaSq / dataFraction;
        for (String key : features) {
          double x = weights.getCount(key);
          batchGradient.incrementCount(key, x / scaledSigmaSquared);
        }
      }

      return batchGradient;
   }

   abstract public Counter<String> getUnregularizedGradiant(Counter<String> weights,
         Sequence<IString> source, int sourceId,
         List<RichTranslation<IString, String>> translations,
         List<Sequence<IString>> references, double[] referenceWeights,
         SentenceLevelMetric<IString, String> scoreMetric);
}
