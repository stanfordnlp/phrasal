package mt.hmmalign;

/**
 * The purpose of this class is to hold counts and alignment probabilities for
 * an HMM p(aj|aj-1,aj-2,l) .These are estimated by having a set of parameters p(delta) for
 * the probability of each jump and the probability of going to the empty english word
 * Additional states for the empty word are added to encode the position of the previous non-empty word
 * There are also parameters p(a1|0) that play the role of initial parameters for the HMM
 *@author Kristina Toutanova (kristina@cs.stanford.edu)
 */

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.StringTokenizer;
import edu.stanford.nlp.util.ArrayN;

public class ATableHMM2 extends ATable {

  //we need arrays to keep the counts, the parameters
  //and finally the probabilities
  float[][] params; //p0 is the probability of empty, p1 is p(-MAX_LENGTH+1) , etc . to p(MAX_LENGTH-1)
  float[][] counts;// keep here the counts rather than in params
  float[] initialCounts;
  int shiftParams;
  float pEmpty;// this is the probability of going to the empty word
  float PROB_SMOOTH = (float) .0001;
  float PROB_EMPTY = (float) .4;
  boolean smoothUniform = true;
  float lambda = (float) .2;
  private ArrayN prob_arr; //prob_arr will be formed by normalization from params

  public ATableHMM2(int maxsize) {
    MAX_LENGTH = maxsize;
    params = new float[2 * MAX_LENGTH + 1][2 * MAX_LENGTH];
    counts = new float[2 * MAX_LENGTH + 1][2 * MAX_LENGTH];
    initialCounts = new float[MAX_LENGTH + 2]; // from 1 to MAX_LENGTH+1 filed 0 is not used
    prob_arr = new ArrayN(MAX_LENGTH + 2, MAX_LENGTH + 1, MAX_LENGTH + 1, MAX_LENGTH + 1);


  }


  public ATableHMM2() {
  }

  /**
   * Get the initial probability p(i|l) i is in 0..l , l is the length of the english sentence
   */

  public float getInitialProb(int index, int l) {
    return prob_arr.get(index, 0, 0, l);
  }


  public boolean isPopulated() {
    return count > countCutoff;
  }


  /**
   * Get the probability p(i|i_prev,i_prev2,l) i is from 1 to 2L+1 and i_prev,i_prev2 are in the same set as well without 2L+1
   */
  public float getProbHMM(int i, int i_prev, int i_prev2, int l) {
    boolean empty = false;

    if ((i_prev > 2 * l) || (i_prev2 > 2 * l)) {
      return 0;
    }
    if ((i == 0) && ((i_prev > 0) || (i_prev2 > 0))) {
      return 0;
    }
    if ((i_prev == 0) && (i_prev2 == 0)) {
      if (i > l + 1) {
        return 0;
      } else {
        return getInitialProb(i, l);
      }
    }

    if (i_prev2 > l) {
      i_prev2 -= l;
    }

    if (i > l) {
      i -= l;
      if (i <= l) {
        empty = true;
      }
    }

    if (i_prev > l) {
      i_prev = 0;
    }


    if (empty) {
      if ((i_prev > 0) && (i != i_prev)) {
        return 0;
      }
      if ((i_prev == 0) && (i != i_prev2)) {
        return 0;
      } else {
        return prob_arr.get(0, i_prev, i_prev2, l);
      }
    } else {
      return prob_arr.get(i, i_prev, i_prev2, l);

    }


  }


  /**
   * Smooth the basic probability if smoothUniform is on
   */

  public float getProb(int i, int i_prev, int i_prev2, int l) {
    float prob;
    prob = getProbHMM(i, i_prev, i_prev2, l);
    if (prob == 0) {
      return prob;
    }
    if (this.smoothUniform) {
      return (1 - lambda) * prob + lambda * 1 / (float) (l + 2);

    } else {
      return prob;
    }


  }


  /**
   * Increment the corresponding counts
   */
  public void incCount(int i, int i_prev, int i_prev2, int l, double val1) {


    float val = (float) val1;
    int jump, jump_1;

    if ((i_prev == 0) && (i_prev2 == 0)) {
      incCountInitPos(i, val);
    } else {
      //we need jump and jump_1
      if (i_prev2 > l) {
        i_prev2 -= l;
      }
      if (i_prev > l) {
        jump_1 = -MAX_LENGTH;
      } else {
        jump_1 = i_prev - i_prev2;
      }
      if ((i > l) && (i <= 2 * l)) {
        jump = -MAX_LENGTH;
        if ((i - l != i_prev) && (i != i_prev)) {
          return;
        }
      } else {
        if (i_prev > l) {
          jump = i - (i_prev - l);
        } else {
          jump = i - i_prev;
        }

      }
      if (jump_1 == l) {
        jump_1--;
      }
      incCount(jump, jump_1, val);


    }
  }


  /**
   * Increment the count for a jump of distance distance
   */
  public void incCount(int distance, int distance1, float cnt) {
    counts[distance + MAX_LENGTH][distance1 + MAX_LENGTH] += cnt;

  }


  /**
   * Get the prior probability of jumping a distance distance
   */

  public float getProbJump(int distance, int distance1) {
    return params[distance + MAX_LENGTH][distance1 + MAX_LENGTH];

  }

  /**
   * Increment the count for a zero jump with cnt
   */

  public void incEmpty(int absjump, float cnt) {
    counts[0][absjump + MAX_LENGTH] += cnt;

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
    float total = 0, total_mass;
    for (int i = 0; i <= MAX_LENGTH + 1; i++) {
      total += initialCounts[i] + PROB_SMOOTH;
    }
    for (int i = 0; i <= MAX_LENGTH + 1; i++) {
      initialCounts[i] = (initialCounts[i] + PROB_SMOOTH) / total;
    }
    total_mass = initialCounts[0] + initialCounts[1];
    for (int length = 1; length <= MAX_LENGTH; length++) {
      total_mass += initialCounts[length + 1];
      for (int i = 0; i <= length + 1; i++) {
        prob_arr.set((initialCounts[i] / total_mass), i, 0, 0, length);

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
      for (int j = 0; j < counts[i].length; j++) {
        counts[i][j] = 0;
      }
    }

  }


  /**
   * Normalize the transition table prob_arr and put the appropriate probabilities there
   */

  public void normalizeProbArr() {
    //first make the params sum to 1, put params[0] into pEmpty
    //then put normalzied params in prob_arr

    float total;
    float prob;
    int jump, jump_1;

    total = 0;

    for (int jump_1Abs = 0; jump_1Abs <= 2 * MAX_LENGTH - 1; jump_1Abs++) {
      total = 0;
      for (int i = 1; i < counts[jump_1Abs].length + 1; i++) {
        total += counts[i][jump_1Abs] + PROB_SMOOTH;
      }
      if (!this.fixEmpty) {
        params[0][jump_1Abs] = (counts[0][jump_1Abs] + PROB_SMOOTH) / (total + counts[0][jump_1Abs] + PROB_SMOOTH);
        pEmpty = params[0][jump_1Abs];
      } else {
        //System.out.println("pEmpty is "+pEmpty);
        params[0][jump_1Abs] = pEmpty;
      }

      //normalize

      for (int i = 1; i < params[jump_1Abs].length + 1; i++) {
        params[i][jump_1Abs] = (1 - pEmpty) * (counts[i][jump_1Abs] + PROB_SMOOTH) / total;
      }
      pEmpty = params[0][jump_1Abs]; //this one is used by initialParams


    }


    for (int l = 1; l <= MAX_LENGTH; l++) {

      for (int i_prev2 = 0; i_prev2 <= l; i_prev2++) {

        for (int i_prev = 0; i_prev <= l; i_prev++) {

          if ((i_prev == 0) && (i_prev2 == 0)) {
            continue;
          }

          if (i_prev == 0) {
            jump_1 = -MAX_LENGTH;
          } else {
            jump_1 = i_prev - i_prev2;
            if (jump_1 == l) {
              jump_1--;
            }
          }

          total = 0;

          for (int i = 0; i <= l + 1; i++) {

            if (i == 0) {
              jump = -MAX_LENGTH;
            } else {

              if (i_prev == 0) {
                jump = i - i_prev2;
              } else {
                jump = i - i_prev;
              }
            }
            total += getProbJump(jump, jump_1);


          }

          for (int i = 0; i <= l + 1; i++) {

            if (i == 0) {
              jump = -MAX_LENGTH;
            } else {

              if (i_prev == 0) {
                jump = i - i_prev2;
              } else {
                jump = i - i_prev;
              }
            }

            prob = getProbJump(jump, jump_1) / total;

            prob_arr.set(prob, i, i_prev, i_prev2, l);


          }


        }


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
    float empty = PROB_EMPTY; //making the empty twice lower than the others
    for (int i = 0; i <= MAX_LENGTH + 1; i++) {
      incCountInitPos(i, inc_init);
    }

    pEmpty = empty;
    //then the jump + empty probabilities
    for (int dist1 = -MAX_LENGTH; dist1 <= MAX_LENGTH - 1; dist1++) {
      incEmpty(dist1, empty);
      inc = (1 - empty) / (float) (2 * MAX_LENGTH);
      for (int dist = -MAX_LENGTH + 1; dist <= MAX_LENGTH; dist++) {
        incCount(dist, dist1, inc);
      }
    }
    normalize();


  }

  /*
  * Copy all the values of a
  */

  public void initialize(ATable a1) {


    ATableHMM2 a = (ATableHMM2) a1;
    int i1, i_p1;
    for (int l = 1; l <= MAX_LENGTH; l++) {

      for (int i_pp = 0; i_pp <= l; i_pp++) {
        for (int i_p = 0; i_p <= l; i_p++) {
          for (int i = 0; i <= l + 1; i++) {

            i_p1 = i_p;
            if ((i_p == 0) && (i_pp > 0)) {
              i_p1 = i_pp + l;
            }


            i1 = i;
            if ((i == 0) && (i_p1 > 0)) {
              i1 = i_p1;
              if (i1 <= l) {
                i1 += l;
              }
            }

            if ((i == l + 1) && (i_p1 > 0)) {
              i1 = i + l;
            }
            this.prob_arr.set(a.getProb(i1, i_p1, i_pp, l), i, i_p, i_pp, l);

          }

        }

      }
    }
    pEmpty = a.getEmpty();


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
   * Check OK for a specific length len
   */


  public boolean checkOK(int len) {
    float total = 0;
    //check initial
    for (int i = 0; i <= len + 1; i++) {
      total += getInitialProb(i, len);
    }
    if (Mabs(total - 1) > .001) {
      return false;
    }

    for (int i_prev2 = 0; i_prev2 <= 2 * len; i_prev2++) {
      for (int i_prev = 0; i_prev <= 2 * len; i_prev++) {
        if ((i_prev2 == 0) && (i_prev > len)) {
          continue;
        }
        total = 0;

        for (int i = 0; i <= 2 * len + 1; i++) {
          total += getProb(i, i_prev, i_prev2, len);
        }
        if (Mabs(total - 1) > .001) {
          return false;
        }

      }
    }

    return true;


  }


  public void printProbs() {

    //print the initial probabilities

    int i_prev = 0, i_prev2 = 0;
    for (int l = 1; l <= MAX_LENGTH; l++) {

      for (int i = 0; i <= l + 1; i++) {
        System.out.println("P(" + i + "|" + i_prev + "," + i_prev2 + "," + l + ")" + getProb(i, i_prev, i_prev2, l));

      }
    }

    for (int l = 1; l <= MAX_LENGTH; l++) {
      for (i_prev2 = 0; i_prev2 <= l; i_prev2++) {
        for (i_prev = 0; i_prev <= l; i_prev++) {
          if ((i_prev == 0) && (i_prev2 == 0)) {
            continue;
          }
          if (i_prev == 0) {
            i_prev = i_prev2 + l;
          }
          for (int i = 1; i <= 2 * l + 1; i++) {
            System.out.println("P(" + i + "|" + i_prev + "," + i_prev2 + "," + l + ")" + getProb(i, i_prev, i_prev2, l));

          }
          System.out.println("*************************");


        }
      }
      System.out.println("*************************");

    }


  }


  public void printBasicProbs() {

    //print the stuff in initialCounts and in params
    for (int i = 0; i <= MAX_LENGTH + 1; i++) {
      System.out.println("Initial" + i + " " + initialCounts[i]);
    }

    System.out.println("**********************************");
    //System.out.println(" empty "+params[0]);

    for (int dist = -MAX_LENGTH; dist <= MAX_LENGTH; dist++) {
      for (int dist1 = -MAX_LENGTH; dist1 <= MAX_LENGTH; dist1++) {
        System.out.println(" jump " + dist + " " + dist1 + " " + getProbJump(dist1, dist));
      }
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
    boolean ok;

    ATableHMM2 a = new ATableHMM2(3);
    a.initializeUniform();
    System.out.println("Printing a ");
    a.printProbs();
    ok = a.checkOK();
    System.out.println("a is" + ok);

    ATableHMM2 b = new ATableHMM2(3);
    b.initialize(a);

    System.out.println("Printing b");
    b.printProbs();
    ok = a.checkOK();
    System.out.println("a is" + ok);
    ok = b.checkOK();
    System.out.println("b is " + ok);


  }


  /**
   * Saving just the jump probabilities
   */

  public void save(String filename) {
    try {
      PrintStream out = new PrintStream(new FileOutputStream(filename, true));
      //MAX_LENGTH
      out.println(MAX_LENGTH);

      for (int jump_1 = -MAX_LENGTH; jump_1 <= MAX_LENGTH - 1; jump_1++) {
        for (int jump = -MAX_LENGTH; jump <= MAX_LENGTH; jump++) {


          out.print(jump + " " + jump_1 + " " + this.getProbJump(jump, jump_1) + "\t");


        }//jump
        out.println();
      }

      //now the initial probs
      for (int jump = 0; jump <= MAX_LENGTH + 1; jump++) {


        out.print(jump + " " + getProbHMM(jump, 0, 0, MAX_LENGTH) + "\t");


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
    int current;
    String line, token;
    StringTokenizer st;
    try {
      InFile in = new InFile(filename);
      //MAX_LENGTH
      MAX_LENGTH = (new Integer(in.readLine())).intValue();
      params = new float[2 * MAX_LENGTH + 1][2 * MAX_LENGTH];
      counts = new float[2 * MAX_LENGTH + 1][2 * MAX_LENGTH];
      initialCounts = new float[MAX_LENGTH + 2]; // from 1 to MAX_LENGTH+1 filed 0 is not used
      prob_arr = new ArrayN(MAX_LENGTH + 2, MAX_LENGTH + 1, MAX_LENGTH + 1, MAX_LENGTH + 1);
      pEmpty = PROB_EMPTY;


      for (int current_jump_prev = 0; current_jump_prev < 2 * MAX_LENGTH; current_jump_prev++) {
        current = 0;
        line = in.readLine(); //read the line of probabilities
        st = new StringTokenizer(line, " \t");
        if (st.countTokens() == 2) {
          in.close();
          readHMM1(filename);
          return;
        }
        while (st.hasMoreTokens()) {
          token = st.nextToken();
          token = st.nextToken();//skip the jump size and the previous size
          token = st.nextToken();
          counts[current++][current_jump_prev] = (float) (new Double(token)).doubleValue();

        }
      }

      line = in.readLine(); //read the line of initial probabilities
      st = new StringTokenizer(line, " \t");
      current = 0;
      while (st.hasMoreTokens()) {
        token = st.nextToken();//skip the jump size
        token = st.nextToken();
        initialCounts[current++] = (float) (new Double(token)).doubleValue();

      }


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


  /**
   * reading the jump probabilities from an HMM1 file and initializing
   */

  public void readHMM1(String filename) {
    String line, token;
    try {
      InFile in = new InFile(filename);
      //MAX_LENGTH
      MAX_LENGTH = (new Integer(in.readLine())).intValue();
      params = new float[2 * MAX_LENGTH + 1][2 * MAX_LENGTH];
      counts = new float[2 * MAX_LENGTH + 1][2 * MAX_LENGTH];
      initialCounts = new float[MAX_LENGTH + 2]; // from 1 to MAX_LENGTH filed 0 is not used
      prob_arr = new ArrayN(MAX_LENGTH + 2, MAX_LENGTH + 1, MAX_LENGTH + 1, MAX_LENGTH + 1);
      pEmpty = PROB_EMPTY;

      line = in.readLine();
      StringTokenizer st;
      st = new StringTokenizer(line, " \t");
      token = st.nextToken();
      token = st.nextToken();

      counts[0][0] = (float) (new Double(token)).doubleValue();
      for (int jmp = 0; jmp < 2 * MAX_LENGTH; jmp++) {
        counts[0][jmp] = counts[0][0];
      }

      int current = 1;


      line = in.readLine(); //read the line of probabilities
      st = new StringTokenizer(line, " \t");
      while (st.hasMoreTokens()) {
        token = st.nextToken();//skip the jump size
        token = st.nextToken();
        counts[current++][0] = (float) (new Double(token)).doubleValue();

      }

      for (int jmp = 0; jmp < 2 * MAX_LENGTH; jmp++) {
        for (int jmp1 = 1; jmp1 < 2 * MAX_LENGTH + 1; jmp1++) {
          counts[jmp1][jmp] = counts[jmp1][0];
        }
      }


      line = in.readLine(); //read the line of initial probabilities
      st = new StringTokenizer(line, " \t");
      current = 0;
      while (st.hasMoreTokens()) {
        token = st.nextToken();//skip the jump size
        token = st.nextToken();
        initialCounts[current++] = (float) (new Double(token)).doubleValue();

      }


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
  public double DKL(ATableHMM a) {
    return 0;
  }


}
