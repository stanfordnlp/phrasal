package edu.stanford.nlp.mt.metrics;


import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class IBMTokenizer {

   /* contraction list from UPenn's PTB sed script
      http://www.cis.upenn.edu/~treebank/tokenizer.sed
   */
   public static String tokenize(String s) {
     s = NISTTokenizer.tokenize(s);
     s = s.replaceAll("([^'])' ", "$1 ' ");
     s = s.replaceAll("(?i)'([smd])", " '$1");
     s = s.replaceAll("(?i)'ll", " 'll");
     s = s.replaceAll("(?i)'re", " 're");
     s = s.replaceAll("(?i)'ve", " 've");
     s = s.replaceAll("(?i)n't", " n't");
     s = s.replaceAll("\\s+", " ");
     return s;
   }

   public static void main(String args[]) throws IOException {
     BufferedReader b = new BufferedReader(
        new InputStreamReader(System.in));
     NISTTokenizer.lowercase(true);
     for (String line = b.readLine(); line != null; line = b.readLine()){
       System.out.println(tokenize(line));
     }
   }
}
