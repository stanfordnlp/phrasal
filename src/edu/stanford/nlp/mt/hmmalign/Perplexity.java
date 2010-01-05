package mt.hmmalign;


/* A class to help calculate the preplexity
 * of a corpus
 * Keeps a pre-computed table of the exponential distribution
 * p(m|l)=lambda^m*e^-lambda / m!
 *@author Kristina Toutanova (kristina@cs.stanford.edu)
 */

public class Perplexity {
  private static byte MAX_LENGTH = 41;
  static double[][] probs = new double[MAX_LENGTH][MAX_LENGTH];
  static double LAMBDA = 1.09;
  static boolean verbose = false;

  public Perplexity() {
    initTable();
  }

  public static double getProb(int m, int l) {
    return probs[m][l];
  }


  public void initTable() {


    for (int l = 0; l < MAX_LENGTH; l++) {
      probs[0][l] = -LAMBDA * l;
    }

    for (int m = 1; m < MAX_LENGTH; m++) {

      for (int l = 1; l < MAX_LENGTH; l++) {
        probs[m][l] = probs[m - 1][l] + Math.log(LAMBDA * l) - Math.log(m);
      }
    }

    if (verbose) {

      for (int l = 0; l < MAX_LENGTH; l++) {
        for (int m = 0; m < MAX_LENGTH; m++) {
          System.out.println("Probability " + m + "|" + l + " " + probs[m][l]);
        }
      }
    }

  }


}
