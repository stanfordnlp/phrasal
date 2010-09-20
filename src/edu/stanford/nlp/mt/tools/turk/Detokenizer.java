package edu.stanford.nlp.mt.tools.turk;

import edu.stanford.nlp.process.PTBTokenizer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

/**
 * Command line utility for detokenizing MT output.
 * 
 * @author Daniel Cer
 */
class Detokenizer {
  static public void main(String args[]) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    for (String line = reader.readLine(); line != null; line = reader
        .readLine()) {
      String detok = PTBTokenizer.ptb2Text(line);
      System.out.println(detok);
    }
  }
}
