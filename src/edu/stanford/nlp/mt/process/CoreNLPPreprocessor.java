package edu.stanford.nlp.mt.process;

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.OriginalTextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.util.Characters;
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
   * Converts control characters to whitespace. The underlying CoreNLP tokenizers
   * typically delete these characters. In MT bitexts they usually function as
   * whitespace.
   * 
   * @param input
   * @return
   */
  private String removeControlCharacters(String input) {
    StringBuilder sb = new StringBuilder();
    int inputLength = input.length();
    for (int i = 0; i < inputLength; ++i) {
      char c = input.charAt(i);
      sb.append(Characters.isControl(c) ? " " : c);
    }
    return sb.toString();
  }
  
  @Override
  public Sequence<IString> process(String input) {
    String uncased = removeControlCharacters(toUncased(input.trim()));
    Tokenizer<CoreLabel> tokenizer = tf.getTokenizer(new StringReader(uncased));
    List<String> outputStrings = Generics.newLinkedList();
    while (tokenizer.hasNext()) {
      String string = tokenizer.next().get(TextAnnotation.class);
      outputStrings.add(string);
    }
    return IStrings.toIStringSequence(outputStrings);
  }
  
  @Override
  public SymmetricalWordAlignment processAndAlign(String input) {
    String uncased = removeControlCharacters(toUncased(input.trim()));
    Tokenizer<CoreLabel> tokenizer = tf.getTokenizer(new StringReader(uncased));
    List<CoreLabel> outputTokens = tokenizer.tokenize();
    
    // Convert tokenized string to sequence
    List<String> originalStrings = Generics.newArrayList(outputTokens.size());
    List<String> outputStrings = Generics.newArrayList(outputTokens.size());
    for (CoreLabel token : outputTokens) {
      String outputToken = token.get(TextAnnotation.class);
      String inputToken = token.get(OriginalTextAnnotation.class);
      if (inputToken == null) {
        throw new RuntimeException("Invertible option not set in preprocessor/tokenizer");
      }
      
      // Oooh. This is gross and slow. But some core tokenizers return whitespace
      // in a CoreLabel. The MT system never does that.
      List<String> inputFields = Arrays.asList(inputToken.split("\\s+"));
      List<String> outputFields = Arrays.asList(outputToken.split("\\s+"));
      if (inputFields.size() == outputFields.size()) {
        outputStrings.addAll(outputFields);
        originalStrings.addAll(inputFields);
      
      } else {
        // Skip token
        System.err.printf("%s: Skipping non-invertible token ||%s|| -> ||%s||%n", this.getClass().getName(),
            inputToken, outputToken);
      }
    }
    
    Sequence<IString> inputSequence = IStrings.tokenize(input);
    Sequence<IString> outputSequence = IStrings.toIStringSequence(outputStrings);
    SymmetricalWordAlignment alignment = new SymmetricalWordAlignment(inputSequence, outputSequence);
    
    // Generate the alignments
    int j = 0;
    for (int i = 0; i < inputSequence.size(); ++i) {
      if (j >= outputTokens.size()) {
        System.err.printf("%s: WARNING: Non-invertible input: %s%n", this.getClass().getName(), input);
        break;
      }
      String uncasedInputToken = toUncased(inputSequence.get(i).toString());
      List<Integer> targets = Generics.newLinkedList();
      StringBuilder sb = new StringBuilder();
      String original = originalStrings.get(j);
      targets.add(j++);
      sb.append(original);
      while ( ! uncasedInputToken.equals(original) && j < outputSequence.size()) {
        sb.append(originalStrings.get(j));
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
