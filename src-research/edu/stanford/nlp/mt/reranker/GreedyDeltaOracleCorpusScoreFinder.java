package edu.stanford.nlp.mt.reranker;

import edu.stanford.nlp.util.ArrayUtils;
import edu.stanford.nlp.util.StringUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Given a Hiero/Moses nbest output file and a set of of reference filenames as
 * arguments, runs a greedy search for the global corpus maximum score (BLEU or
 * negative TER), and outputs a feature file (in GALE format) which gives the
 * negative delta corpus bleu (or negTER) for each hypothesis. If this number is
 * epsilon close to zero, then the hypothesis should be treated as good.
 * 
 * @author William Morgan
 **/

public class GreedyDeltaOracleCorpusScoreFinder {
  public static final int NUM_ITERS = 10;
  public static final double EPSILON = 0.0001;
  public StatsFactory sf = new SegStatsFactory();
  public Class<? extends Scorer> scorerClass = Bleu.class;

  public GreedyDeltaOracleCorpusScoreFinder() {
    this("bleu");
  }

  public GreedyDeltaOracleCorpusScoreFinder(String scoring) {
    if (scoring.equalsIgnoreCase("bleu")) {
      System.err.println("GreedyDeltaOracleCorpusScoreFinder using Scorer "
          + scoring);
      scorerClass = Bleu.class;
      sf = new SegStatsFactory();
    } else if (scoring.equalsIgnoreCase("TER")) {
      System.err.println("GreedyDeltaOracleCorpusScoreFinder using Scorer "
          + scoring);
      scorerClass = TER.class;
      sf = new EditStatsFactory();
    } else {
      System.err.println("GreedyDeltaOracleCorpusScoreFinder using Scorer "
          + scoring);
      throw new RuntimeException();
    }
  }

  public static class ReturnValue {
    public int[] hyps;
    public double score;

    public ReturnValue(int[] hyps, double score) {
      this.hyps = hyps;
      this.score = score;
    }
  }

  Stats[][] read(String nBestFilename, String[] refFilenames)
      throws IOException {
    BufferedReader nbin = new BufferedReader(new FileReader(nBestFilename));
    BufferedReader[] rin = new BufferedReader[refFilenames.length];
    for (int i = 0; i < rin.length; i++)
      rin[i] = new BufferedReader(new FileReader(refFilenames[i]));
    return read(nbin, rin);
  }

  Stats[][] read(BufferedReader nbin, BufferedReader[] rin) throws IOException {
    ArrayList<Stats[]> corpus = new ArrayList<Stats[]>();
    ArrayList<Stats> sentence = null;
    String sentID = null;
    String[] refs = null;

    System.err.print("Reading sentences: ");
    while (true) {
      String l = nbin.readLine();
      if (l == null)
        break;

      String[] fields = l.split("\\s*\\|\\|\\|\\s*");
      String words = fields[1];

      if ((sentID == null) || !sentID.equals(fields[0])) {
        if (sentence != null) {
          System.err.print("(" + sentence.size() + ") ");
          corpus.add(sentence.toArray(new Stats[0]));
        }
        sentence = new ArrayList<Stats>();
        sentID = fields[0];
        System.err.print(sentID + " ");
        refs = new String[rin.length];
        for (int i = 0; i < rin.length; i++) {
          String ll = rin[i].readLine();
          refs[i] = ll;
        }
      }
      sentence.add(sf.newStats(words, refs));
    }

    if (sentence != null) {
      corpus.add(sentence.toArray(new Stats[0]));
      System.err.print("(" + sentence.size() + ") ");
    }

    System.err.println();
    return corpus.toArray(new Stats[0][]);
  }

  /* does random initialization for you */
  public ReturnValue findGlobalOptimum(Stats[][] hypotheses) {
    java.util.Random r = new java.util.Random();
    int starts[] = new int[hypotheses.length];

    for (int i = 0; i < starts.length; i++)
      starts[i] = r.nextInt(hypotheses[i].length);

    return findGlobalOptimum(starts, hypotheses);
  }

  public ReturnValue findGlobalOptimum(int[] starts, Stats[][] hypotheses) {
    Scorer b;
    try {
      b = (scorerClass.newInstance());
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("Can't get new instance of " + scorerClass);
    }

    int[] hyps = ArrayUtils.copy(starts);

    for (int i = 0; i < hypotheses.length; i++) {
      b.add(hypotheses[i][hyps[i]]);
    }
    double score = b.score();
    double lastScore = score - 100;
    // System.err.printf("Starting hypotheses yield a BLEU score of %.2f\n",
    // score * 100.0);

    int numIters = 0;
    while (score - lastScore > EPSILON) {
      lastScore = score;
      numIters++;

      for (int i = 0; i < hypotheses.length; i++) {
        double best = b.score();
        int bestHyp = hyps[i];

        b.sub(hypotheses[i][hyps[i]]);
        for (int j = 0; j < hypotheses[i].length; j++) {
          b.add(hypotheses[i][j]);
          double thisScore = b.score();
          b.sub(hypotheses[i][j]);

          if (thisScore > best) {
            best = thisScore;
            bestHyp = j;
          }
        }
        // System.err.printf("%d: %d -> %d (%.2f -> %.2f)\n", i, hyps[i],
        // bestHyp, oldScore, best);
        hyps[i] = bestHyp;
        b.add(hypotheses[i][hyps[i]]);
      }

      score = b.score();
      System.err.printf("Loop %d: score: %.2f -> %.2f\n", numIters,
          lastScore * 100.0, score * 100.0);
    }

    return new ReturnValue(hyps, score);
  }

  public static void main(String[] origArgs) throws IOException {
    Map<String, Integer> flagsToNumArgs = new HashMap<String, Integer>();
    flagsToNumArgs.put("scoringMetric", 1);
    Properties p = StringUtils.argsToProperties(origArgs, flagsToNumArgs);
    String[] args = p.getProperty("").split(" ");

    String scorerName = p.getProperty("scoringMetric");
    GreedyDeltaOracleCorpusScoreFinder finder;
    if (scorerName != null) {
      System.err.println("Using Scorer " + scorerName);
      finder = new GreedyDeltaOracleCorpusScoreFinder(scorerName);
    } else {
      finder = new GreedyDeltaOracleCorpusScoreFinder();
    }

    if (args.length == 0)
      throw new RuntimeException("expecting a list of reference filenames");

    BufferedReader[] rin = new BufferedReader[args.length];
    for (int i = 0; i < rin.length; i++)
      rin[i] = new BufferedReader(new FileReader(args[i]));

    Stats[][] nbest = finder.read(new BufferedReader(new InputStreamReader(
        System.in)), rin);
    // Stats[][] nbest = finder.read(new BufferedReader(new
    // FileReader("test/nbest")), rin);

    double oracleScore = Double.NEGATIVE_INFINITY;
    int[] oracleHyps = null;
    for (int iter = 0; iter < NUM_ITERS; iter++) {
      System.err.println("Iteration " + iter + " of " + NUM_ITERS);
      ReturnValue rv = finder.findGlobalOptimum(nbest);
      if (rv.score > oracleScore) {
        oracleScore = rv.score;
        oracleHyps = rv.hyps;
      }
    }
    Scorer scorer;
    try {
      scorer = (finder.scorerClass.newInstance());
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("Can't get new instance of "
          + finder.scorerClass);
    }

    for (int i = 0; i < nbest.length; i++)
      scorer.add(nbest[i][oracleHyps[i]]);

    System.out.printf("## oracle score is %.2f\n", oracleScore * 100.0);

    for (int i = 0; i < nbest.length; i++) {
      scorer.sub(nbest[i][oracleHyps[i]]);
      for (int j = 0; j < nbest[i].length; j++) {
        scorer.add(nbest[i][j]);
        double ndcb = scorer.score() - oracleScore;
        System.out.printf("%d,%d %.4f\n", i, j, ndcb * 100.0);
        scorer.sub(nbest[i][j]);
      }
      scorer.add(nbest[i][oracleHyps[i]]);
    }
  }
}
