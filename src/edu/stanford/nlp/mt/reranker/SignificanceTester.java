package edu.stanford.nlp.mt.reranker;

import edu.stanford.nlp.util.StringUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Given two Hiero/Moses system (1-best) output files, performs a
 * statistical significance test to determine whether the system
 * outputs are different or not. Currently only works with Bleu.
 **/
public class SignificanceTester {
  public static final int NUM_ITERS = 5000;
  public static final double GOOD_P_THRESH = 0.05;
  public static final double MAYBE_P_THRESH = 0.1;

  public static void main(String[] origArgs) throws IOException {

    Map<String, Integer> flagsToNumArgs = new HashMap<String, Integer>();
    flagsToNumArgs.put("StatsFactory", 1);
    flagsToNumArgs.put("Scorer", 1);
    Properties prop = StringUtils.argsToProperties(origArgs, flagsToNumArgs);
    String[] args = prop.getProperty("").split(" ");
    StatsFactory sf;
    String scorerName=null;
    if (prop.containsKey("StatsFactory")) {
      try {
        sf = (StatsFactory)Class.forName(prop.getProperty("StatsFactory")).newInstance();
      } catch (Exception e) {
        e.printStackTrace();
        throw new RuntimeException("Exception");
      }
    } else {
      sf = new SegStatsFactory();
    }
    if (prop.containsKey("Scorer")) {
      scorerName = prop.getProperty("Scorer");//(Scorer)Class.forName(prop.getProperty("Scorer")).newInstance();
    }

    if(args.length < 3) throw new RuntimeException("expecting two system output filenames, and one or more reference filenames");

    System.out.println("System 1 output: " + args[0]);
    System.out.println("System 2 output: " + args[1]);
    for(int i = 0; i < args.length - 2; i++) System.out.println("Reference " + i + ": " + args[i + 2]);

    BufferedReader[] rin = new BufferedReader[args.length - 2];

    System.err.println("Reading system 1 output...");
    for(int i = 0; i < rin.length; i++) rin[i] = new BufferedReader(new FileReader(args[i + 2]));
    Stats[] system1 = BleuFlatFileReader.read(new BufferedReader(new FileReader(args[0])), rin, sf);

    System.err.println("Reading system 2 output...");
    for(int i = 0; i < rin.length; i++) rin[i] = new BufferedReader(new FileReader(args[i + 2]));
    Stats[] system2 = BleuFlatFileReader.read(new BufferedReader(new FileReader(args[1])), rin, sf);

    if(system1.length != system2.length) throw new RuntimeException("Two system output corpora should be of the same length, because they're supposed to be the same output, dummy!");

    Scorer bleu1;
    if (scorerName!=null) {
      try {
        bleu1 = (Scorer)(Class.forName(scorerName).newInstance());
      } catch(Exception e) {
        e.printStackTrace();
        throw new RuntimeException("exception");
      }
    } else {
      bleu1 = new Bleu();
    }
    for(int i = 0; i < system1.length; i++) bleu1.add(system1[i]);
    double s1score = bleu1.score();
    System.out.printf("System 1 score: %.2f\n", s1score * 100.0);

    Scorer bleu2;
    if (scorerName!=null) {
      try {
        bleu2 = (Scorer)(Class.forName(scorerName).newInstance());
      } catch(Exception e) {
        e.printStackTrace();
        throw new RuntimeException("exception");
      }
    } else {
      bleu2 = new Bleu();
    }
    for(int i = 0; i < system2.length; i++) bleu2.add(system2[i]);
    double s2score = bleu2.score();
    System.out.printf("System 2 score: %.2f\n", s2score * 100.0);

    double diff = Math.abs(s1score - s2score);
    System.out.printf("Observed difference: %.2f\n", diff * 100.0);

    System.err.println("Sampling...");
    java.util.Random r = new java.util.Random();

    double num_gte = 0;
    for(int i = 0; i < NUM_ITERS; i++) {
      bleu1.reset(); bleu2.reset();
      for(int j = 0; j < system1.length; j++) {
	if(r.nextDouble() < 0.5) {
	  bleu1.add(system1[j]);
	  bleu2.add(system2[j]);
	}
	else { /* swap */
	  bleu1.add(system2[j]);
	  bleu2.add(system1[j]);
	}
      }

      if(Math.abs(bleu1.score() - bleu2.score()) >= diff) num_gte++;
    }
    double p = num_gte / NUM_ITERS;

    System.out.println("=============================================================================");

    System.out.printf("Out of %d samples, %d had a difference greater than or equal to the\n" +
                      "observed difference of %.2f. Your p-value is thus p=%.3f. This means that\n" +
		      "if the two systems were actually the same, then there would be a %.1f%%\n" +
		      "chance of finding a difference in BLEU greater than or equal to the\n" +
		      "observed difference of %.2f by sheer chance.\n\n", NUM_ITERS, (int)num_gte, diff * 100.0, p, p * 100.0, diff * 100.0);

    if(p < GOOD_P_THRESH) System.out.printf("Since p < %.2f, you may safely claim that the difference between the two\n" +
                                            "systems is statistically significant.\n", GOOD_P_THRESH);
    else if(p < MAYBE_P_THRESH) System.out.printf("Since p >= %.2f, you MAY NOT claim that the difference between the two\n" +
						"system is statistically significant. However, since the p value is still\n" +
						"quite low, it is possible that with more data, you may be able to show a\n" +
						"significant difference.\n", GOOD_P_THRESH);
    else System.out.printf("Since p >= %.2f, you MAY NOT claim that the difference between the two\n" +
			   "system is statistically significant. In fact, since p is quite large\n" +
			   "(>= %.2f), it is likely that the two systems are simply not significantly\n" +
			   "different under the given metric.", GOOD_P_THRESH, MAYBE_P_THRESH);

    System.out.printf("\nNote that repeatedly running this test over and over until you finally\n" +
		      "find a significant difference may constitute \"fishing\". To be statistically\n" +
		      "honest, you should run this test as infrequently as possible. If it is\n" +
		      "necessary to perform many comparisons, you may need to perform a \"correction\"\n" +
		      "to reduce your p-value threshold. See\n" + 
		      "  http://nlp.stanford.edu/local/talks/sigtest.pdf\n" +
		      "for details.\n");
    System.out.println("=============================================================================");
  }
}
