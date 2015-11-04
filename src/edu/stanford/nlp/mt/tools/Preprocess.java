package edu.stanford.nlp.mt.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import edu.stanford.nlp.mt.process.Preprocessor;
import edu.stanford.nlp.mt.process.ProcessorFactory;

/**
 * Preprocess input from stdin.
 * 
 * @author Spence Green
 *
 */
public final class Preprocess {

  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.printf("Usage: java %s lang_code < input%n", Preprocess.class.getName());
      System.exit(-1);
    }
    try(BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
      Preprocessor preproc = ProcessorFactory.getPreprocessor(args[0]);
      for (String line; (line = reader.readLine()) != null;) {
        System.out.println(preproc.process(line).toString());
      }

    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
