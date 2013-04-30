package edu.stanford.nlp.mt.metrics;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * NIST tokenization according to mtevalv11 specs. Taken from
 * edu.stanford.nlp.mt.reranker.ter.
 * 
 * @author Michel Galley
 */
public class NISTTokenizer {

  private static boolean normalized = true;
  private static boolean lowercase = true;
  private static boolean nopunct = false;

  private NISTTokenizer() {
  }

  public static void normalize(boolean n) {
    normalized = n;
  }

  public static void lowercase(boolean l) {
    lowercase = l;
  }

  public static void ignorePunct(boolean n) {
    nopunct = n;
  }

  public static String tokenize(String s) {
    // System.err.println("tokenizer input: "+s);
    if (lowercase)
      s = s.toLowerCase();
    if (normalized) {
      // language-independent part:
      s = s.replaceAll("<skipped>", ""); // strip "skipped" tags
      s = s.replaceAll("-\n", ""); // strip end-of-line hyphenation and join
                                   // lines
      s = s.replaceAll("\n", " "); // join lines
      s = s.replaceAll("&quot;", "\""); // convert SGML tag for quote to "
      s = s.replaceAll("&amp;", "&"); // convert SGML tag for ampersand to &
      s = s.replaceAll("&lt;", "<"); // convert SGML tag for less-than to >
      s = s.replaceAll("&gt;", ">"); // convert SGML tag for greater-than to <

      // language-dependent part (assuming Western languages):
      s = " " + s + " ";
      s = s.replaceAll("([\\{-\\~\\[-\\` -\\&\\(-\\+\\:-\\@\\/])", " $1 "); // tokenize
                                                                            // punctuation
      s = s.replaceAll("([^0-9])([\\.,])", "$1 $2 "); // tokenize period and
                                                      // comma unless preceded
                                                      // by a digit
      s = s.replaceAll("([\\.,])([^0-9])", " $1 $2"); // tokenize period and
                                                      // comma unless followed
                                                      // by a digit
      s = s.replaceAll("([0-9])(-)", "$1 $2 "); // tokenize dash when preceded
                                                // by a digit
      s = s.replaceAll("\\s+", " "); // one space only between words
      s = s.replaceAll("^\\s+", ""); // no leading space
      s = s.replaceAll("\\s+$", ""); // no trailing space
    }
    if (nopunct)
      s = removePunctuation(s);
    // System.err.println("tokenizer output: "+s);
    return s;
  }

  private static String removePunctuation(String str) {
    String s = str.replaceAll("[\\.,\\?:;!\"\\(\\)]", "");
    s = s.replaceAll("\\s+", " ");
    return s;
  } 

  public static void main(String args[]) throws IOException {
    BufferedReader b = new BufferedReader(new InputStreamReader(System.in));
    for (String line = b.readLine(); line != null; line = b.readLine()){
      System.out.println(tokenize(line));
    } 
  }
}
