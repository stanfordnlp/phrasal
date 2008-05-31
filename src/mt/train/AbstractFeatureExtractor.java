package mt.train;

import java.util.Properties;

import edu.stanford.nlp.util.Index;

/**
 * Feature extractor offering default implementations for members of
 * FeatureExtractor.
 *
 * @author Michel Galley
 */
public abstract class AbstractFeatureExtractor implements FeatureExtractor {

  private int currentPass = 0;
  AlignmentTemplates alTemps;
  Properties prop;
  Index<String> featureIndex;

  /**
   * Initialize variables possibly needed by feature extractors.
   *
   * @param prop Command-line arguments.
   * @param featureIndex Index for feature names.
   * @param alTemps Set of all alignment templates seen so far in training data.
   */
  public void init(Properties prop, Index<String> featureIndex, AlignmentTemplates alTemps) {
    this.prop = prop;
    this.featureIndex = featureIndex;
    this.alTemps = alTemps;
  }

  /**
   * Returns the number of passes over the entire training data are required
   * by the feature extractor. Unless this method is overridden, the default is 1.
   */
  public int getRequiredPassNumber() { return 1; }

  /**
   * Set the number of passes over training data that have been completed so far.
   */
  protected int getCurrentPass() { return this.currentPass; }

  /**
   * Returns the number of passes over training data that have been completed so far.
   */
  public void setCurrentPass(int currentPass) { this.currentPass = currentPass; }

  /** 
   * By default, this class does not require an alignment grid for feature extraction.
   */
  public boolean needAlGrid() { return false; }

  /**
   * By default, this class does not give names to features.
   */
  public Object scoreNames() { return null; }

  /**
   * Empty sentence-level feature extractor.
   */
  public void extract(SymmetricalWordAlignment sent, String info, AlignmentGrid alGrid) {}

  /**
   * Empty phrase-level feature extractor.
   */
  public void extract(AlignmentTemplateInstance alTemp, AlignmentGrid alGrid) {}

  /**
   * By default, this feature extractor returns null.
   */
  public Object score(AlignmentTemplate alTemp) { return null; }

  /**
   * Let each extractor output some stuff to STDERR.
   */
  public void report() { return; }
}

