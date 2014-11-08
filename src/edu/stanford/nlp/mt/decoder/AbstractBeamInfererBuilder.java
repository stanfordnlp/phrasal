package edu.stanford.nlp.mt.decoder;

import edu.stanford.nlp.mt.decoder.Inferer;
import edu.stanford.nlp.mt.decoder.util.BeamFactory;

/**
 * An abstract builder interface for beam-based inferers.
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
abstract public class AbstractBeamInfererBuilder<TK, FV> extends
    AbstractInfererBuilder<TK, FV> {

  protected int beamSize;
  protected BeamFactory.BeamType beamType;

  /**
   * Constructor.
   * 
   * @param defaultBeamCapacity
   * @param defaultBeamType
   */
  public AbstractBeamInfererBuilder(int defaultBeamCapacity,
      BeamFactory.BeamType defaultBeamType) {
    beamSize = defaultBeamCapacity;
    beamType = defaultBeamType;
  }

  /**
   * Set the beam type.
   * 
   * @param beamType
   */
  public void setBeamType(BeamFactory.BeamType beamType) {
    this.beamType = beamType;
  }

  /**
   * Set the beam size.
   * 
   * @param beamSize
   * @return
   */
  public AbstractBeamInfererBuilder<TK, FV> setBeamSize(int beamSize) {
    if (beamSize <= 0) {
      throw new RuntimeException(String.format(
          "Invalid beam capacity, %d. Beam capacity must be > 0", beamSize));
    }
    this.beamSize = beamSize;
    return this;
  }

  @Override
  abstract public Inferer<TK, FV> newInferer();

  /**
   * Set the distortion limit.
   * 
   * @param distortionLimit
   * @return
   */
  abstract public AbstractBeamInfererBuilder<TK, FV> setMaxDistortion(
      int distortionLimit);

  /**
   * Toggle ITG constraints.
   * 
   * @param itg
   * @return
   */
  abstract public AbstractBeamInfererBuilder<TK, FV> useITGConstraints(
      boolean itg);
}
