package edu.stanford.nlp.mt.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import edu.stanford.nlp.io.IOUtils;

/**
 * Interactive (/commandline) interface to the linear time parser
 * 
 * @author daniel cer (http://dmcer.net) and Heeyoung Lee
 */
public class InteractiveLinearParser {
  static public void main(String[] args) throws IOException, ClassNotFoundException {
    if (args.length != 1) {
      System.out.println("Usage:\n\tjava ...InteractiveLinearParser (parsing model)");
      System.exit(-1);
    }
    String modelFile = args[0];
    DepDAGParser parser = IOUtils.readObjectFromFile(modelFile);
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    System.out.print("> ");
    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
      if ("</s>".equals(line)) {
        // finalize parse for the current prefix/sentence
      } else {
        // incrementally parse phrase give by line
        // in the context of the current prefix
        String[] toks = line.split("\\s+");
      }
      // System.out.printf("Current partial parse: %s\n", );
      // System.out.printf("Current stack contents: %s\n", );
      System.out.print("> ");
    }
   }
}
