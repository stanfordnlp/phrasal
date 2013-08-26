package edu.stanford.nlp.mt.process;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

public class CRFPostprocessor implements Postprocessor, Serializable {

  private static final long serialVersionUID = -4149700323492795549L;
  
  protected transient CRFClassifier<CoreLabel> classifier;
  protected final SeqClassifierFlags flags;
  
  public CRFPostprocessor(Properties props) {
    // Currently, this class only supports one featureFactory.
    props.put("featureFactory", CRFPostprocessorFeatureFactory.class.getName());

    flags = new SeqClassifierFlags(props);
    classifier = new CRFClassifier<CoreLabel>(flags);
  }

  protected void train(Preprocessor preProcessor) {
    DocumentReaderAndWriter<CoreLabel> docReader = 
        new ProcessorTools.PostprocessorDocumentReaderAndWriter(preProcessor);
    ObjectBank<List<CoreLabel>> lines =
      classifier.makeObjectBankFromFile(flags.trainFile, docReader);

    classifier.train(lines, docReader);
    System.err.println("Finished training.");
  }
  
  protected void serialize(String filename) {
    classifier.serializeClassifier(filename);
  }

  protected void load(String filename, Properties p) {
    classifier = new CRFClassifier<CoreLabel>(p);
    try {
      classifier.loadClassifier(new File(filename), p);
    } catch (ClassCastException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
  }

  protected void load(String filename) {
    load(filename, new Properties());
  }

  @Override
  public SymmetricalWordAlignment process(Sequence<IString> input) {
    // TODO(spenceg)
    return null;
  }
  
  protected void evaluate(Preprocessor preProcessor, PrintWriter pwOut) {
    System.err.println("Starting evaluation...");
    DocumentReaderAndWriter<CoreLabel> docReader = new ProcessorTools.PostprocessorDocumentReaderAndWriter(preProcessor);
    ObjectBank<List<CoreLabel>> lines =
      classifier.makeObjectBankFromFile(flags.testFile, docReader);

    Counter<String> labelTotal = new ClassicCounter<String>();
    Counter<String> labelCorrect = new ClassicCounter<String>();
    int total = 0;
    int correct = 0;
//    PrintStream pw = IOTools.getWriterFromFile("eval.out");
    for (List<CoreLabel> line : lines) {
      line = classifier.classify(line);
//      pw.append(Sentence.listToString(ProcessorTools.toPostProcessedSequence(line))).append("\n");
      for (CoreLabel label : line) {
        // Do not evaluate labeling of whitespace
        String observation = label.get(CoreAnnotations.CharAnnotation.class);
        if ( ! observation.equals(ProcessorTools.WHITESPACE)) {
          total++;
          String hypothesis = label.get(CoreAnnotations.AnswerAnnotation.class);
          String reference = label.get(CoreAnnotations.GoldAnswerAnnotation.class);
          labelTotal.incrementCount(reference);
          if (hypothesis.equals(reference)) {
            correct++;
            labelCorrect.incrementCount(reference);
          }
        }
      }
    }
//    pw.close();

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

  
  protected static void setup(CRFPostprocessor postProcessor, Preprocessor preProcessor, Properties options) {
    if (postProcessor.flags.inputEncoding == null) {
      postProcessor.flags.inputEncoding = System.getProperty("file.encoding");
    }

    // Load or train the classifier
    if (postProcessor.flags.loadClassifier != null) {
      postProcessor.load(postProcessor.flags.loadClassifier, options);
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

  protected static double decode(CRFPostprocessor postProcessor,
      BufferedReader br, PrintWriter pwOut, int nThreads) {
    // TODO Auto-generated method stub
    return 0;
  }
  

  protected static void execute(int nThreads, Preprocessor preProcessor,
      CRFPostprocessor postProcessor) {
    // Decode either an evaluation file or raw text
    try {
      PrintWriter pwOut = new PrintWriter(System.out, true);
      if (postProcessor.flags.testFile != null) {
        if (postProcessor.flags.answerFile == null) {
          postProcessor.evaluate(preProcessor, pwOut);
        }

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
