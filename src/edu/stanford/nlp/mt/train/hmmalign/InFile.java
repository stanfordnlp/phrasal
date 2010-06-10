package edu.stanford.nlp.mt.train.hmmalign;


/* This serves for input from files. Copied from Bruce Eckel
 *@author Kristina Toutanova (kristina@cs.stanford.edu)
 */

import java.io.*;

public class InFile extends BufferedReader {
  public InFile(String filename) throws FileNotFoundException {
    super(new InputStreamReader(new FileInputStream(filename)));
  }

  public InFile(File file) throws FileNotFoundException {
    this(file.getPath());
  }
}
