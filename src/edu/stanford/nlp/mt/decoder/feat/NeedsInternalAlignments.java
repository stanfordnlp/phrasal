package edu.stanford.nlp.mt.decoder.feat;

/**
 * An IncrementalFeaturizer implementing AlignmentFeaturizer expects
 * Featurizable's t2fAlignmentIndex and f2tAlignmentIndex to be non-null. Most
 * featurizers (e.g., baseline Moses featurizers) do not need these member
 * variables.
 * 
 * @author Michel Galley
 */
public interface NeedsInternalAlignments {
}
