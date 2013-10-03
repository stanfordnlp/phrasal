package edu.stanford.nlp.mt.tune.optimizers;

import java.util.List;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.metrics.SentenceLevelMetric;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

/**
 * Smooth MERT (Och 2003, Cherry and Foster 2012)
 * 
 * Optimizes: E_p_w(y|x) Loss(y) latex: \mathbb{E}_{p_\theta(y|x)} \ell (y)
 * 
 * The loss function, \ell, can be any machine translation evaluation metric 
 * that operates over individual translations such as smooth BLEU or TER.
 * 
 * The derivative of this objective is given by:
 * 
 * dL/dw_i = E_p_w(y|x) [Loss(y) * f_i(x,y)] - 
 *               E_p_w(y|x) [Loss(y)] *  E_p_w(y|x) [f_i(x,y)]
 * 
 * Latex:     \mathbb{E}_{p_\theta(y|x)} (\ell (y) f_i(x,y)) - 
 *              \mathbb{E}_{p_\theta(y|x)} \ell (y) \mathbb{E}_{p_\theta(y|x)}  f_i(x,y)
 *              
 * @author Daniel Cer 
 *
 */
public class SmoothMERT extends AbstractOnlineOptimizer {

	public SmoothMERT(int tuneSetSize, int expectedNumFeatures, String[] args) {
		super(tuneSetSize, expectedNumFeatures, args);
	}	

	public SmoothMERT(int tuneSetSize, int expectedNumFeatures,
			int minFeatureSegmentCount, int gamma, int xi, double nThreshold,
			double sigma, double rate, String updaterType, double L1lambda,
			String regconfig) {
		super(tuneSetSize, expectedNumFeatures, minFeatureSegmentCount, sigma, rate, updaterType, L1lambda, regconfig);
	}

   @Override
   public Counter<String> getUnregularizedGradiant(Counter<String> weights,
         Sequence<IString> source, int sourceId,
         List<RichTranslation<IString, String>> translations,
         List<Sequence<IString>> references, double[] referenceWeights,
         SentenceLevelMetric<IString, String> scoreMetric) {
      Counter<String> expectedLossF = new ClassicCounter<String>();
      Counter<String> expectedLoss  = new ClassicCounter<String>();
      Counter<String> expectedF = new ClassicCounter<String>();
      
      Counter<String> expectedLossExpectedF = Counters.product(expectedLoss, expectedF);
      Counter<String> gradient = new ClassicCounter<String>(expectedLossF);
      Counters.subtractInPlace(gradient, expectedLossExpectedF);
      
      return gradient;
   }
}
