package mt;

import edu.stanford.nlp.util.Index;

import java.util.*;
import java.io.BufferedReader;
import java.io.IOException;

import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * Extractor for printing the number of occurrences of each alignment template.
 * 
 * @author Michel Galley
 */
public class CountFeatureExtractor extends AbstractFeatureExtractor<String> {

  public static final String DEBUG_PROPERTY = "DebugPharaohFeatureExtractor";
  public static final int DEBUG_LEVEL = Integer.parseInt(System.getProperty(DEBUG_PROPERTY, "0"));

  public static final String PRINT_COUNTS_PROPERTY = "DebugPrintCounts";
  public static final boolean PRINT_COUNTS = Boolean.parseBoolean(System.getProperty(PRINT_COUNTS_PROPERTY, "false"));

  IntArrayList feCounts = new IntArrayList();

  public void extract(AlignmentTemplateInstance alTemp, AlignmentGrid alGrid) {
    addCountToArray(feCounts, alTemp.getKey());
  }

  public Object score(AlignmentTemplate alTemp) {
    int idx = alTemp.getKey();
    return new double[] { feCounts.get(idx) };
  }

  private static void addCountToArray(IntArrayList list, int idx) {
    if(idx < 0)
      return;
    while(idx >= list.size())
      list.add(0);
    int newCount = list.get(idx)+1;
    list.set(idx,newCount);
    if(DEBUG_LEVEL >= 3)
      System.err.println("Increasing count idx="+idx+" in vector ("+list+").");
  }
}
