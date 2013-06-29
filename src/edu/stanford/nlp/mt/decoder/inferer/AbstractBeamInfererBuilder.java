package edu.stanford.nlp.mt.decoder.inferer;

import edu.stanford.nlp.mt.decoder.inferer.Inferer;
import edu.stanford.nlp.mt.decoder.util.BeamFactory;

/**
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
abstract public class AbstractBeamInfererBuilder<TK, FV> extends
    AbstractInfererBuilder<TK, FV> {

  int beamCapacity;
  BeamFactory.BeamType beamType;

  /**
	 *
	 */
  public AbstractBeamInfererBuilder(int defaultBeamCapacity,
      BeamFactory.BeamType defaultBeamType) {
    beamCapacity = defaultBeamCapacity;
    beamType = defaultBeamType;
  }

  /**
	 *
	 */
  public void setBeamType(BeamFactory.BeamType beamType) {
    this.beamType = beamType;
  }

  /**
	 *
	 */
  public AbstractBeamInfererBuilder<TK, FV> setBeamCapacity(int beamCapacity) {
    if (beamCapacity <= 0) {
      throw new RuntimeException(String.format(
          "Invalid beam capacity, %d. Beam capacity must be > 0", beamCapacity));
    }
    this.beamCapacity = beamCapacity;
    return this;
  }

  @Override
  abstract public Inferer<TK, FV> build();

  abstract public AbstractBeamInfererBuilder<TK, FV> setMaxDistortion(
      int distortionLimit);

  abstract public AbstractBeamInfererBuilder<TK, FV> useITGConstraints(
      boolean itg);
}
