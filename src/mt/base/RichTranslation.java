package mt.base;

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

	public String nbestToMosesString(int id) {
   /* 
   Sample output: 
     0 ||| lebanese president emile lahoud to a violent campaign in the chamber of deputies , which was held yesterday in the regular legislative session turned into a " trial " of the president of the republic for its position on the international court and " observations " made here on this subject .  ||| d: -12 -2.00517 -1.14958 -5.62344 -1.51436 -0.408961 -3.67606 lm: -206.805 tm: -44.5496 -81.3977 -35.8545 -77.5407 19.9979 w: -52 ||| -10.2091 ||| 2=0 0-1=1 3=2 4-5=3-4 6-7=5-7 8-10=8-13 11-14=14-19 17=20 15-16=21-22 18-19=23-25 20-21=26-27 22-23=28-30 24-25=31-34 26-29=35-39 30-33=40-43 34-35=44-45 36-38=46 39=47 40-42=48-50 43=51
    */
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
		sbuf.append(df.format(this.score)).append(" ").append(NBEST_SEP);
    // Alignment:
    for(int lastRangeEnd=-1, i=0; i<t2fAlignmentIndex.length; ++i) {
      int[] range = t2fAlignmentIndex[i];
      if(i+1<t2fAlignmentIndex.length && t2fAlignmentIndex[i][0] == t2fAlignmentIndex[i+1][0])
        continue;
      sbuf.append(" ").append(range[0]);
      // Foreign positions:
      if(range[0]+1 != range[1])
        sbuf.append("-").append(range[1]-1);
      // Translation positions:
      sbuf.append("=").append(lastRangeEnd+1);
      if(i != lastRangeEnd+1)
        sbuf.append("-").append(i);
      lastRangeEnd=i;
    }
		return sbuf.toString();
	}
}
