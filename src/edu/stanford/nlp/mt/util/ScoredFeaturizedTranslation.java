package edu.stanford.nlp.mt.util;

import java.text.DecimalFormat;
import java.util.Stack;

import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.FlatPhraseTable;

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
  public Derivation<TK, FV> goalHyp = null; // Thang May14: to extract rules
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
        if(goalHyp!=null){// Thang May14: only print LM scores for the moment. TODO: have a more general option to control what will be printed out
          if(fv.name.equals("LM")){
            sb.append(' ').append(fv.name).append(": ").append(
                (fv.value == (int) fv.value ? (int) fv.value : df.format(fv.value)));  
          }
        } else {
          sb.append(' ').append(fv.name).append(": ").append(
              (fv.value == (int) fv.value ? (int) fv.value : df.format(fv.value)));
        }
      }
    }
    sb.append(' ').append(delim).append(' ');
    sb.append(df.format(this.score));
    if (latticeSourceId != -1) {
      sb.append(' ').append(delim).append(' ');
      sb.append(latticeSourceId);
    }
    
    // Thang May14: print derivation history
    if(goalHyp!=null){
      sb.append(' ').append(delim).append(' ');
      String historyString = historyString();
      sb.append(historyString);
    }
    
    return sb.toString();
  }
  
  // Thang May14: copy toString, to debug CubePrunningDecoder/CubePrunningNNLMDecoder
  public String toStringNoLatticeId() {
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
    return sb.toString();
  }
  
  // Thang May14
  public String ruleInfo(ConcreteRule<TK,FV> rule){
    return String.format("%s <r> %s <r> %d <r> %s", rule.abstractRule.source,
        rule.abstractRule.target, rule.sourcePosition, rule.abstractRule.alignment);
    
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
    if(goalHyp!=null){
      // get a stack of hyp
      Derivation<TK, FV> hyp = goalHyp;
      Stack<Derivation<TK, FV>> hypStack = new Stack<Derivation<TK, FV>>();
      for (; hyp != null; hyp = hyp.preceedingDerivation) { hypStack.add(hyp); }
      
      // traverse from the beginning hypothesis until the final one
      while(!hypStack.isEmpty()){
        hyp = hypStack.pop();
        if(hyp.rule!=null){ // not the first hypothesis
          if(hypStack.isEmpty()) { // last one
            sb.append(ruleInfo(hyp.rule));
          } else {
            sb.append(ruleInfo(hyp.rule) + " |R| ");
          }
        }
      }
    }
    
    return sb.toString();
  }
}
