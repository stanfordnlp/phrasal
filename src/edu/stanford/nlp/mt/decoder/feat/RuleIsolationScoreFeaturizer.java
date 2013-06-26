package edu.stanford.nlp.mt.decoder.feat;

/**
 * RuleFeaturizers that only contribute to the isolation score, which
 * is used by the future cost heuristics, should implement this interface.
 * The feature values will not be cached and will thus not be scored when
 * the rule is applied in a <code>Derivation</code>.
 * 
 * @author Spence Green
 *
 */
public interface RuleIsolationScoreFeaturizer<TK,FV> extends RuleFeaturizer<TK, FV> {

}
