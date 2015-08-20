package edu.stanford.nlp.mt.tm;

import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Interface for data structures that query and score rules
 * for specific inputs.
 * 
 * @author Daniel Cer
 * 
 * @param <TK>
 */
public interface TranslationModel<TK,FV> extends Cloneable {

  /**
   * Return a list of *sorted* scored rules mapped into the source sequence.
   * 
   * @param source
   * @param sourceInputProperties
   * @param sourceInputId
   * @param scorer
   * @return The rules organized into a List.
   */
  public List<ConcreteRule<TK,FV>> getRules(Sequence<TK> source, 
      InputProperties sourceInputProperties, int sourceInputId, 
      Scorer<FV> scorer);
  
  public Object clone() throws CloneNotSupportedException;

  public int maxLengthSource();
  
  public int maxLengthTarget();
  
  public void setFeaturizer(RuleFeaturizer<TK, FV> featurizer);
  
  public List<String> getFeatureNames();
  
  public String getName();
  
  public void setName(String name);
}
