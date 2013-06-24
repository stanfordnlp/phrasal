package edu.stanford.nlp.mt.decoder.util;

import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationHistory;

/**
 * 
 * @author danielcer
 * 
 */
public class BeamFactory {

  static public enum BeamType {
    treebeam, sloppybeam
  }

  /**
	 * 
	 */
  private BeamFactory() {
  }

  /**
   * 
   * @param <TK>
   * @param <FV>
   */
  static public <TK, FV> Beam<Derivation<TK, FV>> factory(BeamType beamType,
      RecombinationFilter<Derivation<TK, FV>> filter, int capacity,
      RecombinationHistory<Derivation<TK, FV>> recombinationHistory) {
    switch (beamType) {
    case treebeam:
      return new TreeBeam<Derivation<TK, FV>>(capacity, filter,
          recombinationHistory);
    case sloppybeam:
      return new SloppyBeam<Derivation<TK, FV>>(capacity, filter,
          recombinationHistory);
    }

    throw new RuntimeException(String.format("Unsupported beam type %s\n",
        beamType));
  }

  /**
   * 
   * @param <TK>
   * @param <FV>
   */
  static public <TK, FV> Beam<Derivation<TK, FV>> factory(BeamType beamType,
      RecombinationFilter<Derivation<TK, FV>> filter, int capacity) {
    return factory(beamType, filter, capacity, null);
  }

}
