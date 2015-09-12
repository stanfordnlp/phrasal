package edu.stanford.nlp.mt.decoder.util;

import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.recomb.RecombinationHistory;

/**
 * Beam factory for @{link edu.stanford.nlp.mt.decoder.MultiBeamDecoder} and
 * {@link edu.stanford.nlp.mt.decoder.DTUDecoder}.
 * 
 * @author danielcer
 * 
 */
public final class BeamFactory {

  static public enum BeamType {
    treebeam, sloppybeam
  }

  /**
   * 
   */
  private BeamFactory() {}

  /**
   * Get a new Beam instance.
   * 
   * @param beamType
   * @param filter
   * @param capacity
   * @param recombinationHistory
   * @return
   */
  public static <TK, FV> Beam<Derivation<TK, FV>> factory(BeamType beamType,
      RecombinationFilter<Derivation<TK, FV>> filter, int capacity,
      RecombinationHistory<Derivation<TK, FV>> recombinationHistory) {

    switch (beamType) {
    case treebeam:
      return new TreeBeam<>(capacity, filter, recombinationHistory);
    case sloppybeam:
      return new SloppyBeam<>(capacity, filter, recombinationHistory);
    default:
      throw new IllegalArgumentException("Unsupported beam type: " + beamType.toString());
    } 
  }

  /**
   * Get a new Beam instance.
   * 
   * @param beamType
   * @param filter
   * @param capacity
   * @return
   */
  public static <TK, FV> Beam<Derivation<TK, FV>> factory(BeamType beamType,
      RecombinationFilter<Derivation<TK, FV>> filter, int capacity) {
    return factory(beamType, filter, capacity, null);
  }
}
