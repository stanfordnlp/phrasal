package edu.stanford.nlp.mt.decoder.feat;

/**
 * Indicates that the featurizer needs to store state in addition
 * to the default, which consists of: (1) the coverage set; (2)
 * the LM history.
 * 
 * @author Michel Galley
 */
public abstract class NeedsState<TK, FV> implements
    CombinationFeaturizer<TK, FV> {

  private static final int UNDEFINED_ID = -1;

  private int id = UNDEFINED_ID;

  public void setId(int id) {

    if (this.id != UNDEFINED_ID && this.id != id)
      throw new RuntimeException(
          "Error: setting id twice with different values.");

    this.id = id;
  }

  public int getId() {

    if (this.id == UNDEFINED_ID)
      throw new RuntimeException("Error: id not yet defined.");

    return id;
  }

}
