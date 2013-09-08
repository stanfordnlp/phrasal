package edu.stanford.nlp.mt.base;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A full hypothesis with various fields extracted from the featurizable
 * for convenience. Includes the featurizable for traversal through the
 * translation lattice.
 * 
 * @author danielcer
 * @author Spence Green
 * 
 * @param <TK>
 * @param <FV>
 */
public class RichTranslation<TK, FV> extends
    ScoredFeaturizedTranslation<TK, FV> {

  public final Sequence<TK> source;
  public final CoverageSet sourceCoverage;
  public final List<String> alignmentIndex;
  public final Featurizable<TK, FV> featurizable;
  
  /**
	 *
	 */
  public RichTranslation(Featurizable<TK, FV> f, double score,
      FeatureValueCollection<FV> features) {
    super((f == null ? new EmptySequence<TK>() : f.targetPrefix),
        features, score);
    this.featurizable = f;
    this.alignmentIndex = null;
    if (f == null) {
      this.source = new EmptySequence<TK>();
      this.sourceCoverage = null;
      return;
    }
    this.source = f.sourceSentence;
    this.sourceCoverage = (f.t2sAlignmentIndex != null) ? constructCoverageSet(f.t2sAlignmentIndex)
        : null;
  }

  /**
	 *
	 */
  public RichTranslation(Featurizable<TK, FV> f, double score,
      FeatureValueCollection<FV> features, List<String> alignmentIndex,
      long latticeSourceId) {
    super((f == null ? new EmptySequence<TK>() : f.targetPrefix),
        features, score, latticeSourceId);
    this.featurizable = f;
    this.alignmentIndex = alignmentIndex;
    if (f == null) {
      this.source = new EmptySequence<TK>();
      this.sourceCoverage = null;
      return;
    }
    this.source = f.sourceSentence;
    this.sourceCoverage = (f.t2sAlignmentIndex != null) ? constructCoverageSet(f.t2sAlignmentIndex)
        : null;
  }

  private static CoverageSet constructCoverageSet(int[][] t2fAlignmentIndex) {
    CoverageSet coverage = new CoverageSet();
    for (int[] range : t2fAlignmentIndex) {
      if (range != null)
        coverage.set(range[0], range[1]);
    }
    return coverage;
  }

  /**
   * Prints untokenized Moses n-best list for a given input segment. The n-best
   * list is currently not tokenized since tokenization would break the
   * alignment.
   * <p>
   * Sample output: <br>
   * 0 ||| lebanese president emile lahoud to a violent campaign in the chamber
   * of deputies , which was held yesterday in the regular legislative session
   * turned into a " trial " of the president of the republic for its position
   * on the international court and " observations " made here on this subject .
   * ||| d: -12 -2.00517 -1.14958 -5.62344 -1.51436 -0.408961 -3.67606 lm:
   * -206.805 tm: -44.5496 -81.3977 -35.8545 -77.5407 19.9979 w: -52 |||
   * -10.2091 ||| 2=0 0-1=1 3=2 4-5=3-4 6-7=5-7 8-10=8-13 11-14=14-19 17=20
   * 15-16=21-22 18-19=23-25 20-21=26-27 22-23=28-30 24-25=31-34 26-29=35-39
   * 30-33=40-43 34-35=44-45 36-38=46 39=47 40-42=48-50 43=51
   * 
   * @param id
   *          Segment id
   * @param sbuf
   *          Where to append the output to
   * @param nbestWordInternalAlignments 
   */
  public void nbestToMosesStringBuilder(int id, StringBuilder sbuf, boolean nbestWordInternalAlignments) {
    final String delim = FlatPhraseTable.FIELD_DELIM;
    sbuf.append(id);
    sbuf.append(' ').append(delim).append(' ');
    sbuf.append(this.translation);
    sbuf.append(' ').append(delim);
    DecimalFormat df = new DecimalFormat("0.####E0");
    if (features != null) {
      for (FeatureValue<FV> fv : this.features) {
        sbuf.append(' ')
        .append(fv.name)
        .append(": ")
        .append(
            (fv.value == (int) fv.value ? (int) fv.value : df
                .format(fv.value)));
      }
    }
    sbuf.append(' ').append(delim).append(' ');
    sbuf.append(df.format(this.score)).append(' ').append(delim);

    if (nbestWordInternalAlignments && Featurizable.alignmentsEnabled()) {
      // Internal alignments
      String alignmentString = sourceTargetAlignmentString();
      sbuf.append(" ").append(alignmentString);
    } else {
      // Phrase segmentation
      for (String el : alignmentIndex)
        sbuf.append(" ").append(el);
    }

    if (System.getProperty("VERY_VERBOSE_NBEST") != null) {
      sbuf.append(' ').append(delim).append(' ');
      sbuf.append(this.featurizable.sourceSentence.toString());
      sbuf.append(' ').append(delim).append(' ');
      List<Featurizable<TK,FV>> featurizables = featurizables();
      for (Featurizable<TK,FV> f : featurizables) {
        sbuf.append(' ');
        double parentScore = (f.prior == null ? 0 : f.prior.derivation.score);
        sbuf.append("|").append(f.derivation.score - parentScore).append(" ");
        sbuf.append(f.derivation.rule.sourceCoverage).append(" ");
        sbuf.append(f.derivation.rule.abstractRule.target.toString());
      }
    }
  }

  /**
   * Pull out word-to-word source->target alignments.
   * 
   * @return
   */
  public String sourceTargetAlignmentString() {
    StringBuilder[] sourceAlignments = new StringBuilder[featurizable.sourceSentence.size()];
    List<Featurizable<TK,FV>> featurizableList = featurizables();
    for (Featurizable<TK,FV> featurizable : featurizableList) {
      int srcPosition = featurizable.sourcePosition;
      int tgtPosition = featurizable.targetPosition;
      int tgtLength = featurizable.targetPhrase.size();
      PhraseAlignment al = featurizable.rule.abstractRule.alignment;
      for (int i = 0; i < tgtLength; ++i) {
        int[] sIndices = al.t2s(i);
        if (sIndices != null) {
          String tgtIndexStr = String.valueOf(tgtPosition + i);
          for (int srcOffset : sIndices) {
            int srcIndex = srcPosition + srcOffset;
            if (sourceAlignments[srcIndex] == null) {
              sourceAlignments[srcIndex] = new StringBuilder();
              sourceAlignments[srcIndex].append(tgtIndexStr);
            } else {
              sourceAlignments[srcIndex].append(",").append(tgtIndexStr);
            }
          }
        }
      }
    }
    StringBuilder sb = new StringBuilder();
    boolean isFirst = true;
    for (int i = 0; i < sourceAlignments.length; ++i) {
      if (sourceAlignments[i] != null) {
        if ( ! isFirst) sb.append(" ");
        sb.append(String.valueOf(i)).append("-").append(sourceAlignments[i].toString());
        isFirst = false;
      }
    }
    return sb.toString();
  }

  /**
   * Extract all featurizables in order from the null hypothesis to the goal
   * hypothesis.
   * 
   * @return
   */
  private List<Featurizable<TK,FV>> featurizables() {
    List<Featurizable<TK,FV>> listFeaturizables = new ArrayList<Featurizable<TK,FV>>();
    featurizableHelper(this.featurizable, listFeaturizables);
    Collections.reverse(listFeaturizables);
    return listFeaturizables;
  }
  
  private void featurizableHelper(Featurizable<TK,FV> f, List<Featurizable<TK,FV>> l) {    
    if (f == null) return;      
    l.add(f);
    featurizableHelper(f.prior, l);
  }

}
