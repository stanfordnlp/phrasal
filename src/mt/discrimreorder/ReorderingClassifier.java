package mt.discrimreorder;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.classify.*;

import java.util.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.text.NumberFormat;

import mt.base.IOTools;
import mt.train.*;

/**
 * Read in source, target and alignment and make examples
 * for training the reordering classifier.
 * The class definition is the same as in:
 * Richard Zens and Hermann Ney. Discriminative Reordering Models for
 * Statistical Machine Translation. In HLT-NAACL 2006.
 *
 * @author Pi-Chuan Chang
 */

public class ReorderingClassifier {
  static final boolean DEBUG = false;

  static public final String F_CORPUS_OPT = "fCorpus";
  static public final String E_CORPUS_OPT = "eCorpus";
  static public final String A_CORPUS_OPT = "align";

  static final Set<String> REQUIRED_OPTS = new HashSet<String>();
  static final Set<String> OPTIONAL_OPTS = new HashSet<String>();
  static final Set<String> ALL_RECOGNIZED_OPTS = new HashSet<String>();

  static {
    REQUIRED_OPTS.addAll(
      Arrays.asList(
        F_CORPUS_OPT,
        E_CORPUS_OPT,
        A_CORPUS_OPT
        ));
    ALL_RECOGNIZED_OPTS.addAll(REQUIRED_OPTS);
    ALL_RECOGNIZED_OPTS.addAll(OPTIONAL_OPTS);
  }

  private Properties prop;
  private String fCorpus, eCorpus, align;
  
  public ReorderingClassifier(Properties prop) throws IOException {
    analyzeProperties(prop);
  }


  public void analyzeProperties(Properties prop) throws IOException {
    this.prop = prop;
    // Check required, optional properties:
    System.err.println("properties: "+prop.toString());
    if(!prop.keySet().containsAll(REQUIRED_OPTS)) {
      Set<String> missingFields = new HashSet<String>(REQUIRED_OPTS);
      missingFields.removeAll(prop.keySet());
      System.err.printf
        ("The following required fields are missing: %s\n", missingFields);
      usage();
      System.exit(1);
    }
    if(!ALL_RECOGNIZED_OPTS.containsAll(prop.keySet())) {
      Set extraFields = new HashSet<Object>(prop.keySet());
      extraFields.removeAll(ALL_RECOGNIZED_OPTS);
      System.err.printf
        ("The following fields are unrecognized: %s\n", extraFields);
      usage();
      System.exit(1);
    }

    // Analyze props:
    // Mandatory arguments:
    fCorpus = prop.getProperty(F_CORPUS_OPT);
    eCorpus = prop.getProperty(E_CORPUS_OPT);
    align = prop.getProperty(A_CORPUS_OPT);
  }


  void extractFromAlignedData() {
    long startTimeMillis = System.currentTimeMillis();
    long startStepTimeMillis = startTimeMillis;
    TwoDimensionalCounter<String,TrainingExamples.ReorderingTypes> typeCounter =
      new TwoDimensionalCounter<String,TrainingExamples.ReorderingTypes>();

    Counter<String> allTypesCounter = new IntCounter<String>();

    GeneralDataset trainDataset = new Dataset();
    List<Datum<TrainingExamples.ReorderingTypes,String>> trainData
      = new ArrayList<Datum<TrainingExamples.ReorderingTypes,String>>();
    List<Datum<TrainingExamples.ReorderingTypes,String>> devData
      = new ArrayList<Datum<TrainingExamples.ReorderingTypes,String>>();
    List<Datum<TrainingExamples.ReorderingTypes,String>> testData
      = new ArrayList<Datum<TrainingExamples.ReorderingTypes,String>>();



    try {
      LineNumberReader
        fReader = IOTools.getReaderFromFile(fCorpus),
        eReader = IOTools.getReaderFromFile(eCorpus),
        aReader = IOTools.getReaderFromFile(align);

      int lineNb=0;

      if (DEBUG) DisplayUtils.printAlignmentMatrixHeader();

      for (String fLine;; ++lineNb) {
        fLine = fReader.readLine();
        boolean done = (fLine == null);

        if (lineNb % 1000 == 0 || done) {
          long totalMemory = Runtime.getRuntime().totalMemory()/(1<<20);
          long freeMemory = Runtime.getRuntime().freeMemory()/(1<<20);
          double totalStepSecs = (System.currentTimeMillis() - startStepTimeMillis)/1000.0;
          startStepTimeMillis = System.currentTimeMillis();
          System.err.printf("line %d (secs = %.3f, totalmem = %dm, freemem = %dm)...\n",
                            lineNb, totalStepSecs, totalMemory, freeMemory);
        }


        if (done) break;

        String eLine = eReader.readLine();
        if(eLine == null)
          throw new IOException("Target-language corpus is too short!");
        String aLine = aReader.readLine();
        if(aLine == null)
          throw new IOException("Alignment file is too short!");
        if(aLine.equals(""))
          continue;

        AlignmentMatrix sent = new AlignmentMatrix(fLine, eLine, aLine);
        
        if (DEBUG) DisplayUtils.printAlignmentMatrix(sent);

        TrainingExamples exs = new TrainingExamples();
        allTypesCounter.addAll(exs.extractExamples(sent));
        FeatureExtractor extractor = new WordFeatureExtractor();

        for(TrainingExample ex : exs.examples) {

          // extract features, add datum
          List<String> features = extractor.extractFeatures(sent, ex);
          Datum<TrainingExamples.ReorderingTypes,String> d
            = new BasicDatum(features, ex.type);
          
          // split:
          // train 80%, dev 10%, test 10%
          if (lineNb % 10 == 0) {
            devData.add(d);
            typeCounter.incrementCount("dev", ex.type);
          } else if (lineNb % 10 == 1) {
            testData.add(d);
            typeCounter.incrementCount("test", ex.type);
          } else {
            trainDataset.add(d);
            trainData.add(d);
            typeCounter.incrementCount("train", ex.type);
          }
        }

        if (DEBUG) DisplayUtils.printExamples(exs);
      }
      if (DEBUG) DisplayUtils.printAlignmentMatrixBottom();
      

    } catch(IOException e) {
      e.printStackTrace();
    }
    System.out.println("=========== Overall stats ===========");
    System.out.println("allTypesCounter=\n"+allTypesCounter);
    System.out.println("typeCounter=\n"+typeCounter);
    System.out.println("trainData.size()="+trainData.size());
    System.out.println("devData.size()="+devData.size());
    System.out.println("testData.size()="+testData.size());

    // Train the classifier:
    LinearClassifierFactory<TrainingExamples.ReorderingTypes,String> factory = new LinearClassifierFactory<TrainingExamples.ReorderingTypes,String>();
    LinearClassifier<TrainingExamples.ReorderingTypes,String> classifier
      = (LinearClassifier<TrainingExamples.ReorderingTypes,String>)factory.trainClassifier(trainDataset);
   
    TwoDimensionalCounter<TrainingExamples.ReorderingTypes,TrainingExamples.ReorderingTypes> trainStats = getConfusionMatrix(trainData, classifier);
    TwoDimensionalCounter<TrainingExamples.ReorderingTypes,TrainingExamples.ReorderingTypes> devStats = getConfusionMatrix(devData, classifier);
    TwoDimensionalCounter<TrainingExamples.ReorderingTypes,TrainingExamples.ReorderingTypes> testStats = getConfusionMatrix(testData, classifier);

    System.out.println("=========== classifier stats ===========");
    System.out.println("[train]");
    System.out.println(trainStats.toCSVString(NumberFormat.getInstance()));
    DisplayUtils.resultSummary(trainStats);
    System.out.println("[dev]");
    System.out.println(devStats.toCSVString(NumberFormat.getInstance()));
    DisplayUtils.resultSummary(devStats);
    System.out.println("[test]");
    System.out.println(testStats.toCSVString(NumberFormat.getInstance()));
    DisplayUtils.resultSummary(testStats);

  }

  public TwoDimensionalCounter<TrainingExamples.ReorderingTypes,TrainingExamples.ReorderingTypes> 
    getConfusionMatrix(List<Datum<TrainingExamples.ReorderingTypes,String>> data, 
                       LinearClassifier<TrainingExamples.ReorderingTypes,String> lc) {
    TwoDimensionalCounter<TrainingExamples.ReorderingTypes,TrainingExamples.ReorderingTypes> confusionMatrix 
      = new TwoDimensionalCounter<TrainingExamples.ReorderingTypes,TrainingExamples.ReorderingTypes>();
    for (Datum<TrainingExamples.ReorderingTypes,String> d : data) {
      TrainingExamples.ReorderingTypes predictedClass = lc.classOf(d);
      confusionMatrix.incrementCount(d.label(), predictedClass);
    }
    return confusionMatrix;
  }

  static void usage() {
    System.err.print
      ("Usage: java mt.discrimreorder.ReorderingClassifier [ARGS]\n"+
       "Mandatory arguments:\n"+
       " -fCorpus <file> : source-language corpus\n"+
       " -eCorpus <file> : target-language corpus\n"+
       " -align <file> : alignment file\n");
  }


  public static void main(String[] args) {
    Properties prop = StringUtils.argsToProperties(args);
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MMM-dd hh:mm aaa");
    System.err.println("extraction started at: "+formatter.format(new Date()));

    try {
      ReorderingClassifier e = new ReorderingClassifier(prop);
      e.extractFromAlignedData();
    } catch(Exception e) {
      e.printStackTrace();
      usage();
    }

    System.err.println("extraction ended at: "+formatter.format(new Date()));
  }
}
