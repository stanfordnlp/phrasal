package edu.stanford.nlp.mt.decoder.feat;

/**
 * Any CombinationFeaturizer implementing ReorderingFeaturizer will trigger the use
 * of the reordering recombination history.
 * 
 * 
 * @author Michel Galley
 */
public interface ReorderingFeaturizer<TK, FV> extends CombinationFeaturizer<TK, FV> {
}
