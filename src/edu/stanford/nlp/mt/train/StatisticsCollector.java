package edu.stanford.nlp.mt.train;


/**
 * Interface for collecting global statistics from training data.
 * 
 * @author Pi-Chuan Chang
 */
public interface StatisticsCollector {

  /**
   * Returns the number of passes over the entire training data are required
   * by the feature extractor. 
   */
  public int getNumPasses();

  /**
   * Extract features from sentence word alignment.
   * Note that it is implicitely assumed that each class implementing
   * StatisticsCollector is mainining its own data structures in order to keep track of the
   * statistics it needs. Hence, extract is returning void.
   * (more explanation?)
   */
  public void collect(WordAlignment sent);

  /**
   * Anything that needs to be done after collecting the sufficent statistics from the data
   */
  public void postProcess();

  /**
   * Provide some method for serializing Statistics to a file
   */
  public void serializeTo(String filename);

  /**
   * Provide some method for reading Statistics from a file
   */
  public void loadFrom(String filename);
}
