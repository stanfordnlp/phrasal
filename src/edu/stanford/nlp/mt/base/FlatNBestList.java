package edu.stanford.nlp.mt.base;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

/**
 * Naive data structure for storing n-best lists. This data structure is not memory-efficient.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 */
public class FlatNBestList implements NBestListContainer<IString, String> {

  static public final String FIELD_DELIM = "|||";

  static public final int MAX_DENSE_SIZE = 50;

  private static final int DEFAULT_INITIAL_CAPACITY = 2000;
  
  private final List<List<ScoredFeaturizedTranslation<IString, String>>> nbestLists;
  public final Index<String> featureIndex;
  public static final String DEBUG_PROPERTY = "FlatNBestListDebug";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));

  public final Map<Sequence<IString>, Sequence<IString>> sequenceSelfMap;

  public FlatNBestList(
      List<List<ScoredFeaturizedTranslation<IString, String>>> rawList) {
    this.featureIndex = null;
    sequenceSelfMap = null;
    nbestLists = new ArrayList<List<ScoredFeaturizedTranslation<IString, String>>>(
        rawList);
  }

  public FlatNBestList(String filename) throws IOException {
    this(filename, null);
  }
  
  public FlatNBestList(String filename, int initialCapacity) throws IOException {
    this(filename, null, initialCapacity);
  }

  public FlatNBestList(String filename, Index<String> featureIndex)
      throws IOException {
    this(filename, featureIndex, DEFAULT_INITIAL_CAPACITY);
  }

  public FlatNBestList(String filename, Index<String> featureIndex, int initialCapacity) throws IOException {
    this(filename, new HashMap<Sequence<IString>, Sequence<IString>>(),
        featureIndex, initialCapacity);
  }

  public FlatNBestList(String filename,
      Map<Sequence<IString>, Sequence<IString>> sequenceSelfMap,
      Index<String> featureIndex, int initialCapacity) throws IOException {
    if (featureIndex == null)
      featureIndex = new HashIndex<String>();
    this.featureIndex = featureIndex;
    this.sequenceSelfMap = sequenceSelfMap;
    Runtime rt = Runtime.getRuntime();
    long preNBestListLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    final long startTime = System.nanoTime();

    Map<String, String> featureNameSelfMap = Generics.newHashMap();

    nbestLists = Generics.newArrayList(initialCapacity);

    List<ScoredFeaturizedTranslation<IString, String>> currentNbest = 
        Generics.newLinkedList();

    LineNumberReader reader = IOTools.getReaderFromFile(filename);
    int lastId = -1;
    final String fieldDelim = Pattern.quote(FIELD_DELIM);
    for (String inline; (inline = reader.readLine()) != null;) {
      String[] fields = inline.trim().split(fieldDelim);
      if (fields.length < 3) {
        System.err.printf(
            "Warning: expected at least 3 fields, but found only %d (line %d)%n",
            fields.length, reader.getLineNumber());
        continue;
      }
      int id = Integer.valueOf(fields[0].trim());
      String translation = fields[1].trim();
      String featuresStr = fields[2].trim();
      String scoreStr = (fields.length >= 4 ? fields[3].trim() : "0");
      String latticeIdStr = (fields.length >= 5 ? fields[4].trim() : null);
      
      if (lastId >= 0 && lastId != id) {
        // n-best lists may be out of order
        while (nbestLists.size() <= lastId) {
          nbestLists.add(null);
        }
        if (nbestLists.get(lastId) != null) {
          throw new RuntimeException("N-best lists are not contiguous for id: " + String.valueOf(lastId));
        }
        nbestLists.set(lastId, new ArrayList<ScoredFeaturizedTranslation<IString, String>>(currentNbest));
        currentNbest = new LinkedList<ScoredFeaturizedTranslation<IString, String>>();
        if (DEBUG) {
          System.err.printf("Doing %s Memory: %d MiB%n", id,
              (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
        }
      }
      lastId = id;

      double score;
      try {
        score = Double.parseDouble(scoreStr);
      } catch (NumberFormatException e) {
        throw new RuntimeException(
            String
                .format(
                    "Contents of score field, '%s', cannot be parsed as a double value. (line: %d, %s)",
                    scoreStr, reader.getLineNumber(), filename));
      }

      long latticeId = -1;
      if (latticeIdStr != null) {
        if (latticeIdStr.indexOf('=') == -1) {
          try {
            latticeId = Long.parseLong(latticeIdStr);
          } catch (NumberFormatException e) {
            throw new RuntimeException(
                String
                    .format(
                        "Contents of lattice id field, '%s', cannot be parsed as a long integer value (line: %d)",
                        latticeIdStr, reader.getLineNumber()));
          }
        } else {
          // phrase alignment instead of latticeId
        }
      }

      String[] featureFields = featuresStr.split("\\s+");
      String featureName = "unlabeled";
      Map<String, List<Double>> featureMap = new HashMap<String, List<Double>>();
      featureMap.put(featureName, new ArrayList<Double>());
      for (String field : featureFields) {
        if (field.endsWith(":")) {
          featureName = field.substring(0, field.length() - 1);
          featureMap.put(featureName, new ArrayList<Double>());
          continue;
        }
        try {
          featureMap.get(featureName).add(new Double(field));
        } catch (NumberFormatException e) {
          throw new RuntimeException(
              String
                  .format(
                      "Feature value, '%s', can not be parsed as a double value. (line: %d)",
                      field, reader.getLineNumber()));
        }
      }

      List<FeatureValue<String>> featureValuesTmp = new LinkedList<FeatureValue<String>>();

      for (String feature : featureMap.keySet()) {
        if (featureIndex != null)
          featureIndex.indexOf(feature, true);
        List<Double> values = featureMap.get(feature);
        if (values.size() == 1) {
          String featureNameStored = featureNameSelfMap.get(feature);
          if (featureNameStored == null) {
            featureNameSelfMap.put(feature, feature);
            featureNameStored = feature;
          }
          featureValuesTmp.add(new FeatureValue<String>(featureNameStored,
              values.get(0), true));
        } else {
          for (int i = 0; i < values.size(); i++) {
            String composedName = feature + "_" + i;
            String featureNameStored = featureNameSelfMap.get(composedName);
            if (featureNameStored == null) {
              featureNameSelfMap.put(composedName, composedName);
              featureNameStored = composedName;
            }
            featureValuesTmp.add(new FeatureValue<String>(featureNameStored,
                values.get(i), true));
          }
        }
      }

      boolean useSparse = featureIndex.size() >= MAX_DENSE_SIZE;
      FeatureValueCollection<String> featureValues = useSparse ? new SparseFeatureValueCollection<String>(
          featureValuesTmp, featureIndex)
          : new DenseFeatureValueCollection<String>(featureValuesTmp,
              featureIndex);

      Sequence<IString> sequence = IStrings.tokenize(translation);
      Sequence<IString> sequenceStored = sequenceSelfMap.get(sequence);
      if (sequenceStored == null) {
        sequenceSelfMap.put(sequence, sequence);
        sequenceStored = sequence;
      }
      ScoredFeaturizedTranslation<IString, String> sfTrans;
      if (latticeId != -1) {
        sfTrans = new ScoredFeaturizedTranslation<IString, String>(
            sequenceStored, featureValues, score, latticeId);
      } else {
        sfTrans = new ScoredFeaturizedTranslation<IString, String>(
            sequenceStored, featureValues, score);
      }
      currentNbest.add(sfTrans);
    }
    reader.close();

    if (lastId < 0) {
      throw new RuntimeException("N-best list is empty or malformed!");
    } else if (lastId < nbestLists.size()) {
      nbestLists.set(lastId, new ArrayList<ScoredFeaturizedTranslation<IString, String>>(
        currentNbest));
    } else if (lastId == nbestLists.size()) {
      nbestLists.add(new ArrayList<ScoredFeaturizedTranslation<IString, String>>(
          currentNbest));
    } else {
      throw new RuntimeException("N-best list has some empty ids");
    }

    sequenceSelfMap = null;

    long postNBestListLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    double elapsedTime = ((double) System.nanoTime() - startTime) / 1e9;
    System.err
        .printf(
            "Done loading Flat n-best lists: %s (mem used: %d MiB time: %.3fs)%n",
            filename, (postNBestListLoadMemUsed - preNBestListLoadMemUsed)
                / (1024 * 1024), elapsedTime);
  }

  @Override
  public List<List<ScoredFeaturizedTranslation<IString, String>>> nbestLists() {
    return nbestLists;
  }

  @Override
  public String toString() {
    StringBuilder sbuf = new StringBuilder();
    String nl = System.getProperty("line.separator");
    for (int i = 0; i < nbestLists.size(); i++) {
      for (int j = 0; j < nbestLists.get(i).size(); j++) {
        ScoredFeaturizedTranslation<IString, String> tr = nbestLists.get(i)
            .get(j);
        sbuf.append(i).append(" ").append(FIELD_DELIM).append(" ");
        sbuf.append(tr.toString());
        sbuf.append(nl);
      }
    }
    return sbuf.toString();
  }

  static public void main(String[] args) throws IOException {
    if (args.length != 1) {
      System.err.printf("Usage:\n\tjava ...(flat nbest list)\n");
      System.exit(-1);
    }

    String nbestListFilename = args[0];
    FlatNBestList nbestList = new FlatNBestList(nbestListFilename);
    System.out.print(nbestList.toString());
  }

  public static String escape(String featureName) {
    return featureName.replaceAll("\\\\", "\\\\").replaceAll(" ", "\\_");
  }
}
