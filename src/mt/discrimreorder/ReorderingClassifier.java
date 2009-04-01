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
  static public final String TRAINALL_OPT = "trainAll";
  static public final String WRITE_CLASSIFIER_OPT = "writeClassifier";
  static public final String WRITE_HTML_OPT = "writeHTML";
  static public final String DEAL_EMPTY_OPT = "dealWithEmpty";
  static public final String DEAL_MULTITGT_OPT = "dealWithMultiTarget";
  static public final String WRITE_EXAMPLELINES_OPT = "writeExampleLines";
  static public final String USE_FOUR_CLASS_OPT = "useFourClass";

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
        F_PATH_OPT,
        TRAINALL_OPT,
        WRITE_CLASSIFIER_OPT,
        WRITE_HTML_OPT,
        DEAL_EMPTY_OPT,
        DEAL_MULTITGT_OPT,
        WRITE_EXAMPLELINES_OPT,
        USE_FOUR_CLASS_OPT
        ));
    ALL_RECOGNIZED_OPTS.addAll(REQUIRED_OPTS);
    ALL_RECOGNIZED_OPTS.addAll(OPTIONAL_OPTS);
    ALL_RECOGNIZED_OPTS.addAll(WordFeatureExtractor.OPTS);
    ALL_RECOGNIZED_OPTS.addAll(TypedDepFeatureExtractor.OPTS);
  }

  private Properties prop;
  private String fCorpus, eCorpus, align, fParse, fPath;
  private Boolean trainAll;
  private String writeClassifier, writeHTML;
  private Set<Integer> writeExampleLines = null;
  private PrintWriter htmlPW = null;
  private List<FeatureExtractor> extractors;
  private boolean dealWithEmpty, dealWithMultiTarget;
  private boolean useFourClass;
  
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
    trainAll = Boolean.parseBoolean(prop.getProperty(TRAINALL_OPT, "false"));
    writeClassifier = prop.getProperty(WRITE_CLASSIFIER_OPT);
    writeHTML = prop.getProperty(WRITE_HTML_OPT, null);
    String intsStr = prop.getProperty(WRITE_EXAMPLELINES_OPT, "");
    
    writeExampleLines = new TreeSet<Integer>();
    if (intsStr.length() > 0) {
      String[] ints = intsStr.split(",");
      for (String intStr : ints) {
        writeExampleLines.add(Integer.parseInt(intStr));
      }
    }

    dealWithEmpty = Boolean.parseBoolean(prop.getProperty(DEAL_EMPTY_OPT, "false"));
    dealWithMultiTarget = Boolean.parseBoolean(prop.getProperty(DEAL_MULTITGT_OPT, "false"));
    useFourClass = Boolean.parseBoolean(prop.getProperty(USE_FOUR_CLASS_OPT, "false"));
    if (writeHTML != null) { htmlPW = new PrintWriter(new FileWriter(writeHTML)); }

    System.out.println("========== General Properties ==========");
    System.out.printf("-%s : %s\n", F_CORPUS_OPT, fCorpus);
    System.out.printf("-%s : %s\n", E_CORPUS_OPT, eCorpus);
    System.out.printf("-%s : %s\n", A_CORPUS_OPT, align);
    System.out.printf("-%s : %s\n", F_PARSE_OPT, fParse);
    System.out.printf("-%s : %s\n", F_PATH_OPT, fPath);
    System.out.printf("-%s : %s\n", TRAINALL_OPT, trainAll);
    System.out.printf("-%s : %s\n", WRITE_CLASSIFIER_OPT, writeClassifier);
    System.out.printf("-%s : %s\n", WRITE_HTML_OPT, writeHTML);
    System.out.printf("-%s : %s\n", WRITE_EXAMPLELINES_OPT, StringUtils.join(writeExampleLines, ","));
    System.out.printf("-%s : %s\n", DEAL_EMPTY_OPT, dealWithEmpty);
    System.out.printf("-%s : %s\n", DEAL_MULTITGT_OPT, dealWithMultiTarget);
    System.out.println("========================================");
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

      if (htmlPW!=null) {
        DisplayUtils.printAlignmentMatrixHeader(htmlPW);
      }

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
        
        if (htmlPW!=null) {
          if (writeExampleLines.size()==0) {
            DisplayUtils.printAlignmentMatrix(sent, htmlPW);
          } else {
            if (writeExampleLines.contains(lineNb+1)) {
              DisplayUtils.printAlignmentMatrix(sent, htmlPW);
            }
          }
        }
        
        TrainingExamples exs = new TrainingExamples(dealWithEmpty, dealWithMultiTarget, useFourClass);

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

        if (htmlPW!=null) {
          if (writeExampleLines.size()==0) {
            DisplayUtils.printExamplesHeader(htmlPW);
          } else {
            if (writeExampleLines.contains(lineNb+1)) {
              DisplayUtils.printExamplesHeader(htmlPW);
            }
          }
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
            if (trainAll) {
              trainDataset.add(d);
              trainData.add(d);
              typeCounter.incrementCount("train", ex.type);
            }
          } else if (lineNb % 10 == 1) {
            testData.add(d);
            typeCounter.incrementCount("test", ex.type);
            if (trainAll) {
              trainDataset.add(d);
              trainData.add(d);
              typeCounter.incrementCount("train", ex.type);
            }
          } else {
            trainDataset.add(d);
            trainData.add(d);
            typeCounter.incrementCount("train", ex.type);
          }

          if (htmlPW!=null) {
            if (writeExampleLines.size()==0) {
              DisplayUtils.printExample(ex, features, htmlPW);
            } else {
              if (writeExampleLines.contains(lineNb+1)) {
                DisplayUtils.printExample(ex, features, htmlPW);
              }
            }
          }
        }
        if (htmlPW!=null) {
          if (writeExampleLines.size()==0) {
            DisplayUtils.printExamplesBottom(htmlPW);
          } else {
            if (writeExampleLines.contains(lineNb+1)) {
              DisplayUtils.printExamplesBottom(htmlPW);
            }
          }
        }
      }
      if (htmlPW!=null) DisplayUtils.printAlignmentMatrixBottom(htmlPW);
      

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

    if (writeClassifier != null) {
      LinearClassifier.writeClassifier(classifier, writeClassifier);
      System.err.println("Classifier Written to "+writeClassifier);
    }
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
  // TODO: This really should be done by checking the range in CHTBLexer, 
  // instead of checking each case
  static String fixChars(String str, String toReplace) {
    str = str.replaceAll("\u00e1", toReplace);
    str = str.replaceAll("\u00e8", toReplace);
    str = str.replaceAll("\u00e9", toReplace);
    str = str.replaceAll("\u00ea", toReplace);
    str = str.replaceAll("\u00ed", toReplace);
    str = str.replaceAll("\u00f2", toReplace);
    str = str.replaceAll("\u00f7", toReplace);
    str = str.replaceAll("\u00f9", toReplace);
    str = str.replaceAll("\u00fa", toReplace);
    str = str.replaceAll("\u00fc", toReplace);
    str = str.replaceAll("\u0101", toReplace);
    str = str.replaceAll("\u0113", toReplace);
    str = str.replaceAll("\u016b", toReplace);
    str = str.replaceAll("\u01ce", toReplace);
    str = str.replaceAll("\u01d4", toReplace);
    str = str.replaceAll("\u01d6", toReplace);
    str = str.replaceAll("\u01d8", toReplace);
    str = str.replaceAll("\u01da", toReplace);
    str = str.replaceAll("\u01dc", toReplace);
    str = str.replaceAll("\u02c7", toReplace);
    str = str.replaceAll("\u02d9", toReplace);
    str = str.replaceAll("\u0391", toReplace);
    str = str.replaceAll("\u0393", toReplace);
    str = str.replaceAll("\u0398", toReplace);
    str = str.replaceAll("\u039b", toReplace);
    str = str.replaceAll("\u03a3", toReplace);
    str = str.replaceAll("\u03a4", toReplace);
    str = str.replaceAll("\u03a5", toReplace);
    str = str.replaceAll("\u03a6", toReplace);
    str = str.replaceAll("\u03a7", toReplace);
    str = str.replaceAll("\u03a8", toReplace);
    str = str.replaceAll("\u03a9", toReplace);
    str = str.replaceAll("\u03b1", toReplace);
    str = str.replaceAll("\u03b2", toReplace);
    str = str.replaceAll("\u03b3", toReplace);
    str = str.replaceAll("\u03b8", toReplace);
    str = str.replaceAll("\u03ba", toReplace);
    str = str.replaceAll("\u03bc", toReplace);
    str = str.replaceAll("\u03bd", toReplace);
    str = str.replaceAll("\u03bf", toReplace);
    str = str.replaceAll("\u03c0", toReplace);
    str = str.replaceAll("\u03c1", toReplace);
    str = str.replaceAll("\u03c3", toReplace);
    str = str.replaceAll("\u03c4", toReplace);
    str = str.replaceAll("\u03c5", toReplace);
    str = str.replaceAll("\u03c7", toReplace);
    str = str.replaceAll("\u03c8", toReplace);
    str = str.replaceAll("\u0401", toReplace);
    str = str.replaceAll("\u0410", toReplace);
    str = str.replaceAll("\u0412", toReplace);
    str = str.replaceAll("\u0415", toReplace);
    str = str.replaceAll("\u0416", toReplace);
    str = str.replaceAll("\u0417", toReplace);
    str = str.replaceAll("\u041b", toReplace);
    str = str.replaceAll("\u041e", toReplace);
    str = str.replaceAll("\u041f", toReplace);
    str = str.replaceAll("\u0420", toReplace);
    str = str.replaceAll("\u0421", toReplace);
    str = str.replaceAll("\u0422", toReplace);
    str = str.replaceAll("\u0423", toReplace);
    str = str.replaceAll("\u0424", toReplace);
    str = str.replaceAll("\u0425", toReplace);
    str = str.replaceAll("\u0427", toReplace);
    str = str.replaceAll("\u0428", toReplace);
    str = str.replaceAll("\u042a", toReplace);
    str = str.replaceAll("\u042b", toReplace);
    str = str.replaceAll("\u042c", toReplace);
    str = str.replaceAll("\u042e", toReplace);
    str = str.replaceAll("\u042f", toReplace);
    str = str.replaceAll("\u0430", toReplace);
    str = str.replaceAll("\u0431", toReplace);
    str = str.replaceAll("\u0432", toReplace);
    str = str.replaceAll("\u0433", toReplace);
    str = str.replaceAll("\u0434", toReplace);
    str = str.replaceAll("\u0435", toReplace);
    str = str.replaceAll("\u0437", toReplace);
    str = str.replaceAll("\u0439", toReplace);
    str = str.replaceAll("\u043b", toReplace);
    str = str.replaceAll("\u043d", toReplace);
    str = str.replaceAll("\u043e", toReplace);
    str = str.replaceAll("\u0449", toReplace);
    str = str.replaceAll("\u044c", toReplace);
    str = str.replaceAll("\u0451", toReplace);
    str = str.replaceAll("\u2116", toReplace);
    str = str.replaceAll("\u2192", toReplace);
    str = str.replaceAll("\u2208", toReplace);
    str = str.replaceAll("\u220f", toReplace);
    str = str.replaceAll("\u2211", toReplace);
    str = str.replaceAll("\u2220", toReplace);
    str = str.replaceAll("\u2223", toReplace);
    str = str.replaceAll("\u2225", toReplace);
    str = str.replaceAll("\u2227", toReplace);
    str = str.replaceAll("\u2229", toReplace);
    str = str.replaceAll("\u222a", toReplace);
    str = str.replaceAll("\u2237", toReplace);
    str = str.replaceAll("\u223d", toReplace);
    str = str.replaceAll("\u2248", toReplace);
    str = str.replaceAll("\u224c", toReplace);
    str = str.replaceAll("\u2261", toReplace);
    str = str.replaceAll("\u2299", toReplace);
    str = str.replaceAll("\u22a5", toReplace);
    str = str.replaceAll("\u2312", toReplace);
    str = str.replaceAll("\u2460", toReplace);
    str = str.replaceAll("\u2461", toReplace);
    str = str.replaceAll("\u2462", toReplace);
    str = str.replaceAll("\u2463", toReplace);
    str = str.replaceAll("\u2464", toReplace);
    str = str.replaceAll("\u2467", toReplace);
    str = str.replaceAll("\u2474", toReplace);
    str = str.replaceAll("\u2475", toReplace);
    str = str.replaceAll("\u2476", toReplace);
    str = str.replaceAll("\u2477", toReplace);
    str = str.replaceAll("\u2478", toReplace);
    str = str.replaceAll("\u2479", toReplace);
    str = str.replaceAll("\u247a", toReplace);
    str = str.replaceAll("\u247b", toReplace);
    str = str.replaceAll("\u247c", toReplace);
    str = str.replaceAll("\u247d", toReplace);
    str = str.replaceAll("\u247e", toReplace);
    str = str.replaceAll("\u247f", toReplace);
    str = str.replaceAll("\u2480", toReplace);
    str = str.replaceAll("\u2481", toReplace);
    str = str.replaceAll("\u2482", toReplace);
    str = str.replaceAll("\u2483", toReplace);
    str = str.replaceAll("\u2484", toReplace);
    str = str.replaceAll("\u2485", toReplace);
    str = str.replaceAll("\u2486", toReplace);
    str = str.replaceAll("\u2488", toReplace);
    str = str.replaceAll("\u2489", toReplace);
    str = str.replaceAll("\u248a", toReplace);
    str = str.replaceAll("\u248b", toReplace);
    str = str.replaceAll("\u248c", toReplace);
    str = str.replaceAll("\u248d", toReplace);
    str = str.replaceAll("\u248e", toReplace);
    str = str.replaceAll("\u248f", toReplace);
    str = str.replaceAll("\u2490", toReplace);
    str = str.replaceAll("\u2491", toReplace);
    str = str.replaceAll("\u2492", toReplace);
    str = str.replaceAll("\u2493", toReplace);
    str = str.replaceAll("\u2494", toReplace);
    str = str.replaceAll("\u2495", toReplace);
    str = str.replaceAll("\u2496", toReplace);
    str = str.replaceAll("\u2497", toReplace);
    str = str.replaceAll("\u2498", toReplace);
    str = str.replaceAll("\u2499", toReplace);
    str = str.replaceAll("\u249a", toReplace);
    str = str.replaceAll("\u249b", toReplace);
    str = str.replaceAll("\u2605", toReplace);
    str = str.replaceAll("\u2606", toReplace);
    str = str.replaceAll("\u2640", toReplace);
    str = str.replaceAll("\u3041", toReplace);
    str = str.replaceAll("\u3042", toReplace);
    str = str.replaceAll("\u3043", toReplace);
    str = str.replaceAll("\u3044", toReplace);
    str = str.replaceAll("\u304a", toReplace);
    str = str.replaceAll("\u304b", toReplace);
    str = str.replaceAll("\u304d", toReplace);
    str = str.replaceAll("\u304f", toReplace);
    str = str.replaceAll("\u3050", toReplace);
    str = str.replaceAll("\u3051", toReplace);
    str = str.replaceAll("\u3052", toReplace);
    str = str.replaceAll("\u3053", toReplace);
    str = str.replaceAll("\u3054", toReplace);
    str = str.replaceAll("\u3055", toReplace);
    str = str.replaceAll("\u3056", toReplace);
    str = str.replaceAll("\u3057", toReplace);
    str = str.replaceAll("\u3058", toReplace);
    str = str.replaceAll("\u3059", toReplace);
    str = str.replaceAll("\u305a", toReplace);
    str = str.replaceAll("\u305b", toReplace);
    str = str.replaceAll("\u305c", toReplace);
    str = str.replaceAll("\u305d", toReplace);
    str = str.replaceAll("\u305e", toReplace);
    str = str.replaceAll("\u305f", toReplace);
    str = str.replaceAll("\u3060", toReplace);
    str = str.replaceAll("\u3061", toReplace);
    str = str.replaceAll("\u3062", toReplace);
    str = str.replaceAll("\u3063", toReplace);
    str = str.replaceAll("\u3064", toReplace);
    str = str.replaceAll("\u3066", toReplace);
    str = str.replaceAll("\u3067", toReplace);
    str = str.replaceAll("\u3068", toReplace);
    str = str.replaceAll("\u3069", toReplace);
    str = str.replaceAll("\u306a", toReplace);
    str = str.replaceAll("\u306b", toReplace);
    str = str.replaceAll("\u306d", toReplace);
    str = str.replaceAll("\u306e", toReplace);
    str = str.replaceAll("\u306f", toReplace);
    str = str.replaceAll("\u3070", toReplace);
    str = str.replaceAll("\u3071", toReplace);
    str = str.replaceAll("\u3072", toReplace);
    str = str.replaceAll("\u3073", toReplace);
    str = str.replaceAll("\u3074", toReplace);
    str = str.replaceAll("\u3075", toReplace);
    str = str.replaceAll("\u3076", toReplace);
    str = str.replaceAll("\u3077", toReplace);
    str = str.replaceAll("\u307e", toReplace);
    str = str.replaceAll("\u3080", toReplace);
    str = str.replaceAll("\u3081", toReplace);
    str = str.replaceAll("\u3083", toReplace);
    str = str.replaceAll("\u3088", toReplace);
    str = str.replaceAll("\u3089", toReplace);
    str = str.replaceAll("\u308b", toReplace);
    str = str.replaceAll("\u308c", toReplace);
    str = str.replaceAll("\u308f", toReplace);
    str = str.replaceAll("\u3091", toReplace);
    str = str.replaceAll("\u3093", toReplace);
    str = str.replaceAll("\u309e", toReplace);
    str = str.replaceAll("\u30a1", toReplace);
    str = str.replaceAll("\u30a3", toReplace);
    str = str.replaceAll("\u30a6", toReplace);
    str = str.replaceAll("\u30a7", toReplace);
    str = str.replaceAll("\u30aa", toReplace);
    str = str.replaceAll("\u30ab", toReplace);
    str = str.replaceAll("\u30ac", toReplace);
    str = str.replaceAll("\u30ad", toReplace);
    str = str.replaceAll("\u30af", toReplace);
    str = str.replaceAll("\u30b1", toReplace);
    str = str.replaceAll("\u30b2", toReplace);
    str = str.replaceAll("\u30b5", toReplace);
    str = str.replaceAll("\u30b7", toReplace);
    str = str.replaceAll("\u30b9", toReplace);
    str = str.replaceAll("\u30ba", toReplace);
    str = str.replaceAll("\u30bb", toReplace);
    str = str.replaceAll("\u30bd", toReplace);
    str = str.replaceAll("\u30bf", toReplace);
    str = str.replaceAll("\u30c0", toReplace);
    str = str.replaceAll("\u30c1", toReplace);
    str = str.replaceAll("\u30c3", toReplace);
    str = str.replaceAll("\u30c4", toReplace);
    str = str.replaceAll("\u30c7", toReplace);
    str = str.replaceAll("\u30c8", toReplace);
    str = str.replaceAll("\u30cc", toReplace);
    str = str.replaceAll("\u30ce", toReplace);
    str = str.replaceAll("\u30d2", toReplace);
    str = str.replaceAll("\u30d7", toReplace);
    str = str.replaceAll("\u30df", toReplace);
    str = str.replaceAll("\u30eb", toReplace);
    str = str.replaceAll("\u30ec", toReplace);
    str = str.replaceAll("\u30ef", toReplace);
    str = str.replaceAll("\u30f0", toReplace);
    str = str.replaceAll("\u30f4", toReplace);
    str = str.replaceAll("\u30f6", toReplace);
    str = str.replaceAll("\u30f9", toReplace);
    str = str.replaceAll("\u30fb", toReplace);
    str = str.replaceAll("\u30fe", toReplace);
    str = str.replaceAll("\ue000", toReplace);
    str = str.replaceAll("\ue002", toReplace);
    str = str.replaceAll("\ue00f", toReplace);
    str = str.replaceAll("\ue029", toReplace);
    str = str.replaceAll("\ue02a", toReplace);
    str = str.replaceAll("\ue02b", toReplace);
    str = str.replaceAll("\ue02c", toReplace);
    str = str.replaceAll("\ue02d", toReplace);
    str = str.replaceAll("\ue030", toReplace);
    str = str.replaceAll("\ue057", toReplace);
    str = str.replaceAll("\ue058", toReplace);
    str = str.replaceAll("\ue059", toReplace);
    str = str.replaceAll("\ue05a", toReplace);
    str = str.replaceAll("\ue05b", toReplace);
    str = str.replaceAll("\ue05d", toReplace);
    str = str.replaceAll("\ue05e", toReplace);
    str = str.replaceAll("\ue05f", toReplace);
    str = str.replaceAll("\ue060", toReplace);
    str = str.replaceAll("\ue062", toReplace);
    str = str.replaceAll("\ue063", toReplace);
    str = str.replaceAll("\ue064", toReplace);
    str = str.replaceAll("\ue065", toReplace);
    str = str.replaceAll("\ue073", toReplace);
    str = str.replaceAll("\ue074", toReplace);
    str = str.replaceAll("\ue075", toReplace);
    str = str.replaceAll("\ue076", toReplace);
    str = str.replaceAll("\ue078", toReplace);
    str = str.replaceAll("\ue079", toReplace);
    str = str.replaceAll("\ue07a", toReplace);
    str = str.replaceAll("\ue07b", toReplace);
    str = str.replaceAll("\ue07c", toReplace);
    str = str.replaceAll("\ue092", toReplace);
    str = str.replaceAll("\ue094", toReplace);
    str = str.replaceAll("\ue09c", toReplace);
    str = str.replaceAll("\ue0a4", toReplace);
    str = str.replaceAll("\ue0a6", toReplace);
    str = str.replaceAll("\ue0a8", toReplace);
    str = str.replaceAll("\ue0b3", toReplace);
    str = str.replaceAll("\ue0b4", toReplace);
    str = str.replaceAll("\ue0b5", toReplace);
    str = str.replaceAll("\ue0b6", toReplace);
    str = str.replaceAll("\ue0b7", toReplace);
    str = str.replaceAll("\ue0b8", toReplace);
    str = str.replaceAll("\ue0b9", toReplace);
    str = str.replaceAll("\ue0ba", toReplace);
    str = str.replaceAll("\ue0bb", toReplace);
    str = str.replaceAll("\ue0bc", toReplace);
    str = str.replaceAll("\ue0bd", toReplace);
    str = str.replaceAll("\ue0be", toReplace);
    str = str.replaceAll("\ue0bf", toReplace);
    str = str.replaceAll("\ue0c0", toReplace);
    str = str.replaceAll("\ue0c1", toReplace);
    str = str.replaceAll("\ue0c2", toReplace);
    str = str.replaceAll("\ue0c4", toReplace);
    str = str.replaceAll("\ue0c5", toReplace);
    str = str.replaceAll("\ue0c6", toReplace);
    str = str.replaceAll("\ue0c7", toReplace);
    str = str.replaceAll("\ue0c8", toReplace);
    str = str.replaceAll("\ue0c9", toReplace);
    str = str.replaceAll("\ue0ca", toReplace);
    str = str.replaceAll("\ue0cb", toReplace);
    str = str.replaceAll("\ue0cc", toReplace);
    str = str.replaceAll("\ue0cd", toReplace);
    str = str.replaceAll("\ue0ce", toReplace);
    str = str.replaceAll("\ue0cf", toReplace);
    str = str.replaceAll("\ue0d0", toReplace);
    str = str.replaceAll("\ue0d1", toReplace);
    str = str.replaceAll("\ue0d2", toReplace);
    str = str.replaceAll("\ue0d3", toReplace);
    str = str.replaceAll("\ue0d4", toReplace);
    str = str.replaceAll("\ue0d5", toReplace);
    str = str.replaceAll("\ue0d6", toReplace);
    str = str.replaceAll("\ue0d7", toReplace);
    str = str.replaceAll("\ue0d8", toReplace);
    str = str.replaceAll("\ue0d9", toReplace);
    str = str.replaceAll("\ue0da", toReplace);
    str = str.replaceAll("\ue0e4", toReplace);
    str = str.replaceAll("\ue102", toReplace);
    str = str.replaceAll("\ue111", toReplace);
    str = str.replaceAll("\ue112", toReplace);
    str = str.replaceAll("\ue113", toReplace);
    str = str.replaceAll("\ue114", toReplace);
    str = str.replaceAll("\ue115", toReplace);
    str = str.replaceAll("\ue116", toReplace);
    str = str.replaceAll("\ue117", toReplace);
    str = str.replaceAll("\ue118", toReplace);
    str = str.replaceAll("\ue119", toReplace);
    str = str.replaceAll("\ue11a", toReplace);
    str = str.replaceAll("\ue11b", toReplace);
    str = str.replaceAll("\ue11d", toReplace);
    str = str.replaceAll("\ue11e", toReplace);
    str = str.replaceAll("\ue11f", toReplace);
    str = str.replaceAll("\ue120", toReplace);
    str = str.replaceAll("\ue121", toReplace);
    str = str.replaceAll("\ue122", toReplace);
    str = str.replaceAll("\ue123", toReplace);
    str = str.replaceAll("\ue124", toReplace);
    str = str.replaceAll("\ue125", toReplace);
    str = str.replaceAll("\ue126", toReplace);
    str = str.replaceAll("\ue127", toReplace);
    str = str.replaceAll("\ue128", toReplace);
    str = str.replaceAll("\ue129", toReplace);
    str = str.replaceAll("\ue12a", toReplace);
    str = str.replaceAll("\ue12b", toReplace);
    str = str.replaceAll("\ue12c", toReplace);
    str = str.replaceAll("\ue12d", toReplace);
    str = str.replaceAll("\ue12e", toReplace);
    str = str.replaceAll("\ue12f", toReplace);
    str = str.replaceAll("\ue130", toReplace);
    str = str.replaceAll("\ue132", toReplace);
    str = str.replaceAll("\ue133", toReplace);
    str = str.replaceAll("\ue134", toReplace);
    str = str.replaceAll("\ue135", toReplace);
    str = str.replaceAll("\ue136", toReplace);
    str = str.replaceAll("\ue137", toReplace);
    str = str.replaceAll("\ue138", toReplace);
    str = str.replaceAll("\ue160", toReplace);
    str = str.replaceAll("\ue162", toReplace);
    str = str.replaceAll("\ue16f", toReplace);
    str = str.replaceAll("\ue170", toReplace);
    str = str.replaceAll("\ue171", toReplace);
    str = str.replaceAll("\ue172", toReplace);
    str = str.replaceAll("\ue173", toReplace);
    str = str.replaceAll("\ue174", toReplace);
    str = str.replaceAll("\ue175", toReplace);
    str = str.replaceAll("\ue176", toReplace);
    str = str.replaceAll("\ue177", toReplace);
    str = str.replaceAll("\ue178", toReplace);
    str = str.replaceAll("\ue179", toReplace);
    str = str.replaceAll("\ue17a", toReplace);
    str = str.replaceAll("\ue17b", toReplace);
    str = str.replaceAll("\ue17c", toReplace);
    str = str.replaceAll("\ue17d", toReplace);
    str = str.replaceAll("\ue17e", toReplace);
    str = str.replaceAll("\ue17f", toReplace);
    str = str.replaceAll("\ue180", toReplace);
    str = str.replaceAll("\ue181", toReplace);
    str = str.replaceAll("\ue182", toReplace);
    str = str.replaceAll("\ue183", toReplace);
    str = str.replaceAll("\ue184", toReplace);
    str = str.replaceAll("\ue185", toReplace);
    str = str.replaceAll("\ue186", toReplace);
    str = str.replaceAll("\ue187", toReplace);
    str = str.replaceAll("\ue189", toReplace);
    str = str.replaceAll("\ue18a", toReplace);
    str = str.replaceAll("\ue18b", toReplace);
    str = str.replaceAll("\ue18c", toReplace);
    str = str.replaceAll("\ue18d", toReplace);
    str = str.replaceAll("\ue18e", toReplace);
    str = str.replaceAll("\ue18f", toReplace);
    str = str.replaceAll("\ue190", toReplace);
    str = str.replaceAll("\ue191", toReplace);
    str = str.replaceAll("\ue192", toReplace);
    str = str.replaceAll("\ue193", toReplace);
    str = str.replaceAll("\ue194", toReplace);
    str = str.replaceAll("\ue195", toReplace);
    str = str.replaceAll("\ue196", toReplace);
    str = str.replaceAll("\ue1be", toReplace);
    str = str.replaceAll("\ue1c0", toReplace);
    str = str.replaceAll("\ue1ce", toReplace);
    str = str.replaceAll("\ue1cf", toReplace);
    str = str.replaceAll("\ue1d1", toReplace);
    str = str.replaceAll("\ue1d2", toReplace);
    str = str.replaceAll("\ue1d3", toReplace);
    str = str.replaceAll("\ue1d4", toReplace);
    str = str.replaceAll("\ue1d6", toReplace);
    str = str.replaceAll("\ue1d7", toReplace);
    str = str.replaceAll("\ue1d8", toReplace);
    str = str.replaceAll("\ue1d9", toReplace);
    str = str.replaceAll("\ue1da", toReplace);
    str = str.replaceAll("\ue1db", toReplace);
    str = str.replaceAll("\ue1dc", toReplace);
    str = str.replaceAll("\ue1dd", toReplace);
    str = str.replaceAll("\ue1de", toReplace);
    str = str.replaceAll("\ue1e0", toReplace);
    str = str.replaceAll("\ue1e2", toReplace);
    str = str.replaceAll("\ue1e3", toReplace);
    str = str.replaceAll("\ue1e5", toReplace);
    str = str.replaceAll("\ue1e6", toReplace);
    str = str.replaceAll("\ue1e7", toReplace);
    str = str.replaceAll("\ue1ea", toReplace);
    str = str.replaceAll("\ue1eb", toReplace);
    str = str.replaceAll("\ue1ec", toReplace);
    str = str.replaceAll("\ue1ed", toReplace);
    str = str.replaceAll("\ue1ee", toReplace);
    str = str.replaceAll("\ue1ef", toReplace);
    str = str.replaceAll("\ue1f0", toReplace);
    str = str.replaceAll("\ue1f1", toReplace);
    str = str.replaceAll("\ue1f2", toReplace);
    str = str.replaceAll("\ue1f3", toReplace);
    str = str.replaceAll("\ue1f4", toReplace);
    str = str.replaceAll("\ue21c", toReplace);
    str = str.replaceAll("\ue21e", toReplace);
    str = str.replaceAll("\ue22b", toReplace);
    str = str.replaceAll("\ue22c", toReplace);
    str = str.replaceAll("\ue22d", toReplace);
    str = str.replaceAll("\ue22e", toReplace);
    str = str.replaceAll("\ue22f", toReplace);
    str = str.replaceAll("\ue230", toReplace);
    str = str.replaceAll("\ue231", toReplace);
    str = str.replaceAll("\ue232", toReplace);
    str = str.replaceAll("\ue233", toReplace);
    str = str.replaceAll("\ue234", toReplace);
    str = str.replaceAll("\ue235", toReplace);
    str = str.replaceAll("\ue236", toReplace);
    str = str.replaceAll("\ue237", toReplace);
    str = str.replaceAll("\ue238", toReplace);
    str = str.replaceAll("\ue239", toReplace);
    str = str.replaceAll("\ue23b", toReplace);
    str = str.replaceAll("\ue23c", toReplace);
    str = str.replaceAll("\ue23d", toReplace);
    str = str.replaceAll("\ue23e", toReplace);
    str = str.replaceAll("\ue23f", toReplace);
    str = str.replaceAll("\ue240", toReplace);
    str = str.replaceAll("\ue241", toReplace);
    str = str.replaceAll("\ue242", toReplace);
    str = str.replaceAll("\ue243", toReplace);
    str = str.replaceAll("\ue244", toReplace);
    str = str.replaceAll("\ue245", toReplace);
    str = str.replaceAll("\ue246", toReplace);
    str = str.replaceAll("\ue247", toReplace);
    str = str.replaceAll("\ue248", toReplace);
    str = str.replaceAll("\ue249", toReplace);
    str = str.replaceAll("\ue24a", toReplace);
    str = str.replaceAll("\ue24b", toReplace);
    str = str.replaceAll("\ue24d", toReplace);
    str = str.replaceAll("\ue24e", toReplace);
    str = str.replaceAll("\ue24f", toReplace);
    str = str.replaceAll("\ue250", toReplace);
    str = str.replaceAll("\ue251", toReplace);
    str = str.replaceAll("\ue252", toReplace);
    str = str.replaceAll("\ue27a", toReplace);
    str = str.replaceAll("\ue27c", toReplace);
    str = str.replaceAll("\ue28a", toReplace);
    str = str.replaceAll("\ue28c", toReplace);
    str = str.replaceAll("\ue28d", toReplace);
    str = str.replaceAll("\ue28e", toReplace);
    str = str.replaceAll("\ue28f", toReplace);
    str = str.replaceAll("\ue290", toReplace);
    str = str.replaceAll("\ue291", toReplace);
    str = str.replaceAll("\ue292", toReplace);
    str = str.replaceAll("\ue293", toReplace);
    str = str.replaceAll("\ue294", toReplace);
    str = str.replaceAll("\ue295", toReplace);
    str = str.replaceAll("\ue296", toReplace);
    str = str.replaceAll("\ue298", toReplace);
    str = str.replaceAll("\ue299", toReplace);
    str = str.replaceAll("\ue29a", toReplace);
    str = str.replaceAll("\ue29c", toReplace);
    str = str.replaceAll("\ue29d", toReplace);
    str = str.replaceAll("\ue29f", toReplace);
    str = str.replaceAll("\ue2a0", toReplace);
    str = str.replaceAll("\ue2a1", toReplace);
    str = str.replaceAll("\ue2a2", toReplace);
    str = str.replaceAll("\ue2a3", toReplace);
    str = str.replaceAll("\ue2a5", toReplace);
    str = str.replaceAll("\ue2a6", toReplace);
    str = str.replaceAll("\ue2a7", toReplace);
    str = str.replaceAll("\ue2a8", toReplace);
    str = str.replaceAll("\ue2a9", toReplace);
    str = str.replaceAll("\ue2aa", toReplace);
    str = str.replaceAll("\ue2ab", toReplace);
    str = str.replaceAll("\ue2ac", toReplace);
    str = str.replaceAll("\ue2ad", toReplace);
    str = str.replaceAll("\ue2ae", toReplace);
    str = str.replaceAll("\ue2af", toReplace);
    str = str.replaceAll("\ue2b0", toReplace);
    str = str.replaceAll("\ue2dd", toReplace);
    str = str.replaceAll("\ue2df", toReplace);
    str = str.replaceAll("\ue2ec", toReplace);
    str = str.replaceAll("\ue2ee", toReplace);
    str = str.replaceAll("\ue2ef", toReplace);
    str = str.replaceAll("\ue2f0", toReplace);
    str = str.replaceAll("\ue2f1", toReplace);
    str = str.replaceAll("\ue2f2", toReplace);
    str = str.replaceAll("\ue2f3", toReplace);
    str = str.replaceAll("\ue2f4", toReplace);
    str = str.replaceAll("\ue2f5", toReplace);
    str = str.replaceAll("\ue2f6", toReplace);
    str = str.replaceAll("\ue2f7", toReplace);
    str = str.replaceAll("\ue2f8", toReplace);
    str = str.replaceAll("\ue2f9", toReplace);
    str = str.replaceAll("\ue2fa", toReplace);
    str = str.replaceAll("\ue2fb", toReplace);
    str = str.replaceAll("\ue2fc", toReplace);
    str = str.replaceAll("\ue2fd", toReplace);
    str = str.replaceAll("\ue2fe", toReplace);
    str = str.replaceAll("\ue2ff", toReplace);
    str = str.replaceAll("\ue300", toReplace);
    str = str.replaceAll("\ue302", toReplace);
    str = str.replaceAll("\ue303", toReplace);
    str = str.replaceAll("\ue304", toReplace);
    str = str.replaceAll("\ue305", toReplace);
    str = str.replaceAll("\ue306", toReplace);
    str = str.replaceAll("\ue307", toReplace);
    str = str.replaceAll("\ue308", toReplace);
    str = str.replaceAll("\ue309", toReplace);
    str = str.replaceAll("\ue30a", toReplace);
    str = str.replaceAll("\ue30b", toReplace);
    str = str.replaceAll("\ue30c", toReplace);
    str = str.replaceAll("\ue30d", toReplace);
    str = str.replaceAll("\ue30e", toReplace);
    str = str.replaceAll("\ue30f", toReplace);
    str = str.replaceAll("\ue310", toReplace);
    str = str.replaceAll("\ue311", toReplace);
    str = str.replaceAll("\ue312", toReplace);
    str = str.replaceAll("\ue313", toReplace);
    str = str.replaceAll("\ue33b", toReplace);
    str = str.replaceAll("\ue33d", toReplace);
    str = str.replaceAll("\ue34a", toReplace);
    str = str.replaceAll("\ue34b", toReplace);
    str = str.replaceAll("\ue34c", toReplace);
    str = str.replaceAll("\ue34e", toReplace);
    str = str.replaceAll("\ue34f", toReplace);
    str = str.replaceAll("\ue350", toReplace);
    str = str.replaceAll("\ue351", toReplace);
    str = str.replaceAll("\ue352", toReplace);
    str = str.replaceAll("\ue354", toReplace);
    str = str.replaceAll("\ue355", toReplace);
    str = str.replaceAll("\ue357", toReplace);
    str = str.replaceAll("\ue358", toReplace);
    str = str.replaceAll("\ue35d", toReplace);
    str = str.replaceAll("\ue360", toReplace);
    str = str.replaceAll("\ue361", toReplace);
    str = str.replaceAll("\ue362", toReplace);
    str = str.replaceAll("\ue363", toReplace);
    str = str.replaceAll("\ue364", toReplace);
    str = str.replaceAll("\ue365", toReplace);
    str = str.replaceAll("\ue366", toReplace);
    str = str.replaceAll("\ue367", toReplace);
    str = str.replaceAll("\ue369", toReplace);
    str = str.replaceAll("\ue36a", toReplace);
    str = str.replaceAll("\ue36c", toReplace);
    str = str.replaceAll("\ue36d", toReplace);
    str = str.replaceAll("\ue36e", toReplace);
    str = str.replaceAll("\ue370", toReplace);
    str = str.replaceAll("\ue371", toReplace);
    str = str.replaceAll("\ue399", toReplace);
    str = str.replaceAll("\ue39b", toReplace);
    str = str.replaceAll("\ue3a8", toReplace);
    str = str.replaceAll("\ue3a9", toReplace);
    str = str.replaceAll("\ue3aa", toReplace);
    str = str.replaceAll("\ue3ab", toReplace);
    str = str.replaceAll("\ue3ac", toReplace);
    str = str.replaceAll("\ue3ad", toReplace);
    str = str.replaceAll("\ue3ae", toReplace);
    str = str.replaceAll("\ue3af", toReplace);
    str = str.replaceAll("\ue3b0", toReplace);
    str = str.replaceAll("\ue3b1", toReplace);
    str = str.replaceAll("\ue3b2", toReplace);
    str = str.replaceAll("\ue3b3", toReplace);
    str = str.replaceAll("\ue3b4", toReplace);
    str = str.replaceAll("\ue3b5", toReplace);
    str = str.replaceAll("\ue3b6", toReplace);
    str = str.replaceAll("\ue3b7", toReplace);
    str = str.replaceAll("\ue3b8", toReplace);
    str = str.replaceAll("\ue3b9", toReplace);
    str = str.replaceAll("\ue3ba", toReplace);
    str = str.replaceAll("\ue3bb", toReplace);
    str = str.replaceAll("\ue3bc", toReplace);
    str = str.replaceAll("\ue3bd", toReplace);
    str = str.replaceAll("\ue3be", toReplace);
    str = str.replaceAll("\ue3bf", toReplace);
    str = str.replaceAll("\ue3c0", toReplace);
    str = str.replaceAll("\ue3c1", toReplace);
    str = str.replaceAll("\ue3c2", toReplace);
    str = str.replaceAll("\ue3c3", toReplace);
    str = str.replaceAll("\ue3c4", toReplace);
    str = str.replaceAll("\ue3c5", toReplace);
    str = str.replaceAll("\ue3c6", toReplace);
    str = str.replaceAll("\ue3c7", toReplace);
    str = str.replaceAll("\ue3c8", toReplace);
    str = str.replaceAll("\ue3c9", toReplace);
    str = str.replaceAll("\ue3ca", toReplace);
    str = str.replaceAll("\ue3cb", toReplace);
    str = str.replaceAll("\ue3cc", toReplace);
    str = str.replaceAll("\ue3cd", toReplace);
    str = str.replaceAll("\ue3ce", toReplace);
    str = str.replaceAll("\ue3cf", toReplace);
    str = str.replaceAll("\ue3f7", toReplace);
    str = str.replaceAll("\ue3f9", toReplace);
    str = str.replaceAll("\ue407", toReplace);
    str = str.replaceAll("\ue409", toReplace);
    str = str.replaceAll("\ue40a", toReplace);
    str = str.replaceAll("\ue40b", toReplace);
    str = str.replaceAll("\ue40c", toReplace);
    str = str.replaceAll("\ue40d", toReplace);
    str = str.replaceAll("\ue40e", toReplace);
    str = str.replaceAll("\ue40f", toReplace);
    str = str.replaceAll("\ue410", toReplace);
    str = str.replaceAll("\ue411", toReplace);
    str = str.replaceAll("\ue412", toReplace);
    str = str.replaceAll("\ue413", toReplace);
    str = str.replaceAll("\ue414", toReplace);
    str = str.replaceAll("\ue415", toReplace);
    str = str.replaceAll("\ue417", toReplace);
    str = str.replaceAll("\ue419", toReplace);
    str = str.replaceAll("\ue41a", toReplace);
    str = str.replaceAll("\ue41c", toReplace);
    str = str.replaceAll("\ue41d", toReplace);
    str = str.replaceAll("\ue41e", toReplace);
    str = str.replaceAll("\ue420", toReplace);
    str = str.replaceAll("\ue421", toReplace);
    str = str.replaceAll("\ue423", toReplace);
    str = str.replaceAll("\ue424", toReplace);
    str = str.replaceAll("\ue425", toReplace);
    str = str.replaceAll("\ue427", toReplace);
    str = str.replaceAll("\ue428", toReplace);
    str = str.replaceAll("\ue429", toReplace);
    str = str.replaceAll("\ue42a", toReplace);
    str = str.replaceAll("\ue42b", toReplace);
    str = str.replaceAll("\ue42c", toReplace);
    str = str.replaceAll("\ue42d", toReplace);
    str = str.replaceAll("\ue455", toReplace);
    str = str.replaceAll("\ue457", toReplace);
    str = str.replaceAll("\ue464", toReplace);
    str = str.replaceAll("\ue466", toReplace);
    str = str.replaceAll("\ue467", toReplace);
    str = str.replaceAll("\ue468", toReplace);
    str = str.replaceAll("\ue469", toReplace);
    str = str.replaceAll("\ue46a", toReplace);
    str = str.replaceAll("\ue46b", toReplace);
    str = str.replaceAll("\ue46c", toReplace);
    str = str.replaceAll("\ue46d", toReplace);
    str = str.replaceAll("\ue470", toReplace);
    str = str.replaceAll("\ue471", toReplace);
    str = str.replaceAll("\ue472", toReplace);
    str = str.replaceAll("\ue474", toReplace);
    str = str.replaceAll("\ue475", toReplace);
    str = str.replaceAll("\ue476", toReplace);
    str = str.replaceAll("\ue477", toReplace);
    str = str.replaceAll("\ue478", toReplace);
    str = str.replaceAll("\ue47c", toReplace);
    str = str.replaceAll("\ue47e", toReplace);
    str = str.replaceAll("\ue480", toReplace);
    str = str.replaceAll("\ue482", toReplace);
    str = str.replaceAll("\ue485", toReplace);
    str = str.replaceAll("\ue486", toReplace);
    str = str.replaceAll("\ue487", toReplace);
    str = str.replaceAll("\ue488", toReplace);
    str = str.replaceAll("\ue489", toReplace);
    str = str.replaceAll("\ue48a", toReplace);
    str = str.replaceAll("\ue48b", toReplace);
    str = str.replaceAll("\ue4b3", toReplace);
    str = str.replaceAll("\ue4b5", toReplace);
    str = str.replaceAll("\ue4c2", toReplace);
    str = str.replaceAll("\ue4c5", toReplace);
    str = str.replaceAll("\ue4c6", toReplace);
    str = str.replaceAll("\ue4c7", toReplace);
    str = str.replaceAll("\ue4c8", toReplace);
    str = str.replaceAll("\ue4c9", toReplace);
    str = str.replaceAll("\ue4ca", toReplace);
    str = str.replaceAll("\ue4cb", toReplace);
    str = str.replaceAll("\ue4cc", toReplace);
    str = str.replaceAll("\ue4ce", toReplace);
    str = str.replaceAll("\ue4cf", toReplace);
    str = str.replaceAll("\ue4d0", toReplace);
    str = str.replaceAll("\ue4d1", toReplace);
    str = str.replaceAll("\ue4d2", toReplace);
    str = str.replaceAll("\ue4d3", toReplace);
    str = str.replaceAll("\ue4d4", toReplace);
    str = str.replaceAll("\ue4d6", toReplace);
    str = str.replaceAll("\ue4d7", toReplace);
    str = str.replaceAll("\ue4d8", toReplace);
    str = str.replaceAll("\ue4d9", toReplace);
    str = str.replaceAll("\ue4da", toReplace);
    str = str.replaceAll("\ue4db", toReplace);
    str = str.replaceAll("\ue4dc", toReplace);
    str = str.replaceAll("\ue4dd", toReplace);
    str = str.replaceAll("\ue4de", toReplace);
    str = str.replaceAll("\ue4df", toReplace);
    str = str.replaceAll("\ue4e0", toReplace);
    str = str.replaceAll("\ue4e1", toReplace);
    str = str.replaceAll("\ue4e2", toReplace);
    str = str.replaceAll("\ue4e4", toReplace);
    str = str.replaceAll("\ue4e5", toReplace);
    str = str.replaceAll("\ue4e6", toReplace);
    str = str.replaceAll("\ue4e7", toReplace);
    str = str.replaceAll("\ue4e8", toReplace);
    str = str.replaceAll("\ue4e9", toReplace);
    str = str.replaceAll("\ue511", toReplace);
    str = str.replaceAll("\ue513", toReplace);
    str = str.replaceAll("\ue520", toReplace);
    str = str.replaceAll("\ue521", toReplace);
    str = str.replaceAll("\ue522", toReplace);
    str = str.replaceAll("\ue523", toReplace);
    str = str.replaceAll("\ue524", toReplace);
    str = str.replaceAll("\ue525", toReplace);
    str = str.replaceAll("\ue526", toReplace);
    str = str.replaceAll("\ue528", toReplace);
    str = str.replaceAll("\ue529", toReplace);
    str = str.replaceAll("\ue52a", toReplace);
    str = str.replaceAll("\ue52c", toReplace);
    str = str.replaceAll("\ue52d", toReplace);
    str = str.replaceAll("\ue52e", toReplace);
    str = str.replaceAll("\ue52f", toReplace);
    str = str.replaceAll("\ue534", toReplace);
    str = str.replaceAll("\ue537", toReplace);
    str = str.replaceAll("\ue538", toReplace);
    str = str.replaceAll("\ue53a", toReplace);
    str = str.replaceAll("\ue53c", toReplace);
    str = str.replaceAll("\ue53d", toReplace);
    str = str.replaceAll("\ue540", toReplace);
    str = str.replaceAll("\ue541", toReplace);
    str = str.replaceAll("\ue542", toReplace);
    str = str.replaceAll("\ue543", toReplace);
    str = str.replaceAll("\ue544", toReplace);
    str = str.replaceAll("\ue546", toReplace);
    str = str.replaceAll("\ue547", toReplace);
    return str;
  }
  static String fixCharsInParse(String str) {
    return fixChars(str, "．");
  }

  static String fixCharsInSent(String str) {
    return fixChars(str, ".");
  }
}
