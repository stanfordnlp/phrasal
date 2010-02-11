package edu.stanford.nlp.mt.decoder.feat;

import edu.stanford.nlp.mt.base.Featurizable;

/**
 *
 * @author Michel Galley
 */
public interface RichIncrementalFeaturizer<TK,FV> extends ClonedFeaturizer<TK,FV> {

  /**
   * Dump internal state of Featurizer, which is constructed using featurizable f.
	 */
	void dump(Featurizable<TK,FV> f);

  /**
   * Tells the Featurizer whether decoding is complete or not. If decoding
   * is complete, the Featurizer might generate features that are too expensive
   * to compute during beam search, which one may want to keep for rescoring
   * the n-best list (warning: if you ever rely on this feature, make sure that
   * n-best list generation is always turned on with the same n value, even
   * during test-time decoding).
   */
  void rerankingMode(boolean r);
}
