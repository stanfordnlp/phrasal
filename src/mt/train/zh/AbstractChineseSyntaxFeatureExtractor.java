package mt.train.zh;
import mt.*;
import mt.train.AbstractFeatureExtractor;
import mt.train.AlignmentGrid;
import mt.train.AlignmentTemplateInstance;
import mt.train.AlignmentTemplates;

import java.util.Properties;
import edu.stanford.nlp.util.Index;

/**
 * Feature extractor offering default implementations for members of
 * FeatureExtractor.
 *
 * @author Michel Galley
 */
public abstract class AbstractChineseSyntaxFeatureExtractor<E> extends AbstractFeatureExtractor<E> {

  private int currentPass = 0;
  AlignmentTemplates alTemps;
  Properties prop;
  Index featureIndex;

  /**
   * Set the number of passes over training data that have been completed so far.
   */
  protected int getCurrentPass() { return this.currentPass; }

  /**
   * Returns the number of passes over training data that have been completed so far.
   */
  void setCurrentPass(int currentPass) { this.currentPass = currentPass; }

  public void extract(AlignmentTemplateInstance alTemp, AlignmentGrid alGrid, AlignmentGrid fullAlGrid, String infoLine) {}

  public void report(AlignmentTemplates alTemps) {};
}
