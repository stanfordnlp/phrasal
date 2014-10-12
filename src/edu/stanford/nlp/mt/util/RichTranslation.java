package edu.stanford.nlp.mt.util;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.tm.FlatPhraseTable;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.util.Generics;

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
public class RichTranslation<TK, FV> extends ScoredFeaturizedTranslation<TK, FV> {
  private final Sequence<TK> source;
  private final Featurizable<TK, FV> featurizable;

  /**
   * Constructor.
   * 
   * @param goalHyp
   * @param score
   * @param features
   * @param latticeSourceId
   */
  public RichTranslation(Derivation<TK, FV> goalHyp, double score,
      FeatureValueCollection<FV> features, long latticeSourceId) {
    super(((goalHyp == null || goalHyp.featurizable == null) ? 
        new EmptySequence<TK>() : goalHyp.featurizable.targetPrefix), features, score, latticeSourceId);
    this.featurizable = (goalHyp == null) ? null : goalHyp.featurizable;
    this.source = (this.featurizable == null) ? new EmptySequence<TK>() : this.featurizable.sourceSentence;
    this.goalHyp = goalHyp;
  }
  
 
  /**
   * Access the underlying featurizable.
   * 
   * @return
   */
  public Featurizable<TK, FV> getFeaturizable() { return featurizable; }

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
   * @param featurePattern
   *          Only features that match this pattern will be printed. Set to null to
   *          print all features.
   * @param bolt
   *          Print additional information required for BOLT submissions.
   * @param printHistory
   *          Print the derivation history. 
   */
  public void nbestToMosesStringBuilder(int id, StringBuilder sbuf, Pattern featurePattern, boolean bolt, boolean printHistory) {
    final String delim = FlatPhraseTable.FIELD_DELIM;
    sbuf.append(id);
    sbuf.append(' ').append(delim).append(' ');
    sbuf.append(this.translation);
    sbuf.append(' ').append(delim);
    DecimalFormat df = new DecimalFormat("0.####E0");
    if (features != null) {
      for (FeatureValue<FV> fv : this.features) {
        String featureName = (String) fv.name;
        if (featurePattern == null || featurePattern.matcher(featureName).matches()) {
          sbuf.append(' ').append(fv.name).append(": ")
            .append((fv.value == (int) fv.value ? (int) fv.value : df.format(fv.value)));
        }
      }
    }
    sbuf.append(' ').append(delim).append(' ');
    sbuf.append(df.format(this.score)).append(' ').append(delim);

    // Internal Alignments
    if ( ! bolt) {
      // Simple Alignments
      String alignmentString = alignmentString();
      sbuf.append(" ").append(alignmentString);
    } else {
      // Very Verbose Alignments 
      sbuf.append(' ').append(this.featurizable.sourceSentence.toString());
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
    
    // Print derivation history
    if (printHistory){
      sbuf.append(' ').append(delim).append(' ');
      String historyString = historyString();
      sbuf.append(historyString);
    }
  }
  
  List<Featurizable<TK,FV>> featurizables() {
    List<Featurizable<TK,FV>> listFeaturizables = Generics.newArrayList();
    featurizables(this.featurizable, listFeaturizables);
    Collections.reverse(listFeaturizables);
    return listFeaturizables;
  }

  private void featurizables(Featurizable<TK,FV> f, List<Featurizable<TK,FV>> l) {
    if (f == null) {
      return;
    }
    l.add(f);
    featurizables(f.prior, l);
  }



  /**
   * Pull out word-to-word source-&gt;target alignments.
   * 
   * @return
   */
  public String alignmentString() {
    return alignmentGrid().toString();
  }
  
  /**
   * Create a source-target alignment grid.
   * 
   * @return
   */
  @SuppressWarnings("unchecked")
  public SymmetricalWordAlignment alignmentGrid() {
    // TODO(spenceg): Remove these casts if we remove the templating throughout the code.
    SymmetricalWordAlignment alignment = new SymmetricalWordAlignment(
        (Sequence<IString>) this.source, (Sequence<IString>) this.translation);
    
    for (Featurizable<TK,FV> f = this.featurizable; f != null; f = f.prior) {
      int srcPosition = f.sourcePosition;
      int tgtPosition = f.targetPosition;
      int tgtLength = f.targetPhrase.size();
      PhraseAlignment al = f.rule.abstractRule.alignment;
      if (al == null) {
        throw new RuntimeException("Alignments are not enabled. Cannot extract alignments from translation.");
      }
      for (int i = 0; i < tgtLength; ++i) {
        int[] sIndices = al.t2s(i);
        if (sIndices != null) {
          final int tgtIndex = tgtPosition + i;
          for (int srcOffset : sIndices) {
            int srcIndex = srcPosition + srcOffset;
            alignment.addAlign(srcIndex, tgtIndex);
          }
        }
      }
    }
    return alignment;
  }
}
