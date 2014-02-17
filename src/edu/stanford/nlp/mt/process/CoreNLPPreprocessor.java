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
  
  /**
   * Pass options to the underlying tokenizer.
   * 
   * @param options
   */
  public void setOptions(String options) {
    tf.setOptions(options);
  }
  
  @Override
  public Sequence<IString> process(String input) {
    String tokenizerInput = toUncased(input.trim());
    Tokenizer<CoreLabel> tokenizer = tf.getTokenizer(new StringReader(tokenizerInput));
    List<String> outputStrings = Generics.newLinkedList();
    while (tokenizer.hasNext()) {
      String string = tokenizer.next().get(TextAnnotation.class);
      outputStrings.add(string);
    }
    return IStrings.toIStringSequence(outputStrings);
  }
  
  @Override
  public SymmetricalWordAlignment processAndAlign(String input) {
    input = input.trim();

    // Run through the tokenizer and convert to sequence
    String tokenizerInput = toUncased(input);
    String[] uncasedInputTokens = tokenizerInput.split("\\s+");
    Tokenizer<CoreLabel> tokenizer = tf.getTokenizer(new StringReader(tokenizerInput));
    List<CoreLabel> outputTokens = tokenizer.tokenize();
    IString[] outputSequence = new IString[outputTokens.size()];
    for (int i = 0; i < outputSequence.length; ++i) {
      String outputToken = outputTokens.get(i).get(TextAnnotation.class);
      outputSequence[i] = new IString(outputToken);
    }
    
    // Whitespace tokenization of input, create alignment
    Sequence<IString> inputSequence = IStrings.tokenize(input);
    assert inputSequence.size() == uncasedInputTokens.length;
    SymmetricalWordAlignment alignment = new SymmetricalWordAlignment(inputSequence, 
        new SimpleSequence<IString>(true, outputSequence));
    
    // Generate the alignments
    StringBuilder inputToken = new StringBuilder();
    for (int i = 0, j = 0, limit = outputTokens.size(); j < limit; ++j) {
      CoreLabel tokenizedToken = outputTokens.get(j);
      String inputTokenPart = toUncased(tokenizedToken.get(OriginalTextAnnotation.class));
      alignment.addAlign(i, j);

      inputToken.append(inputTokenPart);
      if (i >= uncasedInputTokens.length) {
        System.err.println("WARNING: Non-invertible input: " + input);
        break;
      }
      if (uncasedInputTokens[i].equals(inputToken.toString())) {
        ++i;
        inputToken = new StringBuilder();
      }
    }
    return alignment;
  }

  @Override
  public abstract String toUncased(String input);
}
