package mt.hmmalign;

/**
 * The purpose of this class is to hold counts and alignment probabilities for
 * an HMM p(aj|aj-1,l) .These are estimated by having a set of parameters p(delta) for
 * the probability of each jump and the probability of going to the empty english word
 * Additional states for the empty word are added to encode the position of the previous non-empty word
 * There are also parameters p(a1|0) that play the role of initial parameters for the HMM
 *@author Kristina Toutanova (kristina@cs.stanford.edu)
 */

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.StringTokenizer;
import edu.stanford.nlp.util.ArrayN;

public class ATableHMM extends ATable {

  //we need arrays to keep the counts, the parameters
  //and finally the probabilities
  float[] params; //p0 is the probability of empty, p1 is p(-MAX_LENGTH+1) , etc . to p(MAX_LENGTH-1)
  float[] counts;// keep here the counts rather than in params
  float[] initialCounts;
  int shiftParams;
  float pEmpty;// this is the probability of going to the empty word
  float PROB_SMOOTH = (float) 1.0;
  float PROB_EMPTY = (float) .4;
  boolean smoothUniform = true;
  boolean fixEmptyStart = true;
  float PROB_EMPTY_START = (float) .2;
  float lambda = (float) .2;
  private ArrayN prob_arr; //prob_arr will be formed by normalization from params

  public ATableHMM(int maxsize) {
    MAX_LENGTH = maxsize;
    params = new float[2 * MAX_LENGTH + 1];
    counts = new float[2 * MAX_LENGTH + 1];
    initialCounts = new float[MAX_LENGTH + 2]; // from 1 to MAX_LENGTH filed 0 is not used
    prob_arr = new ArrayN(MAX_LENGTH + 2, MAX_LENGTH + 1, MAX_LENGTH + 1);


  }


  public ATableHMM() {
  };


  /**
   * Get the initial probability p(i|l) i is in 0..l , l is the length of the english sentence
   */

  public float getInitialProb(int index, int l) {

    if (index == 2 * l + 1) {
      index -= l;
    }
    return prob_arr.get(index, 0, l);
  }


  public boolean isPopulated() {
    return count > countCutoff;
  }


  /**
   * Get the probability p(i|i_prev,l) i is from 1 to 2L and i_prev is in the same set as well
   */
  public float getProbHMM(int i, int i_prev, int l) {
    boolean empty = false;

    if (i_prev > 2 * l) {
      return 0;
    }
    if ((i == 0) && (i_prev > 0)) {
      return 0;
    }
    if (i_prev == 0) {
      if ((i > l) && (i < 2 * l + 1)) {
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
      if (i <= l) {
        empty = true;
      }
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


  /**
   * Smooth the basic probability if smoothUniform is on
   */

  public float getProb(int i, int i_prev, int l) {
    float prob;

    if ((i < 2 * l + 1) && (i > l) && (i_prev != i) && (i_prev != (i - l))) {
      return 0;
    }


    prob = getProbHMM(i, i_prev, l);

    if (this.smoothUniform) {
      return (1 - lambda) * prob + lambda * 1 / (float) (l + 2);

    } else {
      return prob;
    }


  }


  /**
   * Increment the corresponding counts
   */
  public void incCount(int i, int i_prev, int l, double val1) {

    float val = (float) val1;

    if ((i_prev == 0)) {
      if (i == 2 * l + 1) {
        i -= l;
      }
      incCountInitPos(i, val);
    } else {
      if ((i > l) && (i <= 2 * l)) {
        incEmpty(val);
      } else {
        if (i_prev > l) {
          i_prev -= l;
        }
        if (i > l) {
          i -= l;
        }
        incCount(i - i_prev, val);

      }
    }


  }


  /**
   * Increment the count for a jump of distance distance
   */
  public void incCount(int distance, float cnt) {
    counts[distance + MAX_LENGTH] += cnt;

  }


  /**
   * Get the prior probability of jumping a distance distance
   */

  public float getProbJump(int distance) {
    return params[distance + MAX_LENGTH];

  }

  /**
   * Increment the count for a zero jump with cnt
   */

  public void incEmpty(float cnt) {
    counts[0] += cnt;

  }

  /**
   * Increment the count for an initial jump to position I
   */

  public void incCountInitPos(int i, float cnt) {
    initialCounts[i] += cnt;
  }

  /**
   * Calculate normalized initial parameters from the counts in initialCounts
   * This assumes we already have pEmpty calculated from params
   * First, normalizes initialCounts so that p(1)+ ..p(MAX_LENGTH)+p0 is 1
   */

  public void normalizeInitialProbs() {

    float total, total_mass;

    total = 0;
    float uniform = 1 / (float) (initialCounts.length);
    for (int i = 1; i < initialCounts.length; i++) {
      total += initialCounts[i] + PROB_SMOOTH;
    }
    System.out.println(" total is " + total);
    if (total == 0) {
      for (int i = 1; i < initialCounts.length; i++) {
        initialCounts[i] = uniform;
        total += uniform;
      }
      if (initialCounts[0] == 0) {
        initialCounts[0] = uniform;
      }

    }

    if (!this.fixEmptyStart) {
      initialCounts[0] = (initialCounts[0] + PROB_SMOOTH) / (total + initialCounts[0] + PROB_SMOOTH);
    } else {
      //System.out.println("pEmpty is "+pEmpty);
      initialCounts[0] = PROB_EMPTY_START;
    }

    for (int i = 1; i <= MAX_LENGTH + 1; i++) {
      initialCounts[i] = (initialCounts[i] + PROB_SMOOTH) * (1 - initialCounts[0]) / total;
    }
    total_mass = initialCounts[0] + initialCounts[1];
    for (int length = 1; length <= MAX_LENGTH; length++) {
      total_mass += initialCounts[length + 1];
      for (int i = 0; i <= length + 1; i++) {
        //System.out.println(" setting initial "+i+" for "+length+" "+initialCounts[i]/total_mass+" total mass is "+total_mass);
        prob_arr.set((initialCounts[i] / total_mass), i, 0, length);

      }

    }

  }


  /**
   * Before starting a new iteration the counts should be zero-ed
   */

  public void zeroCounts() {
    for (int i = 0; i <= MAX_LENGTH + 1; i++) {
      initialCounts[i] = 0;
    }
    for (int i = 0; i < counts.length; i++) {
      counts[i] = 0;
    }

  }


  /**
   * Normalize the transition table prob_arr and put the appropriate probabilities there
   */

  public void normalizeProbArr() {
    //first make the params sum to 1, put params[0] into pEmpty
    //then put normalzied params in prob_arr

    float total, prob_mass;
    float p;
    int diff;

    total = 0;
    float uniform = 1 / (float) counts.length;

    for (int i = 1; i < counts.length; i++) {
      total += counts[i] + PROB_SMOOTH;
    }
    System.out.println(" total is " + total);
    if (total == 0) {
      for (int i = 1; i < counts.length; i++) {
        counts[i] = uniform;
        total += uniform;
      }
      if (counts[0] == 0) {
        counts[0] = uniform;
      }


    }
    if (!this.fixEmpty) {
      params[0] = (counts[0] + PROB_SMOOTH) / (total + counts[0] + PROB_SMOOTH);
      pEmpty = params[0];
    } else {
      //System.out.println("pEmpty is "+pEmpty);
      params[0] = pEmpty;
    }

    for (int i = 1; i < params.length; i++) {
      params[i] = (1 - pEmpty) * (counts[i] + PROB_SMOOTH) / total;
    }
    pEmpty = params[0]; //this one is used by initialParams


    prob_mass = pEmpty;
    prob_mass += getProbJump(1);

    for (int i_prev = 1; i_prev <= MAX_LENGTH; i_prev++) {
      prob_mass += getProbJump(-i_prev + 1);
      for (int l = i_prev; l <= MAX_LENGTH; l++) {

        //System.out.println(" setting p 0 "+i_prev+" "+pEmpty/prob_mass);
        prob_arr.set(pEmpty / prob_mass, 0, i_prev, l);
        for (int i = 1; i <= l + 1; i++) {
          diff = i - i_prev;
          p = getProbJump(diff);
          prob_arr.set(p / prob_mass, i, i_prev, l);
          //System.out.println(" setting p  "+i+" "+i_prev+" "+p/prob_mass);
        }
        if (l < MAX_LENGTH) {
          prob_mass += getProbJump(l + 2 - i_prev);
        }


      }//for l
      //now we need to substract the jumps from 2 to MAX_LENGTH+1-i_prev
      for (int jump = 2; jump <= MAX_LENGTH + 1 - i_prev; jump++) {
        prob_mass -= getProbJump(jump);
      }
    }


  }


  /**
   * This does the normalization of the component distributions
   */
  public void normalize() {
    normalizeProbArr();
    normalizeInitialProbs();

    if (GlobalParams.verbose) {
      this.printBasicProbs();
    }
    zeroCounts(); //prepare for the next eStep

  }


  /**
   * Initialize the probabilities in a brain dead manner uniformly
   */

  public void initializeUniform() {
    // first the initial probabilities
    float inc_init = 1 / (float) (MAX_LENGTH + 2);
    float inc;
    float empty = PROB_EMPTY; //making as the others
    for (int i = 0; i <= MAX_LENGTH + 1; i++) {
      incCountInitPos(i, inc_init);
    }

    pEmpty = empty;
    //then the jump + empty probabilities
    incEmpty(empty);
    inc = (1 - empty) / (float) (2 * MAX_LENGTH);
    for (int dist = -MAX_LENGTH + 1; dist <= MAX_LENGTH; dist++) {
      incCount(dist, inc);
    }
    normalize();


  }


  /*
  * Copy all the values of a
  */

  public void initialize(ATable a1) {

    ATableHMM a = (ATableHMM) a1; //a little dirty here

    for (int i = 0; i < params.length; i++) {

      counts[i] = a.params[i];

    }


    //the initial probs

    //now the initial probs
    for (int jump = 0; jump <= MAX_LENGTH + 1; jump++) {


      initialCounts[jump] = a.getProbHMM(jump > MAX_LENGTH ? 2 * MAX_LENGTH + 1 : jump, 0, MAX_LENGTH);


    }//jump


    pEmpty = a.getEmpty();
    //normalize the tables
    float old;
    old = PROB_SMOOTH;
    PROB_SMOOTH = 0;
    normalize();
    PROB_SMOOTH = old;
    System.out.println(checkOK());


  }

  public float getEmpty() {
    return pEmpty;

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
   * Check OK for a specifi length len
   */


  public boolean checkOK(int len) {
    float total = 0;
    //check initial
    for (int i = 0; i <= len + 1; i++) {
      total += getInitialProb(i, len);
    }
    if (Mabs(total - 1) > .001) {
      System.out.println(" not ok in initialprobs total is " + total);
      return false;
    }

    for (int i_prev = 1; i_prev <= 2 * len; i_prev++) {
      total = 0;
      for (int i = 1; i <= 2 * len + 1; i++) {
        total += getProb(i, i_prev, len);
      }
      if (Mabs(total - 1) > .001) {
        System.out.println(" not ok in iPrev " + i_prev + " total is " + total + " len " + len);
        return false;
      }

    }

    return true;


  }


  public void printProbs() {

    //print the initial probabilities

    int i_prev = 0;
    for (int l = 1; l <= MAX_LENGTH; l++) {

      for (int i = 0; i <= l + 1; i++) {
        System.out.println("P(" + i + "|" + i_prev + "," + l + ")" + getProb(i, i_prev, l));

      }
    }

    for (int l = 1; l <= MAX_LENGTH; l++) {
      for (i_prev = 1; i_prev <= l; i_prev++) {
        for (int i = 1; i <= 2 * l + 1; i++) {
          System.out.println("P(" + i + "|" + i_prev + "," + l + ")" + getProb(i, i_prev, l));

        }
        System.out.println("*************************");


      }
      System.out.println("*************************");

    }


  }


  public void printBasicProbs() {

    //print the suff in initialCounts and in params
    for (int i = 0; i <= MAX_LENGTH + 1; i++) {
      System.out.println("Initial" + i + " " + initialCounts[i]);
    }

    System.out.println("**********************************");
    System.out.println(" empty " + params[0]);

    for (int dist = -MAX_LENGTH + 1; dist <= MAX_LENGTH; dist++) {
      System.out.println(" jump " + dist + " " + getProbJump(dist));
    }

  }

  public float Mabs(float x) {
    if (x < 0) {
      x = -x;
    }
    return x;
  }


  /**
   * Some code to test the class
   */


  public static void main(String[] args) {

    ATableHMM a = new ATableHMM();
    a.read(args[0]);
    a.save("d:\\mt\\file1");


  }


  /**
   * Saving just the jump probabilities
   */

  public void save(String filename) {
    try {
      PrintStream out = new PrintStream(new FileOutputStream(filename, true));
      //MAX_LENGTH
      out.println(MAX_LENGTH);
      out.println("pEmpty " + params[0]);


      for (int jump = -MAX_LENGTH + 1; jump <= MAX_LENGTH; jump++) {

        out.print(jump + " " + this.getProbJump(jump) + "\t");


      }//jump

      out.println();

      //now the initial probs
      for (int jump = 0; jump <= MAX_LENGTH + 1; jump++) {

        if (jump == MAX_LENGTH + 1) {
          jump += MAX_LENGTH;
        }

        out.print(jump + " " + getProbHMM(jump, 0, MAX_LENGTH) + "\t");


      }//jump

      out.println();
      out.close();
    } catch (Exception e) {
      e.printStackTrace();
    }


  }


  /**
   * reading the jump probabilities and initializing
   */

  public void read(String filename) {
    String line, token;
    try {
      InFile in = new InFile(filename);
      //MAX_LENGTH
      MAX_LENGTH = (new Integer(in.readLine())).intValue();
      params = new float[2 * MAX_LENGTH + 1];
      counts = new float[2 * MAX_LENGTH + 1];
      initialCounts = new float[MAX_LENGTH + 2]; // from 1 to MAX_LENGTH filed 0 is not used
      prob_arr = new ArrayN(MAX_LENGTH + 2, MAX_LENGTH + 1, MAX_LENGTH + 1);
      pEmpty = PROB_EMPTY;

      line = in.readLine();
      StringTokenizer st;
      st = new StringTokenizer(line, " \t");
      token = st.nextToken();
      token = st.nextToken();
      counts[0] = (float) (new Double(token)).doubleValue();
      int current = 1;


      line = in.readLine(); //read the line of probabilities
      st = new StringTokenizer(line, " \t");
      while (st.hasMoreTokens()) {
        token = st.nextToken();//skip the jump size
        token = st.nextToken();
        counts[current++] = (float) (new Double(token)).doubleValue();

      }


      line = in.readLine(); //read the line of initial probabilities
      st = new StringTokenizer(line, " \t");
      current = 0;
      while (st.hasMoreTokens()) {
        token = st.nextToken();//skip the jump size
        token = st.nextToken();
        initialCounts[current++] = (float) (new Double(token)).doubleValue();

      }


      for (int jump = -MAX_LENGTH + 2; jump <= MAX_LENGTH; jump++) {


        System.out.print(jump + " " + this.getProbJump(jump) + "\t");


      }//jump

      System.out.println();

      in.close();

      //normalize the tables
      float old;
      old = PROB_SMOOTH;
      PROB_SMOOTH = (float) 1E-12;
      normalize();
      PROB_SMOOTH = old;

    } catch (Exception e) {
      e.printStackTrace();
    }


  }


  /*
   * calculate the kl divergance between this table and a in terms of the
   * jump probabilities
   */
  public double DKL(ATable a1) {


    double p, q;
    double d = 0;

    ATableHMM a = (ATableHMM) a1;

    d += getEmpty() * (Math.log(getEmpty()) - Math.log(a.getEmpty()));
    for (int jump = -MAX_LENGTH + 1; jump <= MAX_LENGTH - 1; jump++) {

      p = getProbJump(jump);
      q = a.getProbJump(jump);
      d += p * (Math.log(p) - Math.log(q));

    }
    return d;


  }


}
