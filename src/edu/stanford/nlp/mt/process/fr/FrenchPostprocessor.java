package edu.stanford.nlp.mt.process.fr;

import java.util.Properties;

import edu.stanford.nlp.mt.process.CRFPostprocessor;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * CRF-based post-processor for French.
 * 
 * @author Spence Green
 *
 */
public class FrenchPostprocessor extends CRFPostprocessor {

  private static final long serialVersionUID = -4340278525864733396L;

  /**
   * Constructor loading a new model before training.
   * 
   * @param options
   */
  public FrenchPostprocessor(Properties options) {
    super(options);
  }

  /**
   * Constructor for loading pre-trained models.
   * 
   * @param args
   */
  public FrenchPostprocessor(String...args) {
    super(new Properties());
    if (args.length == 0) throw new IllegalArgumentException("Requires at least one argument");
    String serializedFile = args[0];
    load(serializedFile);
    System.err.println("Loaded FrenchPostprocessor from: " + args[0]);
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
      System.err.println(usage(FrenchPostprocessor.class.getName()));
      System.exit(-1);
    }

    int nThreads = PropertiesUtils.getInt(options, "nthreads", 1);
    FrenchPreprocessor preProcessor = new FrenchPreprocessor();
    FrenchPostprocessor postProcessor = new FrenchPostprocessor(options);
    
    CRFPostprocessor.setup(postProcessor, preProcessor, options);
    CRFPostprocessor.execute(nThreads, preProcessor, postProcessor);    
  }
}
