package edu.stanford.nlp.mt.train;


/**
 * Interface for extracting sentence-level and phrase-level features from training data.
 * 
 * @author Michel Galley
 */
public interface FeatureExtractor {

  // TODO: add fuction to score only source or target language phrases
  // (used in some lex re-ordering models).

  /**
   * Returns true if the feature extractor needs to have access to access
   * to the alignment grid, which may contain various information such as 
   * phrase positions.
   * @see AlignmentGrid
   */
  public boolean needAlGrid();

  /**
   * Extract features from sentence word alignment.
   * Note that it is implicitely assumed that each class implementing
   * FeatureExtractor is mainining its own data structures in order to assign 
   * scores with {@link #score(AlignmentTemplate)}. Hence, extract is 
   * returning void. As opposed to 
   * {@link #extract(AlignmentTemplateInstance,AlignmentGrid)}, which is called
   * once for each instance of alignment template, this function is called once for 
   * each sentence pair.
   */
  public void extract(SymmetricalWordAlignment sent, String info, AlignmentGrid alGrid);

  /**
   * Extract features from phrase pair f-e and sentence word alignment.
   * Note that it is implicitely assumed that each class implementing
   * FeatureExtractor is mainining its own data structures in order to assign scores with
   * {@link #score(AlignmentTemplate)}. Hence, extract is returning void.
   *
   * @param alTemp A particular occurrence of an alignment template.
   * @param alGrid A two dimensional array, in which each cell (i,j) contains the set of 
   * AlignmentTemplateInstance objects that have one of their four corners lying at (i,j)
   * in the alignment grid. Not that if {@link #needAlGrid} returns false, alTemps
   * is always null.
   */
  public void extract(AlignmentTemplateInstance alTemp, AlignmentGrid alGrid);

  /**
   * Assign here a value for each alignment template and each feature. Return value must 
   * currently be an instance of double[].
   *
   * @param alTemp Alignment template to score.
   * @return Feature values, which must either be represented as double[].
   */
  public Object score(AlignmentTemplate alTemp);

  /**
   * Names of features returned by {@link #score(AlignmentTemplate)}. 
   */
  public Object scoreNames();

  /**
   * Called to report the status of  each FeatureExtrator, but only if a
   * specific output is provided with the FeatureExtractor. Note: do not
   * output to STDOUT since that will mess up the combined behavior of the
   * CombinedFeatureExtractor.
   */
  public void report();

}
