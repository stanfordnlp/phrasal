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
public abstract class CoreNLPPreprocessor implements Preprocessor {

 protected final TokenizerFactory<CoreLabel> tf;
  
  public CoreNLPPreprocessor(TokenizerFactory<CoreLabel> tf) {
    this.tf = tf;
  }
  
  @Override
  public SymmetricalWordAlignment process(String input) {
    String uncased = toUncased(input);
    Tokenizer<CoreLabel> tokenizer = tf.getTokenizer(new StringReader(uncased.trim()));
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
    int j = 0;
    for (int i = 0; i < inputSequence.size(); ++i) {
      if (j >= outputTokens.size()) {
        System.err.println("WARNING: Non-invertible input: " + input);
        break;
      }
      String uncasedInputToken = toUncased(inputSequence.get(i).toString());
      List<Integer> targets = Generics.newLinkedList();
      StringBuilder sb = new StringBuilder();
      String original = outputTokens.get(j).get(OriginalTextAnnotation.class);
      targets.add(j++);
      sb.append(original);
      while ( (! uncasedInputToken.equals(original)) && j < outputSequence.size()) {
        sb.append(outputTokens.get(j).get(OriginalTextAnnotation.class));
        targets.add(j++);
        original = sb.toString();
      }
      for (int targetIndex : targets) {
        alignment.addAlign(i, targetIndex);
      }
    }
    
    return alignment;
  }

  @Override
  public abstract String toUncased(String input);
}
