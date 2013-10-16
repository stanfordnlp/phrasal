package edu.stanford.nlp.mt.service.handlers;

import java.util.List;

/**
 * The result of a translation query.
 * 
 * @author Spence Green
 *
 */
public class TranslationQuery extends ScoredQuery  {

  public final List<String> tgt;
  public final List<String> align;
  
  public TranslationQuery(List<String> tgt, List<String> align, double score) {
    super(score);
    this.tgt = tgt;
    this.align = align;
  }
}
