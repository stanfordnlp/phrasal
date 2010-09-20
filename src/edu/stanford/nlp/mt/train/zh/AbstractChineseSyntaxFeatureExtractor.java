package edu.stanford.nlp.mt.train.zh;

import edu.stanford.nlp.mt.train.AbstractFeatureExtractor;
import edu.stanford.nlp.mt.train.AlignmentGrid;
import edu.stanford.nlp.mt.train.AlignmentTemplateInstance;
import edu.stanford.nlp.mt.train.AlignmentTemplates;

import java.util.Properties;
import edu.stanford.nlp.util.Index;

/**
 * Feature extractor offering default implementations for members of
 * FeatureExtractor.
 * 
 * @author Michel Galley
 */
public abstract class AbstractChineseSyntaxFeatureExtractor<E> extends
    AbstractFeatureExtractor {

  private int currentPass = 0;
  AlignmentTemplates alTemps;
  Properties prop;
  Index<E> featureIndex;

  /**
   * Set the number of passes over training data that have been completed so
   * far.
   */
  @Override
  protected int getCurrentPass() {
    return this.currentPass;
  }

  /**
   * Returns the number of passes over training data that have been completed so
   * far.
   */
  @Override
  public void setCurrentPass(int currentPass) {
    this.currentPass = currentPass;
  }

  public void extract(AlignmentTemplateInstance alTemp, AlignmentGrid alGrid,
      AlignmentGrid fullAlGrid, String infoLine) {
  }

  public void report(AlignmentTemplates alTemps) {
  }
}
