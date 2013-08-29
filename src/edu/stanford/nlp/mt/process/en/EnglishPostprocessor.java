package edu.stanford.nlp.mt.process.en;

import java.io.FileNotFoundException;
import java.util.Properties;

import edu.stanford.nlp.mt.process.CRFPostprocessor;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Inverts the pre-processing performed by EnglishPreprocessor.
 * 
 * @author Spence Green
 *
 */
public class EnglishPostprocessor extends CRFPostprocessor {

  private static final long serialVersionUID = -3355581863585846099L;

  /**
   * Constructor for untrained models.
   * 
   * @param props
   */
  public EnglishPostprocessor(Properties props) {
    super(props);
  }

  /**
   * Constructor for loading pre-trained models.
   * 
   * @param args
   */
  public EnglishPostprocessor(String...args) {
    super(new Properties());
    if (args.length == 0) throw new IllegalArgumentException("Requires at least one argument");
    String serializedFile = args[0];
    try {
      load(serializedFile);
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
    System.err.println("Loaded EnglishPostprocessor from: " + args[0]);
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
      System.err.println(usage(EnglishPostprocessor.class.getName()));
      System.exit(-1);
    }

    int nThreads = PropertiesUtils.getInt(options, "nthreads", 1);
    EnglishPreprocessor preProcessor = new EnglishPreprocessor();
    EnglishPostprocessor postProcessor = new EnglishPostprocessor(options);
    
    CRFPostprocessor.setup(postProcessor, preProcessor, options);
    CRFPostprocessor.execute(nThreads, preProcessor, postProcessor);    
  }
}
