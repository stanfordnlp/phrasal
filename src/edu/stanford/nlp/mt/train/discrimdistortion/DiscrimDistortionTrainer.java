package edu.stanford.nlp.mt.train.discrimdistortion;

import java.util.Date;
import java.util.List;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

public final class DiscrimDistortionTrainer {

  private DiscrimDistortionTrainer() {}

  private static String usage()
  {
    String cmdLineUsage = "Usage: java DiscrimDistortionTrainer [features] source target align\n";
    StringBuilder classUsage = new StringBuilder(cmdLineUsage);

    classUsage.append(" -v          : Verbose output\n");
    classUsage.append(" -f  <num>   : Expected number of features (for mem pre-allocation)\n");
    classUsage.append(" -t  <num>   : Number of threads to use in feature extraction\n");
    classUsage.append(" -l          : Feature: source sentence length\n");
    classUsage.append(" -p          : Feature: relative sentence position\n");
    classUsage.append(" -s          : Feature: source POS tag (source file must be tagged with delimiter #)\n");
    classUsage.append(" -w <thresh> : Feature: word (cutoff threshold for vocabulary)\n");
    classUsage.append(" -e <file>   : Extract and write feature set ONLY\n");
//    classUsage.append(" -c          : Feature: right/left POS tag context\n");
    classUsage.append(" -a          : Feature: Arc POS tag in sequence\n");
    classUsage.append(" -r thresh   : Restrict training to abs(thresh) distortion movement\n");
    classUsage.append(" -x <rate>   : Sub-sample monotone and null classes\n");
    classUsage.append(" -n          : Use null alignments\n");
    classUsage.append(" -m <name>   : Name of the serialized output model\n");
    classUsage.append(" -o          : Train an outbound model\n");
    classUsage.append(" -d          : Use sentence begin/end delimiters\n");

    return classUsage.toString();
  }

  //Uses GNU getopt() syntax
  private final static OptionParser op = new OptionParser("aodnx:r:ve:w:plst:f:m:");
  private final static int MIN_ARGS = 3;

  //Command line options
  private static boolean VERBOSE = false;
  private static boolean USE_WORD = false;
  private static boolean USE_TAG = false;
  private static boolean USE_POSITION = false;
  private static boolean USE_SLEN = false;
  private static boolean USE_CONTEXT = false;
  private static boolean EXTRACT_ONLY = false;
  private static boolean THRESHOLD_TRAINING = false;
  private static boolean SUB_SAMPLE = false;
  private static boolean OUTBOUND = false; //default is inbound
  private static boolean USE_ARC_TAG = false;
  private static boolean INSERT_DELIM = false;

  private static int numThreads = 1;
  private static int numExpectedFeatures = 0;
  private static int minWordCount = 40;
  private static String extractFile = "";
  private static float trainingThreshold = 0.0f;
  private static String modelName = "ddmodel.ser.gz"; //Default
  private static float subSampleRate = 0.0f;
  
  //Arguments
  private static String sourceFile = "";
  private static String targetFile = "";
  private static String alignFile = "";

  private static boolean validateCommandLine(String[] args) {
    //Command line parsing
    OptionSet opts = null;
    List<String> parsedArgs = null;
    try {
      opts = op.parse(args);

      parsedArgs = opts.nonOptionArguments();

      if(parsedArgs == null || parsedArgs.size() < MIN_ARGS)
        return false;

    } catch (OptionException e) {
      System.err.println(e.toString());
      return false;
    }

    VERBOSE = opts.has("v");
    USE_SLEN = opts.has("l");
    USE_POSITION = opts.has("p");
    USE_TAG = opts.has("s");
    USE_CONTEXT = opts.has("c");
    USE_ARC_TAG = opts.has("a");
    OUTBOUND = opts.has("o"); //inbound is the default
    INSERT_DELIM = opts.has("d");
    
    if(opts.has("x")) {
      SUB_SAMPLE = true;
      subSampleRate = Float.parseFloat((String) opts.valueOf("x"));
    }
    if(opts.has("t"))
      numThreads = Integer.parseInt((String) opts.valueOf("t"));
    if(opts.has("f"))
      numExpectedFeatures = Integer.parseInt((String) opts.valueOf("f"));
    if(opts.has("w")) {
      USE_WORD = true;
      minWordCount = Integer.parseInt((String) opts.valueOf("w"));
    }
    if(opts.has("e")) {
      EXTRACT_ONLY = true;
      extractFile = (String) opts.valueOf("e");
    }
    if(opts.has("r")) {
      THRESHOLD_TRAINING = true;
      trainingThreshold = Float.parseFloat((String) opts.valueOf("r"));
    }
    if(opts.has("m"))
      modelName = (String) opts.valueOf("m");
    
    sourceFile = parsedArgs.get(0);
    targetFile = parsedArgs.get(1);
    alignFile = parsedArgs.get(2);

    return true;
  }


  public static void main(String[] args) {
    if(!validateCommandLine(args)) {
      System.err.println(usage());
      System.exit(-1);
    }

    Date startTime = new Date();
    System.out.println("###############################################");
    System.out.println("### Discriminative Distortion Model Trainer ###");
    System.out.println("###############################################");
    System.out.printf("Start time: %s\n", startTime);

    DiscrimDistortionController controller = new DiscrimDistortionController(sourceFile,targetFile,alignFile,modelName);
    controller.setVerbose(VERBOSE);
    controller.setFeatureFlags(USE_WORD,USE_TAG,USE_POSITION,USE_SLEN, USE_CONTEXT, USE_ARC_TAG);
    controller.setNumThreads(numThreads);
    controller.setMinWordCount(minWordCount);
    controller.preAllocateMemory(numExpectedFeatures);
    controller.subSampleFeatureExtraction(SUB_SAMPLE, subSampleRate);
    controller.insertDelimiters(INSERT_DELIM);

    if(THRESHOLD_TRAINING)
      controller.setTrainingThreshold(trainingThreshold);
    
    if(OUTBOUND)
      controller.trainOutboundModel();

    if(EXTRACT_ONLY) {
      controller.extractOnly(extractFile);
      System.out.println("done!");
    } else if(controller.run()) {
      System.out.println("Writing final weights and indices...");
      controller.outputModel();
      System.out.println("done!");
    } else
      System.out.println("ERROR: Terminating execution...");

    Date stopTime = new Date();
    long elapsedTime = stopTime.getTime() - startTime.getTime();
    System.out.println();
    System.out.println();
    System.out.printf("Completed processing at %s\n",stopTime);
    System.out.printf("Elapsed time: %d seconds\n", (int) (elapsedTime / 1000F));
  }

}
