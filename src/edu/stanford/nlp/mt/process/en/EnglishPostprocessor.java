package edu.stanford.nlp.mt.process.en;

import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.mt.process.CRFPostprocessor;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

public class EnglishPostprocessor extends CRFPostprocessor {

  private static final long serialVersionUID = -3355581863585846099L;

  public EnglishPostprocessor(Properties props) {
    super(props);
  }

  public EnglishPostprocessor(String...args) {
    super(new Properties());
    if (args.length == 0) throw new IllegalArgumentException("Requires at least one argument");
    String serializedFile = args[0];
    load(serializedFile);
    System.err.println("Loaded EnglishPostprocessor from: " + args[0]);
  }
  
  private static String usage() {
    String nl = System.getProperty("line.separator");
    StringBuilder sb = new StringBuilder();
    sb.append("Usage: java ").append(EnglishPostprocessor.class.getName()).append(" OPTS < file").append(nl);
    sb.append(nl).append(" Options:").append(nl);
    sb.append("  -help                : Print this message.").append(nl);
    sb.append("  -orthoOptions str    : Comma-separated list of orthographic normalization options to pass to PTBTokenizer.").append(nl);
    sb.append("  -trainFile file      : Training file.").append(nl);
    sb.append("  -testFile  file      : Evaluation file.").append(nl);
    sb.append("  -textFile  file      : Raw input file to be postProcessed.").append(nl);
    sb.append("  -loadClassifier file : Load serialized classifier from file.").append(nl);
    sb.append("  -nthreads num        : Number of threads  (default: 1)").append(nl);
    sb.append(nl).append(" Otherwise, all flags correspond to those present in SeqClassifierFlags.java.").append(nl);
    return sb.toString();
  }

  private static Map<String,Integer> optionArgDefs() {
    Map<String,Integer> optionArgDefs = Generics.newHashMap();
    optionArgDefs.put("help", 0);
    optionArgDefs.put("orthoOptions", 1);
    optionArgDefs.put("trainFile", 1);
    optionArgDefs.put("testFile", 1);
    optionArgDefs.put("textFile", 1);
    optionArgDefs.put("loadClassifier", 1);
    optionArgDefs.put("nthreads", 1);
    return optionArgDefs;
  }

  /**
   * A main method for training and evaluating the postprocessor.
   * 
   * @param args
   */
  public static void main(String[] args) {
    // Strips off hyphens
    Properties options = StringUtils.argsToProperties(args, optionArgDefs());
    if (options.containsKey("help") || args.length == 0) {
      System.err.println(usage());
      System.exit(-1);
    }

    int nThreads = PropertiesUtils.getInt(options, "nthreads", 1);
    EnglishPreprocessor preProcessor = new EnglishPreprocessor();
    EnglishPostprocessor postProcessor = new EnglishPostprocessor(options);
    
    CRFPostprocessor.setup(postProcessor, preProcessor, options);
    CRFPostprocessor.execute(nThreads, preProcessor, postProcessor);    
  }
}
