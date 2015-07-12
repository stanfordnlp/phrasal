package edu.stanford.nlp.mt.util;

import java.io.Serializable;
import java.util.Collection;

/**
 * @author Daniel Cer
 */
public interface FeatureValueCollection<E> extends Collection<FeatureValue<E>>,
    Cloneable,Serializable {

  public Object clone() throws CloneNotSupportedException;

}
