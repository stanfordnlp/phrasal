package edu.stanford.nlp.mt.tools;

import java.util.*;

import edu.stanford.nlp.mt.base.*;
import edu.stanford.nlp.mt.metrics.*;

/**
 * Approximate Randomization Test for Statistical Significance Testing 
 * 
 * See Reizler & Maxwell ACL 2005 Workshop on Intrinsic and Extrinsic Evaluation Measures for Machine Translation  
 * 
 * @author danielcer
 *
 */
public class SigTestApproxRand {
	static public final int SAMPLES = 1000;
	
	static double scoreList(List<Sequence<IString>> transList, EvaluationMetric<IString,String> eval) {
		IncrementalEvaluationMetric<IString, String> incEval = eval.getIncrementalMetric();
		for (Sequence<IString> trans : transList) {
			incEval.add(new ScoredFeaturizedTranslation<IString, String>(trans, null, 0));
		}
		return incEval.score();		
	}
	
	static public void main(String[] args) throws Exception {
		if (args.length != 4) {
			System.err.printf("Usage:\n\tjava edu.stanford.nlp.mt.tools.SigTestApproxRand (eval type:bleu/ter) (reference prefix) (system1.trans) (system2.trans)\n");
			System.exit(-1);
		}
		String evalMetricName = args[0];
		String referencePrefix = args[1];
		String system1TransFilename = args[2];
		String system2TransFilename = args[3];
		
		// Load everything we need
		EvaluationMetric<IString,String> eval = MetricFactory.metric(evalMetricName, referencePrefix);
		List<Sequence<IString>> system1Trans = IOTools.slurpIStringSequences(system1TransFilename);
		List<Sequence<IString>> system2Trans = IOTools.slurpIStringSequences(system2TransFilename);
		
		if (system1Trans.size() != system2Trans.size()) {
			System.err.printf("Warning: %s contains %d translations while %s contains %d translations\n", system1TransFilename, system1Trans.size(), system2TransFilename, system2Trans.size());
			int min = Math.min(system1Trans.size(), system2Trans.size());
			System.err.printf("Truncating both to %d translations\n", min);
			system1Trans = system1Trans.subList(0, min);
			system2Trans = system2Trans.subList(0, min);
		}
		
		// Compute the given metric for both system outputs
		double system1Eval = scoreList(system1Trans, eval);
		double system2Eval = scoreList(system2Trans, eval);
		
		double trueSystemDiff = Math.abs(system1Eval - system2Eval);
		
		System.out.printf("System1 Eval: %f System2 Eval: %f abs(Diff): %f\n", system1Eval, system2Eval, trueSystemDiff);
		System.out.printf("Sampling...");
		Random r = new Random(8682522807148012L);
		int matchedOrExceededDiffs = 0;
		for (int i = 0; i < SAMPLES; i++) {
			if ((i % 10) == 0) System.out.printf(".");
			List<Sequence<IString>> sample1Trans = new ArrayList<Sequence<IString>>(system1Trans.size());
			List<Sequence<IString>> sample2Trans = new ArrayList<Sequence<IString>>(system2Trans.size());
			int sz = system1Trans.size();
			for (int ii = 0; ii < sz; ii++) {
				if (r.nextDouble() >= 0.5) {
					sample1Trans.add(system1Trans.get(ii));
					sample2Trans.add(system2Trans.get(ii));
				} else {
					sample1Trans.add(system2Trans.get(ii));
					sample2Trans.add(system1Trans.get(ii));
				}
			}
			double sample1Eval = scoreList(sample1Trans,eval);
			double sample2Eval = scoreList(sample2Trans,eval);
			double sampleDiff = Math.abs(sample1Eval - sample2Eval);
			if (sampleDiff >= trueSystemDiff) matchedOrExceededDiffs++; 			
		}
		double p = (matchedOrExceededDiffs + 1.0)/(SAMPLES+1.0);
		System.out.printf("\np = %f (%d+1)/(%d+1)\n", p, matchedOrExceededDiffs, SAMPLES);
	}
}
