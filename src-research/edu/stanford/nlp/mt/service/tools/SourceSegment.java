package edu.stanford.nlp.mt.service.tools;

import java.util.List;

import edu.stanford.nlp.util.Generics;

/**
 * Source segment format served by the middleware and loaded by
 * the UI.
 * 
 * @author Spence Green
 *
 */
public class SourceSegment {
  public final List<String> tokens;
  public final List<String> pos;
  public final List<String> ner;
  public final List<String> layoutSpec;
  public final int[] chunkVector;
  public String inputProperties;
  
  /**
   * Constructor.
   * 
   * @param numTokens
   */
  public SourceSegment(int numTokens) {
    this.tokens = Generics.newArrayList(numTokens);
    this.pos = Generics.newArrayList(numTokens);
    this.ner = Generics.newArrayList(numTokens);
    this.layoutSpec = Generics.newArrayList(numTokens);
    this.chunkVector = new int[numTokens];
  }
}