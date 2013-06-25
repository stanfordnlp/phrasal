package edu.stanford.nlp.mt.decoder.feat;

/**
 * Indicates that the featurizer is not re-entrant and thus should be cloned.
 * 
 * @author Michel Galley
 * @author Spence Green
 * 
 */
public interface NeedsCloneable<TK, FV> extends Cloneable {

  public Object clone() throws CloneNotSupportedException;

}
