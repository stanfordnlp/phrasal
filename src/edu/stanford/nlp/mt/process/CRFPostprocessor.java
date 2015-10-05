package edu.stanford.nlp.mt.process;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.OriginalTextAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;

/**
 * CRF-based text post-processor. The model depends on a companion Preprocessor
 * for training.
 * 
 * @author Spence Green
 *
 */
public class CRFPostprocessor implements Postprocessor, Serializable {

  private static final long serialVersionUID = -4149700323492795549L;
  
  protected transient CRFClassifier<CoreLabel> classifier;
  protected final SeqClassifierFlags flags;
  
  protected static String usage(String className) {
    String nl = System.getProperty("line.separator");
    StringBuilder sb = new StringBuilder();
    sb.append("Usage: java ").append(className).append(" OPTS < file").append(nl);
    sb.append(nl).append(" Options:").append(nl);
    sb.append("  -help                : Print this message.").append(nl);
    sb.append("  -orthoOptions str    : Comma-separated list of orthographic normalization options to pass to PTBTokenizer.").append(nl);
    sb.append("  -trainFile file      : Training file.").append(nl);
    sb.append("  -testFile  file      : Evaluation file.").append(nl);
    sb.append("  -textFile  file      : Raw input file to be postProcessed.").append(nl);
    sb.append("  -loadClassifier file : Load serialized classifier from file.").append(nl);
    sb.append("  -nthreads num        : Number of threads  (default: 1)").append(nl);
    sb.append(nl).append(" Otherwise, all flags correspond to those present in SeqClassifierFlags.java.").append(nl);
    return sb.toString();
  }

  protected static Map<String,Integer> optionArgDefs() {
    Map<String,Integer> optionArgDefs = new HashMap<>();
    optionArgDefs.put("help", 0);
    optionArgDefs.put("orthoOptions", 1);
    optionArgDefs.put("trainFile", 1);
    optionArgDefs.put("testFile", 1);
    optionArgDefs.put("textFile", 1);
    optionArgDefs.put("loadClassifier", 1);
    optionArgDefs.put("nthreads", 1);
    return optionArgDefs;
  }
  
  public CRFPostprocessor(Properties props) {
    // Currently, this class only supports one featureFactory.
    props.put("featureFactory", CRFPostprocessorFeatureFactory.class.getName());

    flags = new SeqClassifierFlags(props);
    classifier = new CRFClassifier<CoreLabel>(flags);
  }

  /**
   * Train a model given a preprocessor.
   * 
   * @param preProcessor
   */
  protected void train(Preprocessor preProcessor) {
    DocumentReaderAndWriter<CoreLabel> docReader = 
        new ProcessorTools.PostprocessorDocumentReaderAndWriter(preProcessor);
    ObjectBank<List<CoreLabel>> lines =
      classifier.makeObjectBankFromFile(flags.trainFile, docReader);

    classifier.train(lines, docReader);
    System.err.println("Finished training.");
  }
  
  /**
   * Serialize a model to file.
   * 
   * @param filename
   */
  protected void serialize(String filename) {
    classifier.serializeClassifier(filename);
  }

  /**
   * Load a serialized model.
   * 
   * @param filename
   * @param p
   * @throws FileNotFoundException
   */
  protected void load(String filename, Properties p) throws FileNotFoundException {
    File file = new File(filename);
    if ( ! file.exists()) throw new FileNotFoundException(filename);
    classifier = new CRFClassifier<CoreLabel>(p);
    try {
      classifier.loadClassifier(file, p);
    } catch (ClassCastException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
  }

  /**
   * Load a serialized model.
   * 
   * @param filename
   * @throws FileNotFoundException
   */
  protected void load(String filename) throws FileNotFoundException {
    Properties props = new Properties();
    // Currently, this class only supports one featureFactory.
    props.put("featureFactory", CRFPostprocessorFeatureFactory.class.getName());
    load(filename, props);
  }

  @Override
  public SymmetricalWordAlignment process(Sequence<IString> inputSequence) {
    List<CoreLabel> labeledTokens = ProcessorTools.toCharacterSequence(inputSequence);
    labeledTokens = classifier.classify(labeledTokens);
    List<CoreLabel> outputTokens = ProcessorTools.toPostProcessedSequence(labeledTokens);
    List<String> outputStrings = new ArrayList<>();
    for (CoreLabel label : outputTokens) {
      outputStrings.add(label.word());
    }
    Sequence<IString> outputSequence = IStrings.toIStringSequence(outputStrings);
    SymmetricalWordAlignment alignment = new SymmetricalWordAlignment(inputSequence, outputSequence);
    
    // Reconstruct the alignment by iterating over the target
    for (int outputIndex = 0, inputIndex = 0, outputSize = outputTokens.size(), inputSize = inputSequence.size(); 
        outputIndex < outputSize && inputIndex < inputSize; ++outputIndex) {
      String outputToken = outputTokens.get(outputIndex).get(OriginalTextAnnotation.class);
      String inputCandidate = inputSequence.get(inputIndex).toString();
      if (outputToken.equals(inputCandidate)) {
        // Unigram alignment
        alignment.addAlign(inputIndex, outputIndex);
        ++inputIndex;
        
      } else {
        // one-to-many alignment (output-to-input)
        String[] originalTokens = outputToken.split("\\s+");
        int newInputIndex = findSubsequenceStart(originalTokens, inputSequence, inputIndex);
        if (newInputIndex < 0) {
          System.err.printf("Unable to find |%s| in |%s|%n", outputToken, inputSequence.toString());
        } else {
          inputIndex = newInputIndex;
          for (int i = 0; i < originalTokens.length; ++i) {
            alignment.addAlign(inputIndex, outputIndex);
            ++inputIndex;
          }
        }
      }
    }
    return alignment;
  }
  
  /**
   * Brute-force substring matching in O(n*m) time.
   * 
   * @param subSequence
   * @param sequence
   * @param startIndex
   * @return
   */
  private static int findSubsequenceStart(String[] subSequence,
      Sequence<IString> sequence, int startIndex) {
    for (int i = startIndex, sequenceSize = sequence.size(); i < sequenceSize; ++i) {
      if (i + subSequence.length > sequenceSize) break;
      if (subSequence[0].equals(sequence.get(i).toString())) {
        if (subSequence.length == 1) return i;
        for (int j = 1; j < subSequence.length; ++j) {
          if (subSequence[j].equals(sequence.get(i+j).toString())) {
            if (j+1 == subSequence.length) {
              return i;
            }
          } else {
            break;
          }
        } 
      }
    }
    return -1;
  }

  /**
   * Evaluate the postprocessor given an input file specified in the flags.
   * 
   * @param preProcessor
   * @param pwOut
   */
  protected void evaluate(Preprocessor preProcessor, PrintWriter pwOut) {
    System.err.println("Starting evaluation...");
    DocumentReaderAndWriter<CoreLabel> docReader = new ProcessorTools.PostprocessorDocumentReaderAndWriter(preProcessor);
    ObjectBank<List<CoreLabel>> lines =
      classifier.makeObjectBankFromFile(flags.testFile, docReader);

    Counter<String> labelTotal = new ClassicCounter<String>();
    Counter<String> labelCorrect = new ClassicCounter<String>();
    int total = 0;
    int correct = 0;
    PrintWriter pw = new PrintWriter(IOTools.getWriterFromFile("apply.out"));
    for (List<CoreLabel> line : lines) {
      line = classifier.classify(line);
      pw.println(Sentence.listToString(ProcessorTools.toPostProcessedSequence(line)));
      total += line.size();
      for (CoreLabel label : line) {
        String hypothesis = label.get(CoreAnnotations.AnswerAnnotation.class);
        String reference = label.get(CoreAnnotations.GoldAnswerAnnotation.class);
        labelTotal.incrementCount(reference);
        if (hypothesis.equals(reference)) {
          correct++;
          labelCorrect.incrementCount(reference);
        }
      }
    }
    pw.close();

    double accuracy = ((double) correct) / ((double) total);
    accuracy *= 100.0;

    pwOut.println("EVALUATION RESULTS");
    pwOut.printf("#datums:\t%d%n", total);
    pwOut.printf("#correct:\t%d%n", correct);
    pwOut.printf("accuracy:\t%.2f%n", accuracy);
    pwOut.println("==================");

    // Output the per label accuracies
    pwOut.println("PER LABEL ACCURACIES");
    for (String refLabel : labelTotal.keySet()) {
      double nTotal = labelTotal.getCount(refLabel);
      double nCorrect = labelCorrect.getCount(refLabel);
      double acc = (nCorrect / nTotal) * 100.0;
      pwOut.printf(" %s\t%.2f%n", refLabel, acc);
    }
  }

  /**
   * Confgiure a post-processor, either by loading from a serialized file or training a new model.
   * 
   * @param postProcessor
   * @param preProcessor
   * @param options
   */
  protected static void setup(CRFPostprocessor postProcessor, Preprocessor preProcessor, Properties options) {
    if (postProcessor.flags.inputEncoding == null) {
      postProcessor.flags.inputEncoding = System.getProperty("file.encoding");
    }

    // Load or train the classifier
    if (postProcessor.flags.loadClassifier != null) {
      try {
        postProcessor.load(postProcessor.flags.loadClassifier, options);
      } catch (FileNotFoundException e) {
        throw new RuntimeException(e);
      }
    } else if (postProcessor.flags.trainFile != null){
      postProcessor.train(preProcessor);

      if(postProcessor.flags.serializeTo != null) {
        postProcessor.serialize(postProcessor.flags.serializeTo);
        System.err.println("Serialized segmenter to: " + postProcessor.flags.serializeTo);
      }
    } else {
      throw new RuntimeException("No training file or trained model specified!");
    }
  }

  /**
   * Decode raw text input.
   * 
   * @param postProcessor
   * @param reader
   * @param outstream
   * @param nThreads
   * @return
   */
  protected static double decode(final CRFPostprocessor postProcessor,
      BufferedReader reader, PrintWriter outstream, int nThreads) {
    long numChars = 0;
    int lineNumber = 0;
    long startTime = System.nanoTime();
    try {
      // Setup the threadpool
      MulticoreWrapper<String,String> wrapper = 
          new MulticoreWrapper<String,String>(nThreads, 
              new ThreadsafeProcessor<String,String>() {
                @Override
                public String process(String input) {
                  List<CoreLabel> labeledSeq = ProcessorTools.toCharacterSequence(input);
                  labeledSeq = postProcessor.classifier.classify(labeledSeq);
                  List<CoreLabel> tokenSeq = ProcessorTools.toPostProcessedSequence(labeledSeq);
                  return Sentence.listToString(tokenSeq);
                }
                @Override
                public ThreadsafeProcessor<String, String> newInstance() {
                  return this;
                }
      });
      
      // Read the input
      for (String line; (line = reader.readLine()) != null; ++lineNumber) {
        numChars += line.length();
        wrapper.put(line.trim());
        while(wrapper.peek()) outstream.println(wrapper.poll());
      }
      
      wrapper.join();
      while(wrapper.peek()) outstream.println(wrapper.poll());
      
    } catch (IOException e) {
      System.err.printf("%s: Error at input line %d%s", CRFPostprocessor.class.getName(), lineNumber);
      e.printStackTrace();
    }
    // Calculate throughput
    double elapsedTime = ((double) System.nanoTime() - startTime) / 1e9;
    double charsPerSecond = (double) numChars / elapsedTime;
    return charsPerSecond;
  }

  /**
   * Decode an evaluate file or raw input based on postProcessor.flags.
   * 
   * @param nThreads
   * @param preProcessor
   * @param postProcessor
   */
  protected static void execute(int nThreads, Preprocessor preProcessor,
      CRFPostprocessor postProcessor) {
    try {
      PrintWriter pwOut = new PrintWriter(System.out, true);
      if (postProcessor.flags.testFile != null) {
        postProcessor.evaluate(preProcessor, pwOut);

      } else {
        BufferedReader reader = postProcessor.flags.textFile == null ?
            new BufferedReader(new InputStreamReader(System.in)) :
              new BufferedReader(new InputStreamReader(new FileInputStream(postProcessor.flags.textFile),
                  postProcessor.flags.inputEncoding));

        double charsPerSec = CRFPostprocessor.decode(postProcessor, reader, pwOut, nThreads);
        reader.close();
        System.err.printf("Done! Processed input text at %.2f input characters/second%n", charsPerSec);
      }

    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch (FileNotFoundException e) {
      System.err.printf("%s: Could not open %s%n", CRFPostprocessor.class.getName(), postProcessor.flags.textFile);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}