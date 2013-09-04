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
    String uncased = toUncased(input.trim());
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
      String[] inputFields = inputToken.split("\\s+");
      String[] outputFields = outputToken.split("\\s+");
      if (inputFields.length == outputFields.length) {
        outputStrings.addAll(Arrays.asList(outputFields));
        originalStrings.addAll(Arrays.asList(inputFields));
      
      } else {
        throw new RuntimeException("Non-invertible input: " + input);
      }
    }
    
    Sequence<IString> inputSequence = IStrings.tokenize(input);
    Sequence<IString> outputSequence = 
        new SimpleSequence<IString>(true, IStrings.toIStringArray(outputStrings)); 
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
