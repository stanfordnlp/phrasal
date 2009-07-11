package mt.reranker.ter;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.io.FileNotFoundException;

/**
 * A simplified version of TERtest that computes segmentwise TER scores for two
 * plain text (not SGML!) files, for (a) the system output and (b) one
 * reference translation
 * 
 * Handy for the RTE MT evaluation stuff, since firing up TERtest for each
 * and every pair of segments takes forever
 * 
 * Supports the TERtest options for mangling the input file, but does not support
 * output, multiple references, translation spans etc.
 * 
 * @author pado
 * 
 */

public class TERsegmentwise {

  private static final TERcalc ter = new TERcalc();

  /**
	 * Computes segmentwise TER for a hypothesis/reference plain text filepair.
	 * @param hypFilename
	 * @param refFilename
	 * @param costfunc
	 * @return double[] with TER scores. Currently, the number of edits and words are not returned
	 */
	public static List<Double> segmentwiseTER(String hypFilename, String refFilename, TERcost costfunc) throws FileNotFoundException, IOException {
		List<Double> segmentwise_scores = new LinkedList<Double>();
		
		BufferedReader hypstream = new BufferedReader(new FileReader(hypFilename));
		BufferedReader refstream = new BufferedReader(new FileReader(refFilename));

		String hyp;

		while ((hyp = hypstream.readLine()) != null) {
			String ref = refstream.readLine();

			// 6. compute TER

			TERalignment result = ter.TER(hyp, ref, costfunc);
			segmentwise_scores.add(result.numEdits/result.numWords);
		}
		return segmentwise_scores;
	}
	

	public static void main(String[] args) throws IOException {

		// 1. process arguments
		@SuppressWarnings("unchecked")
		HashMap<TERpara.OPTIONS, Object> paras = (HashMap<TERpara.OPTIONS, Object>)TERpara.getOpts(args);
		String ref_fn = (String) paras.get(TERpara.OPTIONS.REF);
		String hyp_fn = (String) paras.get(TERpara.OPTIONS.HYP);
		Object val = paras.get(TERpara.OPTIONS.NORMALIZE);
		boolean normalized = (Boolean) val;
		val = paras.get(TERpara.OPTIONS.CASEON);
		boolean caseon = (Boolean) val;
		val = paras.get(TERpara.OPTIONS.NOPUNCTUATION);
		boolean nopunct = (Boolean) val;
		val = paras.get(TERpara.OPTIONS.BEAMWIDTH);
		int beam_width = (Integer) val;
		val = paras.get(TERpara.OPTIONS.SHIFTDIST);
		int shift_dist = (Integer) val;

		TERcost costfunc = new TERcost();
		costfunc._delete_cost = (Double) paras.get(TERpara.OPTIONS.DELETE_COST);
		costfunc._insert_cost = (Double) paras.get(TERpara.OPTIONS.INSERT_COST);
		costfunc._shift_cost = (Double) paras.get(TERpara.OPTIONS.SHIFT_COST);
		costfunc._match_cost = (Double) paras.get(TERpara.OPTIONS.MATCH_COST);
		costfunc._substitute_cost = (Double) paras.get(TERpara.OPTIONS.SUBSTITUTE_COST);

		// set options to compute TER
		ter.setNormalize(normalized);
		ter.setCase(caseon);
		ter.setPunct(nopunct);
		ter.setBeamWidth(beam_width);
		ter.setShiftDist(shift_dist);
		
		List<Double> segmentwiseScores = segmentwiseTER(hyp_fn,ref_fn,costfunc);
		for (Double result: segmentwiseScores) {
			System.out.println("TER: " +result);
		}

	}
}