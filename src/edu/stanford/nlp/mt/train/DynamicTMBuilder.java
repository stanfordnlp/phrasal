package edu.stanford.nlp.mt.train;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.stanford.nlp.mt.tm.DynamicTranslationModel;
import edu.stanford.nlp.mt.train.AlignmentSymmetrizer.SymmetrizationType;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.ParallelCorpus;
import edu.stanford.nlp.mt.util.ParallelSuffixArray;
import edu.stanford.nlp.mt.util.TimingUtils;
import edu.stanford.nlp.mt.util.TimingUtils.TimeKeeper;
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
   * Constructor. Build a dynamic translation model from a ParallelCorpus.
   * 
   * @param corpus
   */
  public DynamicTMBuilder(ParallelCorpus corpus) {
    sa = new ParallelSuffixArray(corpus);
  }
  
  /**
   * Constructor.
   * 
   * @param sourceFile
   * @param targetFile
   * @param feAlign
   * @param efAlign
   * @param type
   * @throws IOException 
   */
  public DynamicTMBuilder(String sourceFile, String targetFile, String feAlign, String efAlign, 
      SymmetrizationType type) throws IOException {
    this(sourceFile, targetFile, 
        symmetrize(sourceFile, targetFile, feAlign, efAlign, type));
  }

  /**
   * Constructor.
   * 
   * @param sourceFile
   * @param targetFile
   * @param alignFile
   * @param expectedSize
   * @throws IOException 
   */
  public DynamicTMBuilder(String sourceFile, String targetFile, String alignFile) throws IOException {
    sa = new ParallelSuffixArray(sourceFile, targetFile, alignFile);
  }
    
  /**
   * Wrap the underlying data structure in a Phrasal TranslationModel.
   * 
   * @return
   */
  public DynamicTranslationModel<String> build() {
    sa.build();
    return new DynamicTranslationModel<>(sa);
  }
  
  /**
   * Symmetrize the alignments and create a corpus.
   * 
   * @param sourceFile
   * @param targetFile
   * @param feAlign
   * @param efAlign
   * @return
   * @throws IOException 
   */
  private static String symmetrize(String sourceFile,
      String targetFile, String feAlign, String efAlign, SymmetrizationType type) throws IOException {    
    try (LineNumberReader fReader = IOTools.getReaderFromFile(sourceFile)) {
      LineNumberReader eReader = IOTools.getReaderFromFile(targetFile);
      LineNumberReader feReader = IOTools.getReaderFromFile(feAlign);
      LineNumberReader efReader = IOTools.getReaderFromFile(efAlign);
      String outFileName = getSymmetrizationFilename(Paths.get(sourceFile).getParent());
      PrintStream alignFile = IOTools.getWriterFromFile(outFileName);
      
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
        int sourceLength = fLine.trim().split("\\s+").length;
        int targetLength = eLine.trim().split("\\s+").length;
        // Recall that source and target are swapped in this data structure.
        if (gizaAlign.e().size() != sourceLength) {
          logger.error("Source length mismatch at line {}", fReader.getLineNumber());
          throw new RuntimeException();
        }
        if (gizaAlign.f().size() != targetLength) {
          logger.error("Target length mismatch at line {}", eReader.getLineNumber());
          throw new RuntimeException();
        }
        SymmetricalWordAlignment symAlign = AlignmentSymmetrizer
            .symmetrize(gizaAlign, type);
        symAlign.reverse();
        String aLine = symAlign.toString().trim();
        alignFile.println(aLine);
      }
      
      // Ensure that input files are exhausted.
      if (eReader.readLine() != null) {
        logger.error("Target file is not exhausted!");
        throw new RuntimeException();
      }
      if(feReader.readLine() != null) {
        logger.error("fe alignment file is not exhausted!");
        throw new RuntimeException();
      }
      if (efReader.readLine() != null) {
        logger.error("ef alignment file is not exhausted!");
        throw new RuntimeException();
      }
      
      alignFile.close();
      eReader.close();
      feReader.close();
      efReader.close();
      logger.info("Symmetrized {} lines.", fReader.getLineNumber());
      return outFileName;
    }
  }
  
  /**
   * Create a temporary file for the symmetrized alignments.
   * 
   * @param basepath
   * @return
   */
  private static String getSymmetrizationFilename(Path basepath) {
    String path = basepath == null ? "" : basepath.toString();
    Random random = new Random();
    String fileName = String.format("align.sym.%d%s", random.nextInt(10000), IOTools.GZ_EXTENSION);
    return Paths.get(path, fileName).toString();
  }

  private static Map<String, Integer> optionDefs() {
    Map<String,Integer> optionDefs = new HashMap<>();
    optionDefs.put("o", 1);
    optionDefs.put("s", 1);
    return optionDefs;
  }  

  private static String usage() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    sb.append("Usage: java ").append(DynamicTMBuilder.class.getName()).append(" OPTS src target alignf2e [aligne2f]").append(nl);
    sb.append(nl).append(" Options:").append(nl)
    .append("   -o file-name   : Output file name.").append(nl)
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
    
    String outputFileName = options.getProperty("o", "tm" + IOTools.BIN_EXTENSION);
    SymmetrizationType type = options.containsKey("s") ? SymmetrizationType.valueOf(options.getProperty("s"))
        : SymmetrizationType.valueOf("grow_diag_final_and");
    
    String sourceFile = positionalArgs[0];
    String targetFile = positionalArgs[1];
    String alignFEfile = positionalArgs[2];
    String alignEFfile = positionalArgs.length == 4 ? positionalArgs[3] : null;
    
    logger.info("Source file: {}", sourceFile);
    logger.info("Target file: {}", targetFile);
    logger.info("Alignment file (f2e): {}", alignFEfile);
    if (alignEFfile != null) logger.info("Alignment file (e2f): {}", alignEFfile);
    
    try {
      TimeKeeper timer = TimingUtils.start();
      DynamicTMBuilder tmBuilder = alignEFfile == null ? new DynamicTMBuilder(sourceFile, targetFile, alignFEfile) :
        new DynamicTMBuilder(sourceFile, targetFile, alignFEfile, alignEFfile, type);
      timer.mark("Model construction");
      
      DynamicTranslationModel<String> tm = tmBuilder.build();
          
      logger.info("Serializing to: " + outputFileName);
      IOTools.serialize(outputFileName, tm);
      timer.mark("Serialization");
      logger.info("Timing summary: {}", timer);
      logger.info("Success! Shutting down...");
    } catch (Exception e) {
      logger.fatal("Translation model build error!", e);
    }
  }
}
