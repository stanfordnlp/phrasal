package edu.stanford.nlp.mt.decoder.feat.sparse;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Map;

import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;

/**
 * Convenience functions for the sparse feature templates.
 * 
 * @author Spence Green
 *
 */
public final class SparseFeatureUtils {

  private SparseFeatureUtils() {}
  
  /**
   * Load a genre file. Mapping is zero-indexed source line number
   * to (genre,index) tuple.
   * 
   * @param filename
   * @return
   */
  public static Map<Integer,Pair<String,Integer>> loadGenreFile(String filename) {
    Map<Integer,Pair<String,Integer>> genreMap = Generics.newHashMap();
    try {
      LineNumberReader reader = IOTools.getReaderFromFile(filename);
      String genreFile = reader.readLine();
      String genreMapping = reader.readLine();
      reader.close();
      
      Map<String,Pair<String,Integer>> genreToPair = Generics.newHashMap();
      String[] pairs = genreMapping.trim().split(",");
      for (String pair: pairs) {
        String[] fields = pair.trim().split(":");
        if (fields.length != 2) throw new RuntimeException("Invalid genre specification: " + genreMapping);
        String genre = fields[0];
        int featureIndex = Integer.valueOf(fields[1]);
        genreToPair.put(genre, new Pair<String,Integer>(genre,featureIndex));
      }
      
      LineNumberReader sourceIdReader = IOTools.getReaderFromFile(genreFile);
      for (String genre; (genre = sourceIdReader.readLine()) != null; ) {
        genre = genre.trim();
        if (genre.length() == 0) continue;
        int lineId = sourceIdReader.getLineNumber()-1;
        if (genreToPair.containsKey(genre)) {
          genreMap.put(lineId, genreToPair.get(genre));
        } else {
          throw new RuntimeException(String.format("%s: %s does not contain genre %s", SparseFeatureUtils.class.getName(),
              filename, genre));
        }
      }
      sourceIdReader.close();

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return genreMap;
  }
}
