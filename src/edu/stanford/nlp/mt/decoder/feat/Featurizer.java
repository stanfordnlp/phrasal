package edu.stanford.nlp.mt.decoder.feat;

/**
 * In Phrasal, feature extractors are known as "featurizers." This is the
 * top-level interface for featurizers.
 * 
 * NOTE: You must implement an interface that extends this interface
 * for the feature to fire in Phrasal. See: <code>DerivationFeaturizer</code> 
 * and <code>RuleFeaturizer</code>.
 * 
 * @author Spence Green
 *
 * @param <TK>
 * @param <FV>
 */
public interface Featurizer<TK,FV> {

  /**
   * Should return true if the featurizer wants the decoder to construct word alignments for
   * each <code>Derivation</code> (stored in <code>Featurizable</code>). Otherwise, return false.
   * 
   * @return
   */
  boolean constructInternalAlignments();
}
