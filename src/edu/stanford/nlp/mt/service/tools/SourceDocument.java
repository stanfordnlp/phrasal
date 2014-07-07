package edu.stanford.nlp.mt.service.tools;

import java.util.Map;

/**
 * Source document format served by the middleware application and
 * loaded by the UI.
 * 
 * @author Spence Green
 *
 */
public class SourceDocument {
  // Name of this document
  public final String docId;
  // Annotated segments, indicated with a name
  public final Map<Integer,SourceSegment> segments;
  public SourceDocument(String docId, Map<Integer,SourceSegment> segments) {
    this.docId = docId;
    this.segments = segments;
  }
}