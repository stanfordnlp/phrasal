package edu.stanford.nlp.mt.process.es;

import java.io.FileNotFoundException;
import java.util.Properties;

import edu.stanford.nlp.mt.process.CRFPostprocessor;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * CRF-based post-processor for Spanish.
 * 
 * @author Spence Green
 *
 */
public class SpanishPostprocessor extends CRFPostprocessor {

  private static final long serialVersionUID = -4340278525864733396L;

  /**
   * Constructor loading a new model before training.
   * 
   * @param options
   */
  public SpanishPostprocessor(Properties options) {
    super(options);
  }

  /**
   * Constructor for loading pre-trained models.
   * 
   * @param args
   */
  public SpanishPostprocessor(String...args) {
    super(new Properties());
    if (args.length == 0) throw new IllegalArgumentException("Requires at least one argument");
    String serializedFile = args[0];
    try {
      load(serializedFile);
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
    System.err.println("Loaded SpanishPostprocessor from: " + args[0]);
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
      System.err.println(usage(SpanishPostprocessor.class.getName()));
      System.exit(-1);
    }

    int nThreads = PropertiesUtils.getInt(options, "nthreads", 1);
    SpanishPreprocessor preProcessor = new SpanishPreprocessor();
    SpanishPostprocessor postProcessor = new SpanishPostprocessor(options);
    
    CRFPostprocessor.setup(postProcessor, preProcessor, options);
    CRFPostprocessor.execute(nThreads, preProcessor, postProcessor);    
  }
}
