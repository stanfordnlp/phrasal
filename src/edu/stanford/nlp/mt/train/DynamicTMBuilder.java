package edu.stanford.nlp.mt.train;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.stanford.nlp.mt.util.ParallelCorpus;
import edu.stanford.nlp.mt.util.ParallelSuffixArray;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * 
 * 
 * @author Spence Green
 *
 */
public class DynamicTMBuilder {

  private static final Logger logger = LogManager.getLogger(DynamicTMBuilder.class);

  private ParallelSuffixArray sa;
  
  public DynamicTMBuilder(ParallelCorpus corpus) {
    sa = new ParallelSuffixArray(corpus);
  }
  
  public DynamicTMBuilder(String sourceFile, String targetFile, String align, int expectedSize) {
    sa = new ParallelSuffixArray(sourceFile, targetFile, align, expectedSize);
  }
  
  public DynamicTMBuilder(String sourceFile, String targetFile, String feAlign, String efAlign, int expectedSize) {
    sa = symmetrizeAndCreateSuffixArray(sourceFile, targetFile, feAlign, efAlign, expectedSize);
  }
  
  public DynamicTranslationModel getModel() {
    
    return null;
  }
  
  
  private ParallelSuffixArray symmetrizeAndCreateSuffixArray(String sourceFile,
      String targetFile, String feAlign, String efAlign, int initialCapacity) {
    ParallelCorpus corpus = new ParallelCorpus();
    
    // TODO(spenceg) Symmetrization
    
    return new ParallelSuffixArray(corpus);
  }

  private static Map<String, Integer> optionDefs() {
    Map<String,Integer> optionDefs = new HashMap<>();
    optionDefs.put("o", 1);
    optionDefs.put("e", 1);
    return optionDefs;
  }  

  private static String usage() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    sb.append("Usage: java ").append(DynamicTMBuilder.class.getName()).append(" OPTS src target alignf2e [aligne2f]").append(nl);
    sb.append(nl).append(" Options:").append(nl)
    .append("   -o file-name   : Output file name.").append(nl)
    .append("   -e num         : Estimated size of corpus (num. lines)").append(nl);
    return sb.toString();
  }
  
  public static void main(String[] args) {
    if (args.length < 1 || args[0].equals("-h") || args[0].equals("-help")) {
      System.err.print(usage());
      System.exit(-1);
    }
    Properties options = StringUtils.argsToProperties(args, optionDefs());
    String[] positionalArgs = options.getProperty("").split("\\s+");
    if (positionalArgs.length < 3) {
      System.err.print(usage());
      System.exit(-1);      
    }
    
    String outputFileName = options.getProperty("o", "tm.kryo");
    int initialCapacity = PropertiesUtils.getInt(options, "e", 10000);
    
    String sourceFile = positionalArgs[0];
    String targetFile = positionalArgs[1];
    String alignFEfile = positionalArgs[2];
    String alignEFfile = positionalArgs.length == 4 ? positionalArgs[3] : null;
    
    logger.info("Source file: {}", sourceFile);
    logger.info("Target file: {}", targetFile);
    logger.info("Alignment file (f2e): {}", alignFEfile);
    if (alignEFfile != null) logger.info("Alignment file (e2f): {}", alignEFfile);
    DynamicTMBuilder tmBuilder = alignEFfile == null ? new DynamicTMBuilder(sourceFile, targetFile, alignFEfile, initialCapacity) :
      new DynamicTMBuilder(sourceFile, targetFile, alignFEfile, alignEFfile, initialCapacity);
    DynamicTranslationModel tm = tmBuilder.getModel();
    // TODO(spenceg) Serialize with kryo
  }


}
