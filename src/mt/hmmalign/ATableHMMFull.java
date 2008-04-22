package mt.hmmalign;

/**
 * The purpose of this class is to hold counts and alignment probabilities for
 * an HMM p(aj|aj-1,l) . In contrast to ATableHMM, here there are no common jump
 * parameters but rather we have seprate p(aj|aj-1,l). We also do the trick for p(0|i,l) which
 * is modelled as p(i+l|i,l) to remember the word after which the empty word followed
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */
import edu.stanford.nlp.util.ArrayN;

public class ATableHMMFull extends ATable {

  //we need arrays to keep the counts, the parameters
  //and finally the probabilities
  int shiftParams;
  float pEmpty;// this is the probability of going to the empty word
  float PROB_SMOOTH = (float) .0001;
  boolean fixEmpty = false; // we sometimes want to fiz the empty b/e it tends to grow too much (why is that ???)
  // if fixEmpty is true there will be the same pEmtpy probability for empty for all lengths
  boolean smoothUniform = false;
  float lambda = (float) .2;
  private ArrayN prob_arr; //prob_arr will be formed by normalization from count_arr
  private ArrayN count_arr;// keep the counts here

  public ATableHMMFull(int maxsize) {
    MAX_LENGTH = maxsize;
    count_arr = new ArrayN(MAX_LENGTH + 1, MAX_LENGTH + 1, MAX_LENGTH + 1);
    prob_arr = new ArrayN(MAX_LENGTH + 1, MAX_LENGTH + 1, MAX_LENGTH + 1);


  }


  /**
   * Get the initial probability p(i|l) i is in 0..l , l is the length of the english sentence
   */

  public float getInitialProb(int index, int l) {
    return prob_arr.get(index, 0, l);
  }


  public float getEmpty() {
    return pEmpty;
  }

  public boolean isPopulated() {
    return count > countCutoff;
  }


  /**
   * Get the probability p(i|i_prev,l) i is from 1 to 2L and i_prev is in the same set as well
   */
  public float getProbHMM(int i, int i_prev, int l) {
    boolean empty = false;

    if ((i == 0) && (i_prev > 0)) {
      return 0;
    }
    if (i_prev == 0) {
      if (i > l) {
        return 0;
      } else {
        return getInitialProb(i, l);
      }
    }

    if (i_prev > l) {
      i_prev -= l;
    }

    if (i > l) {
      i -= l;
      empty = true;
    }

    if (empty) {
      if (i != i_prev) {
        return 0;
      } else {
        return prob_arr.get(0, i_prev, l);
      }
    } else {
      return prob_arr.get(i, i_prev, l);

    }


  }


  public float getProb(int i, int i_prev, int l) {
    float prob;
    prob = getProbHMM(i, i_prev, l);
    if (prob == 0) {
      return prob;
    }
    if (this.smoothUniform) {
      return (1 - lambda) * prob + lambda * 1 / (float) l;

    } else {
      return prob;
    }


  }


  /**
   * Increment the corresponding counts
   */
  public void incCount(int i, int i_prev, int l, double val1) {

    float val = (float) val1;
    if (i_prev > l) {
      i_prev -= l;
    }

    if ((i_prev == 0)) {
      count_arr.inc(val, i, i_prev, l);
    } else {
      if ((i > l)) {
        count_arr.inc(val, 0, i_prev, l);
      } else {
        count_arr.inc(val, i, i_prev, l);
      }
    }


  }


  /**
   * Calculate normalized initial parameters
   * No reason really why it should be separate from normalizeProbArr except for the
   * empty parameters
   */

  public void normalizeInitialProbs() {
    float total = 0, val;
    for (int l = 1; l <= MAX_LENGTH; l++) { //for each length size
      total = 0;
      for (int i = 0; i <= l; i++) { //for each initial position
        total += count_arr.get(i, 0, l) + PROB_SMOOTH;
      }
      //now normalzie
      for (int i = 0; i <= l; i++) { //for each initial position
        val = (count_arr.get(i, 0, l) + PROB_SMOOTH) / total;
        prob_arr.set(val, i, 0, l);
      }

    }//l

  }


  /**
   * Before starting a new iteration the counts should be zero-ed
   */

  public void zeroCounts() {
    count_arr.setZero();
  }


  /**
   * Normalize the transition table prob_arr and put the appropriate probabilities there
   */

  public void normalizeProbArr() {
    int index = 0;
    float total = 0, mult = 1, val;
    if (fixEmpty) {
      index = 1;
      mult = 1 - pEmpty;
    }
    for (int l = 1; l <= MAX_LENGTH; l++) { //for each possible length

      for (int i_p = 1; i_p <= l; i_p++) { //for each previous position
        total = 0;
        for (int i = index; i <= l; i++) {
          total += count_arr.get(i, i_p, l) + PROB_SMOOTH;
        }
        //normalize
        for (int i = index; i <= l; i++) {
          val = (count_arr.get(i, i_p, l) + PROB_SMOOTH) / total;
          prob_arr.set(val * mult, i, i_p, l);
        }
        if (fixEmpty) {
          prob_arr.set(pEmpty, 0, i_p, l);
        }
        ;

      }//i_p

    }//l

  }


  /**
   * This does the normalization of the component distributions
   */
  public void normalize() {
    normalizeProbArr();
    normalizeInitialProbs();

    if (GlobalParams.verbose) {
      this.printProbs();
    }
    zeroCounts(); //prepare for the next eStep

  }


  /**
   * Initialize the probabilities in a brain dead manner uniformly
   */

  public void initializeUniform() {
    // first the initial probabilities

    float inc_init, inc;
    for (int l = 1; l <= MAX_LENGTH; l++) {
      for (int i = 0; i <= MAX_LENGTH; i++) {
        count_arr.set(1, i, 0, l);

      }//i

    }//l

    pEmpty = 1 / (float) (MAX_LENGTH + 1);

    for (int l = 1; l <= MAX_LENGTH; l++) {
      for (int i_p = 1; i_p <= l; i_p++) {
        for (int i = 0; i <= l; i++) {
          count_arr.set(1, i, i_p, l);
        }//i
      }//i_p

    }//l

    normalize();


  }



  /*
 * Copy all the values of a
 */

  public void initialize(ATable a) {

    for (int l = 1; l <= MAX_LENGTH; l++) {

      for (int i_p = 0; i_p <= l; i_p++) {
        for (int i = 0; i <= MAX_LENGTH; i++) {

          int i1 = i;
          if ((i == 0) && (i_p > 0)) {
            i1 = i_p + l;
          }
          this.prob_arr.set(a.getProb(i1, i_p, l), i, i_p, l);

        }


      }
    }
    pEmpty = a.getEmpty();


  }


  public boolean checkOK() {

    boolean ok = true;
    for (int len = 1; len <= MAX_LENGTH; len++) {
      ok = checkOK(len);
      if (!ok) {
        return ok;
      }
    }
    return ok;
  }


  /**
   * Check OK for a specific length len
   */


  public boolean checkOK(int len) {
    float total = 0;
    //check initial
    for (int i = 0; i <= len; i++) {
      total += getInitialProb(i, len);
    }
    if (Mabs(total - 1) > .001) {
      return false;
    }

    for (int i_prev = 1; i_prev <= 2 * len; i_prev++) {
      total = 0;
      for (int i = 1; i <= 2 * len; i++) {
        total += getProb(i, i_prev, len);
      }
      if (Mabs(total - 1) > .001) {
        return false;
      }

    }

    return true;


  }


  /**
   * Some code to test the class;
   */

  public static void main(String[] args) {
    boolean ok = true;
    ATableHMMFull a = new ATableHMMFull(2);
    a.initializeUniform();
    for (int l = 1; l <= a.MAX_LENGTH; l++) {
      ok = ok && a.checkOK(l);
    }
    a.printProbs();
    System.out.println(" Ok is " + ok);
    /*

    a.incCount(0,1);
    a.incCount(-1,1);
    a.incCount(1,1);
    a.incCountInitPos(1,3);
    a.incCountInitPos(2,1);
    a.incEmpty(2);
    a.normalize();
    ok=a.checkOK(1);
    if(ok){ok=a.checkOK(2);}
    System.out.println(" Ok is "+ok);
    a.printProbs();
    */


  }


  public void printProbs() {

    //print the initial probabilities

    int i_prev = 0;
    for (int i = 0; i <= MAX_LENGTH; i++) {
      System.out.println("P(" + i + "|" + i_prev + "," + MAX_LENGTH + ")" + getProb(i, i_prev, MAX_LENGTH));

    }

    for (int l = 1; l <= MAX_LENGTH; l++) {
      for (i_prev = 1; i_prev <= l; i_prev++) {
        for (int i = 1; i <= 2 * l; i++) {
          System.out.println("P(" + i + "|" + i_prev + "," + l + ")" + getProb(i, i_prev, l));

        }
        System.out.println("*************************");


      }
      System.out.println("*************************");

    }


  }


  public float Mabs(float x) {
    if (x < 0) {
      x = -x;
    }
    return x;
  }


}
