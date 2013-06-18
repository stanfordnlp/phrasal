package edu.stanford.nlp.mt.decoder.util;

/**
 * 
 * @author danielcer
 * 
 */
public interface Beam<S extends State<S>> extends Iterable<S> {
  
  /**
	 * Should return the hypothesis that fell off the beam when state was inserted.
	 */
  S put(S state);

  /**
	 * 
	 */
  S remove();

  /**
	 * 
	 */
  S removeWorst();

  /**
	 * 
	 */
  int size();

  /**
	 * 
	 */
  int capacity();

  /**
	 * 
	 */
  double bestScore();

  /**
	 * 
	 */
  public int recombined();

  /**
	 * 
	 */
  public int preinsertionDiscarded();

  /**
	 * 
	 */
  public int pruned();

}
