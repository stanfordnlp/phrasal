package mt.discrimreorder;

import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.international.pennchinese.*;
import edu.stanford.nlp.util.StringUtils;

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

  static ChineseTreebankParserParams ctpp = new ChineseTreebankParserParams();
  static TreeReaderFactory trf = ctpp.treeReaderFactory();

  static public final String F_CORPUS_OPT = "fCorpus";
  static public final String E_CORPUS_OPT = "eCorpus";
  static public final String A_CORPUS_OPT = "align";
  static public final String F_PARSE_OPT = "fParse";
  static public final String F_PATH_OPT = "fPath";

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
    OPTIONAL_OPTS.addAll(
      Arrays.asList(
        F_PARSE_OPT,
        F_PATH_OPT
        ));
    ALL_RECOGNIZED_OPTS.addAll(REQUIRED_OPTS);
    ALL_RECOGNIZED_OPTS.addAll(OPTIONAL_OPTS);
    ALL_RECOGNIZED_OPTS.addAll(WordFeatureExtractor.OPTS);
    ALL_RECOGNIZED_OPTS.addAll(TypedDepFeatureExtractor.OPTS);
  }

  private Properties prop;
  private String fCorpus, eCorpus, align, fParse, fPath;
  private List<FeatureExtractor> extractors;
  
  public ReorderingClassifier(Properties prop) throws Exception {
    analyzeProperties(prop);
    extractors = new ArrayList<FeatureExtractor>();
    extractors.add(new WordFeatureExtractor(prop));
    extractors.add(new TypedDepFeatureExtractor(prop));
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
    fParse = prop.getProperty(F_PARSE_OPT);
    fPath = prop.getProperty(F_PATH_OPT);
  }


  void extractFromAlignedData() {
    long startTimeMillis = System.currentTimeMillis();
    long startStepTimeMillis = startTimeMillis;
    TwoDimensionalCounter<String,TrainingExamples.ReorderingTypes> typeCounter =
      new TwoDimensionalCounter<String,TrainingExamples.ReorderingTypes>();

    Counter<String> allTypesCounter = new IntCounter<String>();

    Dataset trainDataset = new Dataset();
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
        aReader = IOTools.getReaderFromFile(align),
        pReader = null,
        pathReader = null;
      if (fParse != null && fPath !=null)
        throw new RuntimeException("-fParse and -fPath should not both exist");
      if (fParse != null)
        pReader = IOTools.getReaderFromFile(fParse);
      if (fPath != null)
        pathReader = IOTools.getReaderFromFile(fPath);

      int lineNb=0;

      if (DEBUG) DisplayUtils.printAlignmentMatrixHeader();

      for (String fLine;; ++lineNb) {
        fLine = fReader.readLine();
        boolean done = (fLine == null);

        if (lineNb % 100 == 0 || done) {
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

        if(aLine.equals("")) {
          // take one more pLine so it remained synced
          if (pReader != null) {
            String pLine = pReader.readLine();
            if(pLine == null)
              throw new IOException("Target-language parses is too short!");
          }
          // take one more pathLine so it remained synced
          if (pathReader != null) {
            String pathLine = pathReader.readLine();
            if(pathLine == null)
              throw new IOException("Target-language paths is too short!");
          }
          continue;
        }

        //fLine = fixCharsInSent(fLine);
        AlignmentMatrix sent = new AlignmentMatrix(fLine, eLine, aLine);
        
        if (DEBUG) DisplayUtils.printAlignmentMatrix(sent);
        
        TrainingExamples exs = new TrainingExamples();

        allTypesCounter.addAll(exs.extractExamples(sent));

        // get the parse if the parses file exist
        if (pReader != null) {
          String pLine = pReader.readLine();
          if(pLine == null)
            throw new IOException("Target-language parses is too short!");
          pLine = fixCharsInParse(pLine);
          Tree t = Tree.valueOf("("+pLine+")", trf);
          sent.getParseInfo(t);
        }

        // get the patsh if the path file exist
        if (pathReader != null) {
          String pathLine = pathReader.readLine();
          if(pathLine == null)
            throw new IOException("Target-language paths is too short!");
          sent.getPathInfo(pathLine);
        }
        
        for(TrainingExample ex : exs.examples) {
          // extract features, add datum
          List<String> features = new ArrayList<String>();
          for (FeatureExtractor extractor : extractors) {
            features.addAll(extractor.extractFeatures(sent, ex));
          }

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
      

    } catch(Exception e) {
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
    System.out.println("Train Data set:");
    System.out.println(trainDataset.toSummaryStatistics());
    System.out.println("[train]");
    DisplayUtils.printConfusionMatrix(trainStats);
    DisplayUtils.resultSummary(trainStats);
    System.out.println("\n[dev]");
    DisplayUtils.printConfusionMatrix(devStats);
    DisplayUtils.resultSummary(devStats);
    System.out.println("\n[test]");
    DisplayUtils.printConfusionMatrix(testStats);
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

  // there are some characters that can't be corectly read in
  // by Tree.valueOf(t, ctpp.treeReaderFactory())
  // like: \ue002

  static String fixChars(String str, String toReplace) {
    str = str.replaceAll("・", toReplace);
    str = str.replaceAll("ù", toReplace);
    str = str.replaceAll("ぜ", toReplace);
    str = str.replaceAll("\u01ce", toReplace);
    str = str.replaceAll("\u0424", toReplace);
    str = str.replaceAll("\u30c1", toReplace);
    str = str.replaceAll("\u3050", toReplace);
    str = str.replaceAll("\u3077", toReplace);
    str = str.replaceAll("\u2476", toReplace);
    str = str.replaceAll("\u247f", toReplace);
    str = str.replaceAll("\u248f", toReplace);
    str = str.replaceAll("\ue002", toReplace);
    str = str.replaceAll("\ue079", toReplace);
    str = str.replaceAll("\ue0a4", toReplace);
    str = str.replaceAll("\ue0a6", toReplace);
    str = str.replaceAll("\ue0b5", toReplace);
    str = str.replaceAll("\ue0b9", toReplace);
    str = str.replaceAll("\ue0ba", toReplace);
    str = str.replaceAll("\ue0c0", toReplace);
    str = str.replaceAll("\ue0d0", toReplace);
    str = str.replaceAll("\ue0d1", toReplace);
    str = str.replaceAll("\ue0d2", toReplace);
    str = str.replaceAll("\ue0d7", toReplace);
    str = str.replaceAll("\ue0d9", toReplace);
    str = str.replaceAll("\ue11a", toReplace);
    str = str.replaceAll("\ue12b", toReplace);
    str = str.replaceAll("\ue12f", toReplace);
    str = str.replaceAll("\ue137", toReplace);
    str = str.replaceAll("\ue176", toReplace);
    str = str.replaceAll("\ue1cf", toReplace);
    str = str.replaceAll("\ue1f0", toReplace);
    str = str.replaceAll("\ue21e", toReplace);
    str = str.replaceAll("\ue22d", toReplace);
    str = str.replaceAll("\ue239", toReplace);
    str = str.replaceAll("\ue241", toReplace);
    str = str.replaceAll("\ue24f", toReplace);
    str = str.replaceAll("\ue252", toReplace);
    str = str.replaceAll("\ue27a", toReplace);
    str = str.replaceAll("\ue27c", toReplace);
    str = str.replaceAll("\ue29a", toReplace);
    str = str.replaceAll("\ue2dd", toReplace);
    str = str.replaceAll("\ue2fe", toReplace);
    str = str.replaceAll("\ue30f", toReplace);
    str = str.replaceAll("\ue35d", toReplace);
    str = str.replaceAll("\ue3aa", toReplace);
    str = str.replaceAll("\ue3ad", toReplace);
    str = str.replaceAll("\ue3b0", toReplace);
    str = str.replaceAll("\ue3b4", toReplace);
    str = str.replaceAll("\ue3b5", toReplace);
    str = str.replaceAll("\ue3bc", toReplace);
    str = str.replaceAll("\ue3c1", toReplace);
    str = str.replaceAll("\ue3cd", toReplace);
    str = str.replaceAll("\ue3cf", toReplace);
    str = str.replaceAll("\ue3f9", toReplace);
    str = str.replaceAll("\ue520", toReplace);
    str = str.replaceAll("\ue523", toReplace);
    str = str.replaceAll("\ue407", toReplace);
    str = str.replaceAll("\ue477", toReplace);
    str = str.replaceAll("\ue3b9", toReplace);
    str = str.replaceAll("\ue528", toReplace);
    return str;
  }
  static String fixCharsInParse(String str) {
    return fixChars(str, "．");
  }

  static String fixCharsInSent(String str) {
    return fixChars(str, ".");
  }
}
