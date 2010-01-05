package edu.stanford.nlp.mt.reranker;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * Acts like multi-bleu.perl: Given an input file on stdin and a set
 * of reference filenames as arguments, calculates the BLEU score of
 * the input file against the references, interpreting each line as a
 * sentence and each whitespace-separated item as a word.
 *
 **/
public class BleuFlatFileReader {
  static Stats[] read(String corpusFilename, String[] refFilenames) throws IOException {
    BufferedReader cin = new BufferedReader(new FileReader(corpusFilename));
    BufferedReader[] rin = new BufferedReader[refFilenames.length];
    for(int i = 0; i < rin.length; i++) rin[i] = new BufferedReader(new FileReader(refFilenames[i]));
    return read(cin, rin);
  }

  static Stats[] read(BufferedReader cin, BufferedReader[] rin) throws IOException {
    return read(cin, rin, new SegStatsFactory());
  }

  static Stats[] read(BufferedReader cin, BufferedReader[] rin, StatsFactory sf) throws IOException {
    ArrayList<Stats> corpus = new ArrayList<Stats>();

    while(true) {
      String l = cin.readLine();
      if(l == null) break;

      String s = l;
      String[] r = new String[rin.length];
      for(int i = 0; i < rin.length; i++) {
      	String ll = rin[i].readLine();
      	r[i] = ll;
      }

      corpus.add(sf.newStats(s, r));
    }

    return corpus.toArray(new Stats[0]);
  }




  public static void main(String[] args) throws IOException {
    if(args.length == 0) throw new RuntimeException("expecting a list of reference filenames");

    BufferedReader[] rin = new BufferedReader[args.length];
    for(int i = 0; i < rin.length; i++) rin[i] = new BufferedReader(new FileReader(args[i]));

    Stats[] corpus = BleuFlatFileReader.read(new BufferedReader(new InputStreamReader(System.in)), rin);
    Bleu bleu = new Bleu();
    for(int i = 0; i < corpus.length; i++) bleu.add(corpus[i]);

    double ngramScores[] = bleu.rawNGramScores();
    System.out.printf("BLEU = %.2f, %.1f/%.1f/%.1f/%.1f (BP=%.3f)\n", bleu.score() * 100.0, Math.exp(ngramScores[0]) * 100.0,
		      Math.exp(ngramScores[1]) * 100.0, Math.exp(ngramScores[2]) * 100.0, Math.exp(ngramScores[3]) * 100.0,
		      bleu.BP());
  }
}
