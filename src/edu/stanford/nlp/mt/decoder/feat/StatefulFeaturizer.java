package mt.decoder.feat;

/**
 * @author Michel Galley
 */
public abstract class StatefulFeaturizer<TK,FV> implements IncrementalFeaturizer<TK,FV> {

  private static final int UNDEFINED_ID = -1;

  private int id = UNDEFINED_ID;

  public void setId(int id) {

    if(this.id != UNDEFINED_ID && this.id != id)
      throw new RuntimeException("Error: setting id twice with different values.");
    
    this.id = id;
  }

  public int getId() {
    
    if(this.id == UNDEFINED_ID)
      throw new RuntimeException("Error: id not yet defined.");

    return id;
  }
  
}
