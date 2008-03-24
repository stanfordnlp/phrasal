package mt;

import java.text.DecimalFormat;
import java.util.*;

/**
 * 
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class RichTranslation<TK,FV> extends ScoredFeaturizedTranslation<TK,FV> {
	public final Sequence<TK> foreign;
	public final CoverageSet foreignCoverage;
	public final int[][] t2fAlignmentIndex;
	public final int[][] f2tAlignmentIndex;
	
	/**
	 * 
	 * @param foreign
	 * @param translation
	 * @param score
	 * @param t2fAlignmentIndex
	 * @param f2tAlignmentIndex
	 * @param features
	 */
	public RichTranslation(Sequence<TK> foreign, Sequence<TK> translation, CoverageSet foreignCoverage, double score, int[][] t2fAlignmentIndex, int[][] f2tAlignmentIndex, List<FeatureValue<FV>> features) {
		super(translation, features, score);
		this.foreign = foreign;
		this.foreignCoverage = foreignCoverage;
		this.t2fAlignmentIndex = Arrays.copyOf(t2fAlignmentIndex, t2fAlignmentIndex.length);
		this.f2tAlignmentIndex = Arrays.copyOf(f2tAlignmentIndex, f2tAlignmentIndex.length);
	}
	
	/**
	 * 
	 * @param f
	 * @param score
	 * @param features
	 */
	public RichTranslation(Featurizable<TK,FV> f, double score, List<FeatureValue<FV>> features) {
		super((f == null ? new EmptySequence<TK>() : f.partialTranslation), features, score);
		if (f == null) {
			this.foreign = new EmptySequence<TK>();
			this.foreignCoverage = null;
			this.t2fAlignmentIndex = null;
			this.f2tAlignmentIndex = null;
			return;
		}
		this.foreign = f.foreignSentence;
		this.foreignCoverage = constructCoverageSet(f.t2fAlignmentIndex);
		this.t2fAlignmentIndex = f.t2fAlignmentIndex;
		this.f2tAlignmentIndex = f.f2tAlignmentIndex;
	}
	
	/**
	 * 
	 * @param f
	 * @param score
	 * @param features
	 * @param latticeSourceId
	 */
	public RichTranslation(Featurizable<TK,FV> f, double score, List<FeatureValue<FV>> features, long latticeSourceId) {
		super((f == null ? new EmptySequence<TK>() : f.partialTranslation), features, score, latticeSourceId);
		if (f == null) {
			this.foreign = new EmptySequence<TK>();
			this.foreignCoverage = null;
			this.t2fAlignmentIndex = null;
			this.f2tAlignmentIndex = null;
			return;
		}
		this.foreign = f.foreignSentence;
		this.foreignCoverage = constructCoverageSet(f.t2fAlignmentIndex);
		this.t2fAlignmentIndex = f.t2fAlignmentIndex;
		this.f2tAlignmentIndex = f.f2tAlignmentIndex;
	}
	
	private CoverageSet constructCoverageSet(int[][] t2fAlignmentIndex) {
		CoverageSet coverage = new CoverageSet();
		for (int[] range : t2fAlignmentIndex) {
			coverage.set(range[0], range[1]);
		}
		return coverage;
	}
	
	static public final String NBEST_SEP = "|||";
	
	public String nbestToString(int id) {
		StringBuffer sbuf = new StringBuffer();
		sbuf.append(id);
		sbuf.append(" ").append(NBEST_SEP).append(" ");
		sbuf.append(this.translation);
		sbuf.append(" ").append(NBEST_SEP);
		DecimalFormat df = new DecimalFormat("0.####E0"); 
		for (FeatureValue<FV> fv : FeatureValues.combine(this.features)) {
			sbuf.append(" ").append(fv.name).append(": ").append((fv.value == (int)fv.value ? (int)fv.value : df.format(fv.value)));
		}
		sbuf.append(" ").append(NBEST_SEP).append(" ");
		sbuf.append(df.format(this.score));
		if (latticeSourceId != -1) {
			sbuf.append(" ").append(NBEST_SEP).append(" ");
			sbuf.append(latticeSourceId);
		}
		return sbuf.toString();
	}
}
