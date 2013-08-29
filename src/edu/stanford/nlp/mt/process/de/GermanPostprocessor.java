package edu.stanford.nlp.mt.process.de;

import java.io.FileNotFoundException;
import java.util.Properties;

import edu.stanford.nlp.mt.process.CRFPostprocessor;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * CRF-based post-processor for German.
 * 
 * @author Spence Green
 *
 */
public class GermanPostprocessor extends CRFPostprocessor {

  private static final long serialVersionUID = -2611602459669509968L;

  /**
   * Constructor loading a new model before training.
   * 
   * @param options
   */
  public GermanPostprocessor(Properties options) {
    super(options);
  }

  /**
   * Constructor for loading pre-trained models.
   * 
   * @param args
   */
  public GermanPostprocessor(String...args) {
    super(new Properties());
    if (args.length == 0) throw new IllegalArgumentException("Requires at least one argument");
    String serializedFile = args[0];
    try {
      load(serializedFile);
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
    System.err.println("Loaded GermanPostprocessor from: " + args[0]);
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
      System.err.println(usage(GermanPostprocessor.class.getName()));
      System.exit(-1);
    }

    int nThreads = PropertiesUtils.getInt(options, "nthreads", 1);
    GermanPreprocessor preProcessor = new GermanPreprocessor();
    GermanPostprocessor postProcessor = new GermanPostprocessor(options);
    
    CRFPostprocessor.setup(postProcessor, preProcessor, options);
    CRFPostprocessor.execute(nThreads, preProcessor, postProcessor);    
  }
}
