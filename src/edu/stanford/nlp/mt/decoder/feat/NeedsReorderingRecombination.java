package edu.stanford.nlp.mt.decoder.feat;

/**
 * Indicates that the featurizer needs the reordering recombination history.
 * 
 * 
 * @author Michel Galley
 * @author Spence Green
 * 
 */
public interface NeedsReorderingRecombination<TK, FV> extends CombinationFeaturizer<TK, FV> {
}
