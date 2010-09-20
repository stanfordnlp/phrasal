package edu.stanford.nlp.mt.preproc;

import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;



/**
  * Interface to preprocessor for source- and target-language sentences.
  * Language specific processing should be in a different package, e.g.,
  * edu.stanford.nlp.mt.chinese.
  * 
  * @author Michel Galley
  */
public interface InputSentencePreprocessor {
  
  void process(SymmetricalWordAlignment sent);
}
