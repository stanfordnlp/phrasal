package edu.stanford.nlp.mt.base;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class ScoredFeaturizedTranslation<TK, FV> extends
		FeaturizedTranslation<TK, FV> implements Comparable<ScoredFeaturizedTranslation<TK,FV>>{
	public final long latticeSourceId;
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
		return (int)Math.signum(o.score - score);
	}

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(translation).append(" |||");
    for (FeatureValue<FV> fv : features)
      sb.append(" ").append(fv.name).append(": ").append(fv.value);
    sb.append(" ||| ").append(score);
    if (latticeSourceId >= 0)
      sb.append(" ||| ").append(latticeSourceId);
    return sb.toString();
  }
}
