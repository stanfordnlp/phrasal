package edu.stanford.nlp.mt.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.InputProperties;
import edu.stanford.nlp.mt.base.InputProperty;
import edu.stanford.nlp.mt.base.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.metrics.BLEUMetric;
import edu.stanford.nlp.mt.metrics.Metrics;
import edu.stanford.nlp.mt.metrics.NISTTokenizer;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Computes NIST-tokenized, per-genre, corpus-level BLEU for a finite input set.
 * 
 * @author Spence Green
 *
 */
public class BLEUGenreEvaluator {

  private static final String DEFAULT_GENRE = "default";

  /**
   * 
   * @param referencesList
   * @param inputProperties
   * @param order
   * @param inputStream
   * @return
   * @throws IOException
   */
  public static Map<String,BLEUMetric<IString, String>.BLEUIncrementalMetric> run(List<List<Sequence<IString>>> referencesList,
      List<InputProperties> inputProperties, int order, InputStream inputStream) throws IOException {
    List<String> lines = Generics.newArrayList();
    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
    for (String line; (line = reader.readLine()) != null;) {
      lines.add(line);
    }
    return run(referencesList, inputProperties, order, lines);
  }
  
  /**
   * Run the evaluator.
   * 
   * @param referencesList
   * @param inputProperties
   * @param order
   * @param inputStream
   * @return
   * @throws IOException
   */
  public static Map<String,BLEUMetric<IString, String>.BLEUIncrementalMetric> run(List<List<Sequence<IString>>> referencesList,
      List<InputProperties> inputProperties, int order, List<String> lines) throws IOException {
    Set<String> genreList = Generics.newHashSet();
    Map<String,List<List<Sequence<IString>>>> refsByGenre = Generics.newHashMap();
    for (int sourceId = 0; sourceId < referencesList.size(); ++sourceId) {
      String genre = inputProperties.get(sourceId).containsKey(InputProperty.Domain) ? (String) inputProperties.get(sourceId).get(InputProperty.Domain)
          : DEFAULT_GENRE;
      genreList.add(genre);
      if ( ! refsByGenre.containsKey(genre)) {
        refsByGenre.put(genre, new ArrayList<List<Sequence<IString>>>());
      }
      refsByGenre.get(genre).add(referencesList.get(sourceId));
    }
    
    Map<String,BLEUMetric<IString, String>.BLEUIncrementalMetric> metrics = Generics.newHashMap(genreList.size());
    for (String genre : genreList) {
      BLEUMetric<IString, String> bleu = new BLEUMetric<IString, String>(refsByGenre.get(genre), order,
          false);
      metrics.put(genre,  bleu.getIncrementalMetric());
    }

    int sourceId = 0;
    for (String line : lines) {
      line = NISTTokenizer.tokenize(line);
      Sequence<IString> translation = IStrings.tokenize(line);
      ScoredFeaturizedTranslation<IString, String> tran = new ScoredFeaturizedTranslation<IString, String>(
          translation, null, 0);
      String genre = inputProperties.get(sourceId).containsKey(InputProperty.Domain) ? (String) inputProperties.get(sourceId).get(InputProperty.Domain)
          : DEFAULT_GENRE;
      metrics.get(genre).add(tran);
      ++sourceId;
    }
    
    return metrics;
  }
  
  
  private static String usage() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    sb.append("Usage: java ").append(BLEUMetric.class.getName()).append(" genre_file ref [ref] < candidateTranslations").append(nl);
    sb.append(nl);
    sb.append(" Options:").append(nl);
    sb.append("   -order num      : ngram order (default: 4)").append(nl);
    sb.append("   -cased          : Don't lowercase the input").append(nl);
    return sb.toString();
  }

  private static Map<String,Integer> argDefs() {
    Map<String,Integer> argDefs = new HashMap<String,Integer>();
    argDefs.put("order", 1);
    argDefs.put("cased", 0);
    return argDefs;
  }

  /**
   * 
   * @param args
   * @throws IOException
   */
  public static void main(String[] args) throws IOException {
    if (args.length < 2) {
      System.err.print(usage());
      System.exit(-1);
    }

    Properties options = StringUtils.argsToProperties(args, argDefs());
    int BLEUOrder = PropertiesUtils.getInt(options, "order", BLEUMetric.DEFAULT_MAX_NGRAM_ORDER);
    boolean doCased = PropertiesUtils.getBool(options, "cased", false);

    // Setup the metric tokenization scheme. Applies to both the references and
    // hypotheses
    if (doCased) NISTTokenizer.lowercase(false);

    // Load the references
    String[] parameters = options.getProperty("").split("\\s+");
    String[] refs = new String[parameters.length - 1];
    System.arraycopy(parameters, 1, refs, 0, refs.length);
    List<InputProperties> inputProperties = InputProperties.parse(new File(parameters[0]));
    List<List<Sequence<IString>>> referencesList = Metrics.readReferences(refs, true);
    
    Map<String,BLEUMetric<IString, String>.BLEUIncrementalMetric> metrics = 
        BLEUGenreEvaluator.run(referencesList, inputProperties, BLEUOrder, System.in);

    for (Map.Entry<String,BLEUMetric<IString, String>.BLEUIncrementalMetric> entry : metrics.entrySet()) {
      String genre = entry.getKey();
      BLEUMetric<IString, String>.BLEUIncrementalMetric incMetric = entry.getValue();
      System.out.printf("Genre: %s%n", genre);
      double[] ngramPrecisions = incMetric.ngramPrecisions();
      System.out.printf("BLEU = %.3f, ", 100 * incMetric.score());
      for (int i = 0; i < ngramPrecisions.length; i++) {
        if (i != 0) {
          System.out.print("/");
        }
        System.out.printf("%.3f", ngramPrecisions[i] * 100);
      }
      System.out.printf(" (BP=%.3f, ratio=%.3f %d/%d)%n", incMetric
          .brevityPenalty(), ((1.0 * incMetric.candidateLength()) / incMetric
              .effectiveReferenceLength()), incMetric.candidateLength(), incMetric
              .effectiveReferenceLength());

      System.out.printf("%nPrecision Details:%n");
      double[][] precCounts = incMetric.ngramPrecisionCounts();
      for (int i = 0; i < ngramPrecisions.length; i++) {
        System.out.printf("\t%d:%d/%d%n", i, (int) precCounts[i][0], (int) precCounts[i][1]);
      }
      System.out.println();
    }
  }
}
