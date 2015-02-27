package edu.stanford.nlp.mt.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;

/**
 * Cache Stanford CoreNLP annotations for lookup by source input id. Obviously,
 * this cache is only useful for fixed-size source inputs.
 * 
 * @author Spence Green
 *
 */
public class CoreNLPCache {

  // The list of raw English CoreNLP annotations
  private static Map<Integer,CoreMap> annotationMap;
  
  /**
   * Load serialized CoreNLP annotations from a file.
   *  
   * @param filename
   */
  public static int loadSerialized(String filename) {
    try {
      Annotation annotation = IOUtils.readObjectFromFile(filename);
      List<CoreMap> sentenceList = annotation.get(CoreAnnotations.SentencesAnnotation.class);

      if (sentenceList == null) {
        throw new RuntimeException("Unusable annotation (no sentences) in " + filename);
      }
      annotationMap = new HashMap<Integer,CoreMap>(sentenceList.size());
      int maxLineId = 0;
      for (CoreMap annotationSet : sentenceList) {
        // 1-indexed
        int lineId = annotationSet.get(CoreAnnotations.LineNumberAnnotation.class);
        maxLineId = lineId > maxLineId ? lineId : maxLineId;
        annotationMap.put(lineId-1, annotationSet);
      }
      return maxLineId + 1;

    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }
  
  /**
   * Return a CoreNLP annotation for the given SourceId.
   * 
   * @param sourceId
   * @return
   */
  public static CoreMap get(int sourceId) {
    return annotationMap.containsKey(sourceId) ? annotationMap.get(sourceId) : null;
  }
  
  /**
   * Return true if the cache is loaded. Otherwise, false.
   * 
   * @return
   */
  public static boolean isLoaded() { return annotationMap != null; }
  
  
  /**
   * Flushes the cache.
   */
  public static void flush() { annotationMap = null; }
}
