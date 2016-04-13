package edu.stanford.nlp.mt.process;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.ArraySequence;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.StringUtils;


/**
 * Abstract class for preprocessing/segmentation based on CRFs, e.g. for Chinese or Arabic.
 * 
 * @author Joern Wuebker
 *
 */
public abstract class CRFPreprocessor implements Preprocessor {

  protected final CRFClassifier<CoreLabel> crfSegmenter;

  public CRFPreprocessor(CRFClassifier<CoreLabel> crfSegmenter) {
    this.crfSegmenter = crfSegmenter;
  }
  
  public CRFPreprocessor(String options) {
    this.crfSegmenter = loadClassifier(options);
  }
  
  public static CRFClassifier<CoreLabel> loadClassifier(String options) throws IllegalArgumentException {
    String[] inputFlags = options.split(" ");
    Properties props = StringUtils.argsToProperties(inputFlags);
    SeqClassifierFlags flags = new SeqClassifierFlags(props);
    CRFClassifier<CoreLabel> crfSegmenter = new CRFClassifier<>(flags);
    if(flags.loadClassifier == null) {
      throw new IllegalArgumentException("missing -loadClassifier flag for CRF preprocessor.");
    }
    crfSegmenter.loadClassifierNoExceptions(flags.loadClassifier, props);
    crfSegmenter.loadTagIndex();
    return crfSegmenter;
  }
  
  @Override
  public Sequence<IString> process(String input) {
    String[] outputSequence = segment(input);
    Sequence<IString> rv = IStrings.toIStringSequence(outputSequence);
    return rv;
  }

  @Override
  public SymmetricalWordAlignment processAndAlign(String input) {
    input = input.trim();
    String[] inputTokens = input.split("\\s+");
    IString[] outputSequence = IStrings.toIStringArray(segment(input));

    // Whitespace tokenization of input, create alignment
    Sequence<IString> inputSequence = IStrings.tokenize(input);
    assert inputSequence.size() == inputTokens.length;
    
    SymmetricalWordAlignment alignment = new SymmetricalWordAlignment(inputSequence, 
        new ArraySequence<IString>(true, outputSequence));

    // Generate the alignments
    StringBuilder inputToken = new StringBuilder();
    for (int i = 0, j = 0, limit = outputSequence.length; j < limit; ++j) {
      if (i >= inputTokens.length) {
        System.err.println("WARNING: Non-invertible input: " + input);
        break;
      }
      String inputTokenPart = outputSequence[j].toString();
      alignment.addAlign(i, j);

      inputToken.append(inputTokenPart);
      if (inputTokens[i].length() == inputToken.toString().length()) {
        ++i;
        inputToken = new StringBuilder();
      }
    }
    
    return alignment;
  }
  
  private String[] segment(String input) {
    List<List<CoreLabel>> crfOut = crfSegmenter.classify(input.trim());
    List<CoreLabel> outputTokens = new ArrayList<>();
    for(List<CoreLabel> doc : crfOut) outputTokens.addAll(doc);    
    return getSegmentedText(outputTokens, crfSegmenter);
  }
  
  abstract protected String[] getSegmentedText(List<CoreLabel> doc, CRFClassifier<CoreLabel> crfSegmenter);
  

  @Override
  public String toUncased(String input) {
    return input;
  }
}
