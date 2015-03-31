package edu.stanford.nlp.mt.train;

import edu.stanford.nlp.mt.util.ParallelSuffixArray;
import edu.stanford.nlp.mt.util.TranslationModelIndex;

/**
 * 
 * See here for lex re-ordering: https://github.com/moses-smt/mosesdecoder/commit/51824355f9f469c186d5376218e3396b92652617
 * 
 * @author Spence Green
 *
 */
public class DynamicTranslationModel {

  public ParallelSuffixArray sa;
  public TranslationModelIndex idx;
  
  public DynamicTranslationModel(ParallelSuffixArray suffixArray, TranslationModelIndex idx) {
    this.sa = suffixArray;
    this.idx = idx;
  }
  
  // TODO(spenceg) query functions for dense features and lex re-ordering
  // Queries should prune higher-order n-grams
  
  // TODO(spenceg) Setup caches
  // Top-k rules
  // LRU cache
}
