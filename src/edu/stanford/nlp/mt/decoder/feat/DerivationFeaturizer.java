package edu.stanford.nlp.mt.decoder.feat;

import java.util.List;

import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.pt.ConcreteRule;

/**
 * Extract features from partial derivations. The featurizer is called each
 * time a new rule is applied to a derivation.  initialize()
 * is called once on a new sentence.  Then, each time a derivation 
 * is extended with a new rule application, featurize is called.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 * @param <TK>
 * @param <FV>
 */
public abstract class DerivationFeaturizer<TK, FV> implements Featurizer<TK,FV> {
  /**
   * This call is made *before* decoding a new input begins.
   * 
   * @param sourceInputId
   * @param ruleList
   * @param source
   */
  public abstract void initialize(int sourceInputId,
      List<ConcreteRule<TK,FV>> ruleList, Sequence<TK> source);

  /**
   * Extract and return a list of features. If features overlap in the list, 
   * their values will be added.
   * 
   * @return a list of features or null.
   */
  public abstract List<FeatureValue<FV>> featurize(Featurizable<TK, FV> f);
    
  /**
   * DO NOT MODIFY OR OVERRIDE ANYTHING BELOW THIS LINE. PHRASAL USES THESE
   * METHODS AND FIELDS FOR INTERNAL BOOKKEEPING.
   */
  private static final int UNDEFINED_ID = -1;

  private int id = UNDEFINED_ID;

  public void setId(int id) {
    if (this.id != UNDEFINED_ID && this.id != id) {
      throw new RuntimeException(
          this.getClass().getName() + ": ERROR setting id twice with different values.");
    }
    this.id = id;
  }

  public int getId() {

    if (this.id == UNDEFINED_ID)
      throw new RuntimeException("Error: id not yet defined.");

    return id;
  }
}
