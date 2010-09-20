package edu.stanford.nlp.mt.base;

import java.util.Collection;

/**
 * @author Daniel Cer
 */
public interface FeatureValueCollection<E> extends Collection<FeatureValue<E>>,
    Cloneable {

  public Object clone() throws CloneNotSupportedException;

}
