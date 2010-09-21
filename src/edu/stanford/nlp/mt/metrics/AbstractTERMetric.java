package edu.stanford.nlp.mt.metrics;

/**
 * @author Michel Galley
 */
public abstract class AbstractTERMetric<TK, FV> extends AbstractMetric<TK, FV> {

  public static final int DEFAULT_TER_BEAM_WIDTH = 5; // almost as good as 20
  public static final int DEFAULT_TER_SHIFT_DIST = 12; // Yaser suggested 10; I
                                                       // set it to 2*dlimit =
                                                       // 12

  abstract public void enableFastTER();

  abstract public void setBeamWidth(int beamWidth);

  abstract public void setShiftDist(int maxShiftDist);
}
