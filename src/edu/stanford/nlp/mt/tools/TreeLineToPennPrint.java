package edu.stanford.nlp.mt.tools;
import edu.stanford.nlp.trees.Tree;

import java.io.*;

public class TreeLineToPennPrint { //throws Exception {
  public static void main(String args[]) throws Exception {
  BufferedReader in
    = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));

    String s;
    while((s = in.readLine()) != null) {
      Tree t = Tree.valueOf(s);
      t.pennPrint();
      System.out.println("----------------------------------");
    }
  }
}
