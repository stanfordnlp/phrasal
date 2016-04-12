package edu.stanford.nlp.mt.process.zh;

import java.util.Locale;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.mt.process.CRFPreprocessor;
import edu.stanford.nlp.mt.process.CoreNLPPreprocessor;
import edu.stanford.nlp.mt.process.MosesCompoundSplitter;
import edu.stanford.nlp.process.PTBTokenizer;

/**
 * Pre-processor for Chinese based on CRF classifier.
 *  
 * @author Joern Wuebker
 *
 */
public class ChinesePreprocessor extends CRFPreprocessor {
  
  public ChinesePreprocessor(CRFClassifier<CoreLabel> crfSegmenter) {
    super(crfSegmenter);
  }
  
  public ChinesePreprocessor(String options) {
    super(options);
  }
}
