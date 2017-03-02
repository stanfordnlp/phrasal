package edu.stanford.nlp.mt.decoder.feat;

import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.Sequence;

public interface PrefixExtender<TK, FV> extends Featurizer<TK,FV>, Cloneable {
  
    /**
     * returns the extended prefix.
     * 
     * @param sourceInputId
     * @param source
     * @param targetPrefix
     */
    public abstract Sequence<TK> extendPrefix(int sourceInputId,
        Sequence<TK> source, Sequence<TK> targetPrefix, InputProperties inputProperties);
    
    public Object clone() throws CloneNotSupportedException;

}
