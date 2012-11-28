package edu.stanford.nlp.mt.base;

/**
 * A hypothesis with associated feature values and score under the current model.
 * 
 * @author danielcer
 * 
 * @param <TK>
 * @param <FV>
 */
public class ScoredFeaturizedTranslation<TK, FV> extends
    FeaturizedTranslation<TK, FV> implements
    Comparable<ScoredFeaturizedTranslation<TK, FV>> {
  
  public final long latticeSourceId;
  
  /**
   * Hypothesis score
   */
  public double score;

  /**
	 * 
	 */
  public ScoredFeaturizedTranslation(Sequence<TK> translation,
      FeatureValueCollection<FV> features, double score) {
    super(translation, features);
    this.score = score;
    this.latticeSourceId = -1;
  }

  /**
	 * 
	 */
  public ScoredFeaturizedTranslation(Sequence<TK> translation,
      FeatureValueCollection<FV> features, double score, long latticeSourceId) {
    super(translation, features);
    this.score = score;
    this.latticeSourceId = latticeSourceId;
  }

  /**
   * Have sort place things in descending order
   */
  @Override
  public int compareTo(ScoredFeaturizedTranslation<TK, FV> o) {
    return (int) Math.signum(o.score - score);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(translation).append(" |||");
    if (features == null) {
    	sb.append(" NoFeatures: 0");
    } else {
	    for (FeatureValue<FV> fv : features) {
	      if (fv == null) {
	    	  sb.append(" ").append("NullFeature: 0");
	      } else {
	    	  sb.append(" ").append(fv.name).append(": ").append(fv.value);
	      }
	    }
    }
    sb.append(" ||| ").append(score);
    if (latticeSourceId >= 0)
      sb.append(" ||| ").append(latticeSourceId);

    return sb.toString();
  }
}
