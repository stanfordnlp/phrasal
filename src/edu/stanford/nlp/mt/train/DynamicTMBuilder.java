package edu.stanford.nlp.mt.train;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.stanford.nlp.mt.tm.DynamicTranslationModel;
import edu.stanford.nlp.mt.train.AlignmentSymmetrizer.SymmetrizationType;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.ParallelCorpus;
import edu.stanford.nlp.mt.util.ParallelSuffixArray;
import edu.stanford.nlp.mt.util.TimingUtils;
import edu.stanford.nlp.mt.util.TimingUtils.TimeKeeper;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Builds a dynamic translation model based on suffix arrays.
 * 
 * @author Spence Green
 *
 */
public class DynamicTMBuilder {

  private static final Logger logger = LogManager.getLogger(DynamicTMBuilder.class);

  private ParallelSuffixArray sa;
  
  /**
   * Constructor.
   * 
   * @param sourceFile
   * @param targetFile
   * @param alignFile
   * @param expectedSize
   */
  public DynamicTMBuilder(String sourceFile, String targetFile, String alignFile, int expectedSize) {
    sa = new ParallelSuffixArray(sourceFile, targetFile, alignFile, expectedSize);
  }
  
  /**
   * Constructor.
   * 
   * @param sourceFile
   * @param targetFile
   * @param feAlign
   * @param efAlign
   * @param expectedSize
   * @param type
   */
  public DynamicTMBuilder(String sourceFile, String targetFile, String feAlign, String efAlign, 
      int expectedSize, SymmetrizationType type) {
    ParallelCorpus corpus = loadAndSymmetrize(sourceFile, targetFile, feAlign, efAlign, type, expectedSize);
    sa = new ParallelSuffixArray();
    sa.loadCorpus(corpus);
    // A hacky way to free memory
    corpus = null;
    sa.build();
  }
  
  /**
   * Wrap the underlying data structure in a Phrasal TranslationModel.
   * 
   * @return
   */
  public DynamicTranslationModel<String> build() {
    return new DynamicTranslationModel<>(sa);
  }
  
  /**
   * Symmetrize the alignments and create a corpus.
   * 
   * @param sourceFile
   * @param targetFile
   * @param feAlign
   * @param efAlign
   * @param initialCapacity
   * @return
   */
  private static ParallelCorpus loadAndSymmetrize(String sourceFile,
      String targetFile, String feAlign, String efAlign, SymmetrizationType type, 
      int initialCapacity) {
    ParallelCorpus corpus = new ParallelCorpus(initialCapacity);
    
    LineNumberReader fReader = IOTools.getReaderFromFile(sourceFile);
    LineNumberReader eReader = IOTools.getReaderFromFile(targetFile);
    LineNumberReader feReader = IOTools.getReaderFromFile(feAlign);
    LineNumberReader efReader = IOTools.getReaderFromFile(efAlign);
    
    try {
      for (String fLine; (fLine = fReader.readLine()) != null; ) {
        if (fReader.getLineNumber() % 10000 == 0) 
          logger.info("Reading corpus line {}...", fReader.getLineNumber());
        String eLine = eReader.readLine();
        String ef1 = efReader.readLine();
        String ef2 = efReader.readLine();
        String ef3 = efReader.readLine();
        String fe1 = feReader.readLine();
        String fe2 = feReader.readLine();
        String fe3 = feReader.readLine();

        GIZAWordAlignment gizaAlign = new GIZAWordAlignment(fe1, fe2,
            fe3, ef1, ef2, ef3);
        if (gizaAlign.e().size() > ParallelCorpus.MAX_SENTENCE_LENGTH ||
            gizaAlign.f().size() > ParallelCorpus.MAX_SENTENCE_LENGTH) {
          continue;
        }
        SymmetricalWordAlignment symAlign = AlignmentSymmetrizer
            .symmetrize(gizaAlign, type);
        symAlign.reverse();
        String aLine = symAlign.toString().trim();
        // Sometimes there are no consistent alignments.
        if ( ! aLine.isEmpty()) corpus.add(fLine, eLine, aLine);
      }

      fReader.close();
      eReader.close();
      feReader.close();
      efReader.close();
      
    } catch (IOException e) {
      logger.error("Error at line {}", fReader.getLineNumber());
      logger.error("Exception: ", e);
    }
    
    return corpus;
  }

  private static Map<String, Integer> optionDefs() {
    Map<String,Integer> optionDefs = new HashMap<>();
    optionDefs.put("o", 1);
    optionDefs.put("e", 1);
    optionDefs.put("s", 1);
    return optionDefs;
  }  

  private static String usage() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    sb.append("Usage: java ").append(DynamicTMBuilder.class.getName()).append(" OPTS src target alignf2e [aligne2f]").append(nl);
    sb.append(nl).append(" Options:").append(nl)
    .append("   -o file-name   : Output file name.").append(nl)
    .append("   -e num         : Estimated size of corpus (num. lines)").append(nl)
    .append("   -s type        : Symmetrization type.").append(nl);
    return sb.toString();
  }
  
  /**
   * 
   * @param args
   */
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
    
    String outputFileName = options.getProperty("o", "tm.bin");
    SymmetrizationType type = options.containsKey("s") ? SymmetrizationType.valueOf(options.getProperty("s"))
        : SymmetrizationType.valueOf("grow_diag_final_and");
    int initialCapacity = PropertiesUtils.getInt(options, "e", 10000);
    
    String sourceFile = positionalArgs[0];
    String targetFile = positionalArgs[1];
    String alignFEfile = positionalArgs[2];
    String alignEFfile = positionalArgs.length == 4 ? positionalArgs[3] : null;
    
    logger.info("Source file: {}", sourceFile);
    logger.info("Target file: {}", targetFile);
    logger.info("Alignment file (f2e): {}", alignFEfile);
    if (alignEFfile != null) logger.info("Alignment file (e2f): {}", alignEFfile);
    
    TimeKeeper timer = TimingUtils.start();
    DynamicTMBuilder tmBuilder = alignEFfile == null ? new DynamicTMBuilder(sourceFile, targetFile, alignFEfile, initialCapacity) :
      new DynamicTMBuilder(sourceFile, targetFile, alignFEfile, alignEFfile, initialCapacity, type);
    timer.mark("Model construction");
    
    DynamicTranslationModel<String> tm = tmBuilder.build();
        
    try {
      logger.info("Serializing to: " + outputFileName);
      IOTools.serialize(outputFileName, tm);
      timer.mark("Serialization");
      logger.info("Timing summary: {}", timer);
      logger.info("Success! Shutting down...");
    } catch (IOException e) {
      logger.error("Unable to serialize to: " + outputFileName, e);
    }
  }
}
