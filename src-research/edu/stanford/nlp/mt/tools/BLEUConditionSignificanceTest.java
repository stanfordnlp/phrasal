package edu.stanford.nlp.mt.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.mt.base.IOTools;
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
 * Runs a per-condition significance test on the output of
 * imt_extract_translations.py
 * 
 * Assumes only two conditions.
 * 
 * @author Spence Green
 *
 */
public class BLEUConditionSignificanceTest {

  private static final String DEFAULT_GENRE = "default";
  private static final int SAMPLES = 1000;
  private static final int BLEU_ORDER = 4;
  
  private static String basename(String filename) {
    return filename.substring(0, filename.lastIndexOf('.'));
  }
  
  private static List<List<String>> loadTranslations(
      Map<String, File> subjectToTgt) throws IOException {
    List<List<String>> translations = Generics.newArrayList();
    for (String subjectId : subjectToTgt.keySet()) {
      LineNumberReader reader = IOTools.getReaderFromFile(subjectToTgt.get(subjectId));
      for (String line; (line = reader.readLine()) != null;) {
        int lineId = reader.getLineNumber() - 1;
        if (lineId >= translations.size()) {
          translations.add(new ArrayList<String>());
        }
        translations.get(lineId).add(line.trim());
      }
    }
    return translations;
  }
  
  private static double computeObserved(
      Map<String, List<InputProperties>> subjectToProps,
      Map<String, File> subjectToTgt, Set<String> subjectSet, List<List<Sequence<IString>>> referencesList) throws FileNotFoundException, IOException {
    double peNumerator = 0.0;
    double imtNumerator = 0.0;
    for (String subjectId : subjectSet) {
      assert subjectToProps.containsKey(subjectId) : subjectId;
      assert subjectToTgt.containsKey(subjectId) : subjectId;
      List<InputProperties> inputProperties = subjectToProps.get(subjectId);
      Map<String,BLEUMetric<IString, String>.BLEUIncrementalMetric> metrics = 
          BLEUGenreEvaluator.run(referencesList, inputProperties, BLEU_ORDER, 
              new FileInputStream(subjectToTgt.get(subjectId)));
      double imtScore = metrics.get("imt").score();
      double peScore = metrics.get("pe").score();
      double diff = imtScore - peScore;
      System.err.printf("%s: imt: %.3f pe: %.3f diff:%.3f%n", subjectId, imtScore, peScore, diff);
      imtNumerator += imtScore;
      peNumerator += peScore;
    }
    double imtAvg = imtNumerator / (double) subjectSet.size();
    double peAvg = peNumerator / (double) subjectSet.size();
    double diff = imtAvg - peAvg;
    System.err.printf("imtAvg: %.3f peAvg: %.3f diff: %.3f%n", imtAvg, peAvg, diff);
    return diff;
  }
  
  private static double computeSampled(
      List<List<String>> translationList,
      List<List<Sequence<IString>>> referencesList) throws IOException {
    
    int size = translationList.size();
    List<String> lines = Generics.newArrayList(size);
    List<InputProperties> inputProperties = Generics.newArrayList(size);
    Random random = new Random();
    for (int i = 0; i < size; ++i) {
      boolean isPE = random.nextBoolean();
      InputProperties props = new InputProperties();
      props.put(InputProperty.Domain, isPE ? "pe" : "imt");
      inputProperties.add(props);

      List<String> candidates = translationList.get(i);
      assert candidates.size() > 0 : String.valueOf(i);
      int candidateId = random.nextInt(candidates.size());
      lines.add(candidates.get(candidateId));
    }
    Map<String,BLEUMetric<IString, String>.BLEUIncrementalMetric> metrics = 
        BLEUGenreEvaluator.run(referencesList, inputProperties, BLEU_ORDER, 
            lines);
    double imtScore = metrics.get("imt").score();
    double peScore = metrics.get("pe").score();
    double diff = imtScore - peScore;
//    System.err.printf("sample: imt: %.3f pe: %.3f diff:%.3f%n", imtScore, peScore, diff);
    return diff;
  }
  
  private static double computeBLEU(List<String> translations, List<String> references) {
    // Convert references to Sequences
    List<List<Sequence<IString>>> refSequences = Generics.newArrayList();
    for (String line : references) {
      line = NISTTokenizer.tokenize(line);
      List<Sequence<IString>> refList = Generics.newArrayList();
      refList.add(IStrings.tokenize(line));
      refSequences.add(refList);
    }
    BLEUMetric<IString, String> bleu = new BLEUMetric<IString, String>(refSequences, BLEU_ORDER,
        false);
    BLEUMetric<IString, String>.BLEUIncrementalMetric incMetric = bleu.getIncrementalMetric();

    for (String line : translations) {
      line = NISTTokenizer.tokenize(line);
      Sequence<IString> translation = IStrings.tokenize(line);
      ScoredFeaturizedTranslation<IString, String> tran = new ScoredFeaturizedTranslation<IString, String>(
          translation, null, 0);
      incMetric.add(tran);
    }
    return incMetric.score();
  }
  
  private static Map<Integer,List<String>> deepCopy(Map<Integer,List<String>> list) {
    Map<Integer,List<String>> listOfLists = Generics.newHashMap();
    for (Integer key : list.keySet()) {
      List<String> nl = Generics.newArrayList(list.get(key));
      listOfLists.put(key, nl);
    }
    return listOfLists;
  }
  
  /**
   * Textbook permutation test.
   * 
   * @param subjectToProps
   * @param subjectToTgt
   * @param subjectSet
   * @param referencesList
   * @throws IOException
   */
  private static void conventionalPermutationTest(
      Map<String, List<InputProperties>> subjectToProps,
      Map<String, File> subjectToTgt, Set<String> subjectSet,
      List<List<Sequence<IString>>> referencesList) throws IOException {

    // Read the data
    List<String> imtTranslations = Generics.newArrayList();
    List<String> imtReferences = Generics.newArrayList();
    List<Integer> imtIds = Generics.newArrayList();
    List<String> peTranslations = Generics.newArrayList();
    List<String> peReferences = Generics.newArrayList();
    List<Integer> peIds = Generics.newArrayList();
    Map<Integer,List<String>> pooledTranslations = Generics.newHashMap();
    List<String> references = Generics.newArrayList();
    for (String subjectId : subjectSet) {
      assert subjectToProps.containsKey(subjectId) : subjectId;
      assert subjectToTgt.containsKey(subjectId) : subjectId;
      List<InputProperties> inputProperties = subjectToProps.get(subjectId);
      LineNumberReader reader = IOTools.getReaderFromFile(subjectToTgt.get(subjectId));
      for (String line; (line = reader.readLine()) != null;) {
        int sourceId = reader.getLineNumber() - 1;
        String genre = inputProperties.get(sourceId).containsKey(InputProperty.Domain) ? (String) inputProperties.get(sourceId).get(InputProperty.Domain)
            : DEFAULT_GENRE;
        String refLine = referencesList.get(sourceId).get(0).toString();
        if (references.size() <= referencesList.size()) {
          references.add(refLine);
        }
        if (genre.equals("pe")) {
          peTranslations.add(line.trim());
          peReferences.add(refLine);
          peIds.add(sourceId);
        } else if(genre.equals("imt")) {
          imtTranslations.add(line.trim());
          imtReferences.add(refLine);
          imtIds.add(sourceId);
        } else {
          System.err.printf("WARNING: Discarding line %d for user %s%n", sourceId, subjectId);
          continue;
        }
        if ( ! pooledTranslations.containsKey(sourceId)) {
          List<String> l = Generics.newArrayList();
          pooledTranslations.put(sourceId, l);
        }
        pooledTranslations.get(sourceId).add(line.trim());
      }
    }
    
    // Compute the observed statistics
    final int imtSampleSize = imtReferences.size();
    final int peSampleSize = peReferences.size();
    double peScore = computeBLEU(peTranslations, peReferences);
    double imtScore = computeBLEU(imtTranslations, imtReferences);
    double observedDiff = imtScore - peScore;
    System.err.printf("observed: imt: %.4f pe: %.4f diff: %.4f%n", imtScore, peScore, observedDiff);
    
    // Sample
    Random random = new Random();
    int matchedOrExceededDiffs = 0;
    System.err.print("Sampling..");
    for (int i = 0; i < SAMPLES; i++) {
      if ((i % 50) == 0)
        System.err.printf(".");
      
      Map<Integer,List<String>> pTranslations = deepCopy(pooledTranslations);
      List<String> imtSampleTranslations = Generics.newArrayList();
      List<String> peSampleTranslations = Generics.newArrayList();

      // Sample PE translations
      for (int sourceId : peIds) {
        List<String> candidates = pTranslations.get(sourceId);
        assert candidates.size() > 0 : String.valueOf(sourceId);
        int idx = random.nextInt(candidates.size());
        peSampleTranslations.add(candidates.get(idx));
        candidates.remove(idx);
      }
      
      // Sample IMT translations
      for (int sourceId : imtIds) {
        List<String> candidates = pTranslations.get(sourceId);
        assert candidates.size() > 0 : String.valueOf(sourceId);
        int idx = random.nextInt(candidates.size());
        imtSampleTranslations.add(candidates.get(idx));
        candidates.remove(idx);
      }
      
      assert peSampleTranslations.size() == peSampleSize;
      assert imtSampleTranslations.size() == imtSampleSize;
      double peSampleScore = computeBLEU(peSampleTranslations, peReferences);
      double imtSampleScore = computeBLEU(imtSampleTranslations, imtReferences);
      double sampleDiff = imtSampleScore - peSampleScore;
//      System.err.printf("sample: imt: %.4f pe: %.4f diff: %.4f%n", imtSampleScore, peSampleScore, sampleDiff);
      
      // Two-sided test
      if (Math.abs(sampleDiff) >= Math.abs(observedDiff)) {
        matchedOrExceededDiffs++;
      }
    }
    double p = (matchedOrExceededDiffs + 1.0) / (SAMPLES + 1.0);
    System.out.printf("%np = %f (%d+1)/(%d+1)%n", p, matchedOrExceededDiffs,
        SAMPLES);
  }
  
  private static String usage() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    sb.append("Usage: java ").append(BLEUMetric.class.getName()).append(" prefix ref [ref]").append(nl);
    sb.append(nl);
    sb.append(" Options:").append(nl);
    sb.append("   -order num  : ngram order (default: 4)").append(nl);
    sb.append("   -c          : Output merged files for conventional evaluation.").append(nl);
    return sb.toString();
  }

  private static Map<String,Integer> argDefs() {
    Map<String,Integer> argDefs = new HashMap<String,Integer>();
    argDefs.put("order", 1);
    argDefs.put("c", 0);
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
    boolean doConventional = PropertiesUtils.getBool(options, "c", false);
    String[] parameters = options.getProperty("").split("\\s+");
    if (parameters.length < 2) {
      System.err.print(usage());
      System.exit(-1);
    }
    
    // Load references
    String[] refs = new String[parameters.length - 1];
    System.arraycopy(parameters, 1, refs, 0, refs.length);
    List<List<Sequence<IString>>> referencesList = Metrics.readReferences(refs, true);
    
    // Load the user files
    final String prefix = parameters[0];
    String path = "."; 
    File folder = new File(path);
    File[] files = folder.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return name.startsWith(prefix);
      }
    });
    Map<String,List<InputProperties>> subjectToProps = Generics.newHashMap();
    Map<String,File> subjectToTgt = Generics.newHashMap();
    for (File file : files) {
      String filename = file.getName();
      String subject = basename(filename);
      if (filename.endsWith("props")) {
        assert ! subjectToProps.containsKey(subject);
        subjectToProps.put(subject, InputProperties.parse(file));
      } else if(filename.endsWith("trans")) {
        assert ! subjectToTgt.containsKey(filename);
        subjectToTgt.put(subject, file);
      }
    }
    assert subjectToProps.keySet().size() == subjectToTgt.keySet().size();
    Set<String> subjectSet = Generics.newHashSet(subjectToProps.keySet());
    final double observedDifference = computeObserved(subjectToProps, subjectToTgt, subjectSet, referencesList);
    List<List<String>> translationList = loadTranslations(subjectToTgt);
    
    if (doConventional) {
      System.err.println("Generating long-format files for evaluation");
      conventionalPermutationTest(subjectToProps, subjectToTgt, subjectSet, referencesList);
      System.exit(-1);
    }
    
    // Run the significance test
    System.out.printf("Observed difference: %.3f%n", observedDifference);
    System.out.printf("Sampling... (%d iters)%n", SAMPLES);
    int matchedOrExceededDiffs = 0;
    for (int i = 0; i < SAMPLES; i++) {
      if ((i % 50) == 0)
        System.out.printf(".");
      double sampleDiff = computeSampled(translationList, referencesList);

      // Two-sided test
      if (Math.abs(sampleDiff) >= Math.abs(observedDifference))
        matchedOrExceededDiffs++;
    }
    double p = (matchedOrExceededDiffs + 1.0) / (SAMPLES + 1.0);
    System.out.printf("%np = %f (%d+1)/(%d+1)%n", p, matchedOrExceededDiffs,
        SAMPLES);
  }
}
