package mt.decoder.util;

import mt.decoder.recomb.RecombinationFilter;
import mt.decoder.recomb.RecombinationHistory;


/**
 * 
 * @author danielcer
 *
 */
public class HypothesisBeamFactory {
	
	static public enum BeamType {treebeam, sloppybeam}
	
	/**
	 * 
	 */
	private HypothesisBeamFactory() { }
	
	/**
	 * 
	 * @param <TK>
	 * @param <FV>
	 * @param beamType
	 * @param filter
	 * @param capacity
	 * @param recombinationHistory
	 */
	static public <TK, FV> Beam<Hypothesis<TK, FV>> factory(BeamType beamType, RecombinationFilter<Hypothesis<TK,FV>> filter, int capacity, RecombinationHistory<Hypothesis<TK,FV>> recombinationHistory) {
		switch (beamType) {
		case treebeam:
			return new TreeBeam<Hypothesis<TK, FV>>(capacity,filter,recombinationHistory);
		case sloppybeam:
			return new SloppyBeam<Hypothesis<TK,FV>>(capacity,filter,recombinationHistory);				
		}
		
		throw new RuntimeException(String.format("Unsupported beam type %s\n", beamType));
	}
	
	/**
	 * 
	 * @param <TK>
	 * @param <FV>
	 * @param beamType
	 * @param filter
	 * @param capacity
	 */
	static public <TK, FV> Beam<Hypothesis<TK, FV>> factory(BeamType beamType, RecombinationFilter<Hypothesis<TK,FV>> filter, int capacity) {
		return factory(beamType, filter, capacity, null);
	}
	
}
