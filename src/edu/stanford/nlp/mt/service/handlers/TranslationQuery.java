package edu.stanford.nlp.mt.service.handlers;

import java.util.List;

import edu.stanford.nlp.ling.Sentence;

/**
 * The result of a translation query.
 * 
 * @author Spence Green
 *
 */
public class TranslationQuery extends ScoredQuery  {

  public final List<String> tgt;
  public final List<String> align;
  
  /**
   * Constructor.
   * 
   * @param tgt
   * @param align
   * @param score
   */
  public TranslationQuery(List<String> tgt, List<String> align, double score) {
    super(score);
    this.tgt = tgt;
    this.align = align;
  }
  
  @Override
  public String toString() {
    return String.format("%s (%s) %.5f", Sentence.listToString(tgt), Sentence.listToString(align), score);
  }
}
