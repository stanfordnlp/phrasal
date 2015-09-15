package edu.stanford.nlp.mt.util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.DecimalFormat;
import java.util.Stack;
import java.util.regex.Pattern;

import edu.stanford.nlp.mt.tm.CompiledPhraseTable;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;

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
  private static final long serialVersionUID = 6683028704195476157L;
  
  public Sequence<TK> source;
  private final transient Featurizable<TK, FV> featurizable;
  private String f2eAlignment;

  /**
   * Constructor.
   * 
   * @param score
   * @param features
   * @param latticeSourceId
   */
  public RichTranslation(Featurizable<TK, FV> featurizable, double score,
      FeatureValueCollection<FV> features, long latticeSourceId) {
    super((featurizable == null ? Sequences.emptySequence() : featurizable.targetSequence), 
        features, score, latticeSourceId);
    this.featurizable = featurizable;
    this.source = featurizable == null ? Sequences.emptySequence() : featurizable.sourceSentence;
  }
  
  /**
   * Custom serializer.
   * 
   * @param oos
   */
  private void writeObject(ObjectOutputStream oos) throws IOException {
    oos.defaultWriteObject();
    oos.writeLong(latticeSourceId);
    oos.writeDouble(score);
    oos.writeUTF(source.toString());
    oos.writeUTF(translation.toString());
    oos.writeUTF(f2eAlignment == null ? alignmentString() : f2eAlignment);
    oos.writeObject(this.features);
  }

  /**
   * Custom deserializer.
   * 
   * @param ois
   * @throws ClassNotFoundException
   * @throws IOException
   */
  @SuppressWarnings("unchecked")
  private void readObject(ObjectInputStream ois)
      throws ClassNotFoundException, IOException {
    ois.defaultReadObject();
    this.latticeSourceId = ois.readLong();
    this.score = ois.readDouble();
    this.source = (Sequence<TK>) IStrings.tokenize(ois.readUTF());
    this.translation = (Sequence<TK>) IStrings.tokenize(ois.readUTF());
    this.f2eAlignment = ois.readUTF();
    this.features = (FeatureValueCollection<FV>) ois.readObject();
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
    final String delim = CompiledPhraseTable.FIELD_DELIM;
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

    if ( ! bolt) {
      // Simple Alignments
      String alignmentString = alignmentString();
      sbuf.append(" ").append(alignmentString);
    } else {
      // Very Verbose Alignments 
      sbuf.append(' ').append(this.featurizable.sourceSentence.toString());
      sbuf.append(' ').append(delim).append(' ');
      Stack<Featurizable<TK,FV>> featurizables = featurizables();
      Featurizable<TK,FV> f = null;
      while ( ! featurizables.isEmpty()) {
        f = featurizables.pop();
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
    sbuf.append(" ").append(delim).append(" ").append(this.featurizable.debugStates());
  }
  
  Stack<Featurizable<TK,FV>> featurizables() {
    Stack<Featurizable<TK, FV>> featurizablesStack = new Stack<Featurizable<TK, FV>>();
    for (Featurizable<TK,FV> f = this.featurizable; f != null; f = f.prior) { 
      featurizablesStack.add(f); 
    }
    return featurizablesStack;
  }
  
  // Thang May14: copy toString, to debug CubePrunningDecoder/CubePrunningNNLMDecoder
  public String toStringNoLatticeId() {
    final String delim = CompiledPhraseTable.FIELD_DELIM;
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
    return sb.toString();
  }
    
  /**
   * Print out list of rules participating in building up this translation
   * Useful for JointNNLM model
   * 
   * @return
   */
  // Thang May14
  public String historyString() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    Stack<Featurizable<TK, FV>> featurizables = featurizables();
    Featurizable<TK,FV> f = null;
    while ( ! featurizables.isEmpty()) {
      f = featurizables.pop();
      sb.append(f.rule).append(nl);
    }
    return sb.toString();
  }


  /**
   * Pull out word-to-word source-&gt;target alignments.
   * 
   * @return
   */
  public String alignmentString() {
    if (f2eAlignment == null) f2eAlignment = alignmentGrid().toString();
    return f2eAlignment;
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
