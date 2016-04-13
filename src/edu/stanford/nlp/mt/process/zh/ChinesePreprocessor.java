package edu.stanford.nlp.mt.process.zh;

import java.util.List;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.mt.process.CRFPreprocessor;
import edu.stanford.nlp.wordseg.ChineseStringUtils;

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
  
  @Override
  protected String[] getSegmentedText(List<CoreLabel> doc, CRFClassifier<CoreLabel> crfSegmenter) {
    return ChineseStringUtils.combineSegmentedSentence(doc, crfSegmenter.flags).split("\\s+");
  }
}
