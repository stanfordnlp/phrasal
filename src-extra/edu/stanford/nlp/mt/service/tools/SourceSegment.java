package edu.stanford.nlp.mt.service.tools;

import java.util.ArrayList;
import java.util.List;

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
    this.tokens = new ArrayList<>(numTokens);
    this.pos = new ArrayList<>(numTokens);
    this.ner = new ArrayList<>(numTokens);
    this.layoutSpec = new ArrayList<>(numTokens);
    this.chunkVector = new int[numTokens];
  }
}