package edu.stanford.nlp.mt.base;

import java.text.DecimalFormat;

/**
 * A hypothesis with associated feature values and score under the current model.
 * 
 * @author danielcer
 * @author Spence Green
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
    final String delim = FlatPhraseTable.FIELD_DELIM;
    StringBuilder sb = new StringBuilder();
    sb.append(this.translation.toString());
    sb.append(' ').append(delim);
    DecimalFormat df = new DecimalFormat("0.####E0");
    if (features != null) {
      for (FeatureValue<FV> fv : this.features) {
        sb.append(' ')
        .append(fv.name)
        .append(": ")
        .append(
            (fv.value == (int) fv.value ? (int) fv.value : df
                .format(fv.value)));
      }
    }
    sb.append(' ').append(delim).append(' ');
    sb.append(df.format(this.score));
    if (latticeSourceId != -1) {
      sb.append(' ').append(delim).append(' ');
      sb.append(latticeSourceId);
    }
    return sb.toString();
  }
}
