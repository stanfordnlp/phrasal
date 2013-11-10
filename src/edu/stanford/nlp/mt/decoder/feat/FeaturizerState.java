package edu.stanford.nlp.mt.decoder.feat;

/**
 * State declared by a featurizer.
 * 
 * @author Spence Green
 *
 */
public abstract class FeaturizerState {

  public abstract boolean equals(Object other);
  
  public abstract int hashCode();
}
