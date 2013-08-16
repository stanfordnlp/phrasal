package edu.stanford.nlp.mt.process;

import java.io.StringReader;
import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.OriginalTextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.util.Generics;

/**
 * Abstract class which is appropriate for the CoreNLP finite-state
 * pre-processors.
 * 
 * @author Spence Green
 *
 */
public class CoreNLPPreprocessor implements Preprocessor {

 protected final TokenizerFactory<CoreLabel> tf;
  
  public CoreNLPPreprocessor(TokenizerFactory<CoreLabel> tf) {
    this.tf = tf;
  }
  
  @Override
  public SymmetricalWordAlignment process(String input) {
    Tokenizer<CoreLabel> tokenizer = tf.getTokenizer(new StringReader(input));
    List<CoreLabel> outputTokens = tokenizer.tokenize();
    
    // Convert tokenized string to sequence
    List<String> outputStrings = Generics.newArrayList(outputTokens.size());
    for (CoreLabel token : outputTokens) {
      outputStrings.add(token.get(TextAnnotation.class));
    }
    Sequence<IString> outputSequence = 
        new SimpleSequence<IString>(true, IStrings.toIStringArray(outputStrings)); 
    Sequence<IString> inputSequence = IStrings.tokenize(input);
    
    // Generate the alignment
    SymmetricalWordAlignment alignment = new SymmetricalWordAlignment(inputSequence, outputSequence);
    int i = 0;
    for (int j = 0; j < outputTokens.size(); ++j) {
      if ( ! inputSequence.get(i).toString().equals(outputTokens.get(j).get(OriginalTextAnnotation.class))) {
        ++i;
      }
      alignment.addAlign(i, j);
      assert inputSequence.get(i).toString().equals(outputTokens.get(j).get(OriginalTextAnnotation.class));
    }
    
    return alignment;
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    // TODO Auto-generated method stub

  }

}
