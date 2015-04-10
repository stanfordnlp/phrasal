package edu.stanford.nlp.mt.tm;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.RuleFeaturizer;
import edu.stanford.nlp.mt.decoder.util.RuleGrid;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.ParallelSuffixArray;
import edu.stanford.nlp.mt.util.ParallelSuffixArray.SentenceSample;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.util.TranslationModelIndex;

/**
 * 
 * See here for lex re-ordering: https://github.com/moses-smt/mosesdecoder/commit/51824355f9f469c186d5376218e3396b92652617
 * 
 * @author Spence Green
 *
 */
public class DynamicTranslationModel<TK,FV> implements TranslationModel<TK,FV>,Serializable {

  /**
   * TODO(spenceg) Implement kryo
   */
  private static final long serialVersionUID = 5876435802959430120L;
  
  public ParallelSuffixArray sa;
  
  public DynamicTranslationModel(ParallelSuffixArray suffixArray) {
    this.sa = suffixArray;
  }

  @Override
  public List<ConcreteRule<TK, FV>> getRules(Sequence<TK> source,
      InputProperties sourceInputProperties, List<Sequence<TK>> targets,
      int sourceInputId, Scorer<FV> scorer) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public RuleGrid<TK, FV> getRuleGrid(Sequence<TK> source,
      InputProperties sourceInputProperties, List<Sequence<TK>> targets,
      int sourceInputId, Scorer<FV> scorer) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public int longestSourcePhrase() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int longestTargetPhrase() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public void setFeaturizer(RuleFeaturizer<TK, FV> featurizer) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public List<String> getFeatureNames() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getName() {
    // TODO Auto-generated method stub
    return null;
  }
  
  @Override
  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
  
  // TODO(spenceg) query functions for dense features and lex re-ordering
  // Queries should prune higher-order n-grams
  
  // TODO(spenceg) Setup caches
  // Top-k rules
  // LRU cache
  
  public static void main(String[] args) {
    String fileName = args[0];
    String queryString = args[1];
    
    try {
      DynamicTranslationModel<IString,String> tm = IOTools.deserialize(fileName);
      TranslationModelIndex.setSystemIndex(tm.sa.getIndex());      
//      PrintWriter pw = new PrintWriter(System.out);
//      tm.sa.print(true, pw);
//      pw.flush();
//      System.exit(-1);
      
      Sequence<IString> query = IStrings.tokenize(queryString);
      int[] queryInts = Sequences.toIntArray(query);
      System.out.printf("%s :: %d%n", queryString, tm.sa.count(queryInts, true));
      List<SentenceSample> samples = tm.sa.sample(queryInts, true, 100);
      for (SentenceSample sample : samples) {
        System.out.printf("%d\t%s%n", sample.sentenceId, sample.sentence.getSource(tm.sa.getIndex()));
      }
      
    } catch (ClassNotFoundException | IOException e) {
      e.printStackTrace();
    }
  }
  
}
