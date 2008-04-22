package mt.hmmalign;

/**
 * this is like ATableHMM2 but uses equivalence classes
 * for the previous jumps
 *@author Kristina Toutanova (kristina@cs.stanford.edu)
 */

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.StringTokenizer;
import edu.stanford.nlp.util.ArrayN;

public class ATableHMM2EQ extends ATable {

  //we need arrays to keep the counts, the parameters
  //and finally the probabilities
  float[][] params; //p0 is the probability of empty, p1 is p(-MAX_LENGTH+1) , etc . to p(MAX_LENGTH-1)
  float[][] counts;// keep here the counts rather than in params
  float[] initialCounts;
  int shiftParams;
  float pEmpty;// this is the probability of going to the empty word
  float PROB_SMOOTH = (float) 1;
  float PROB_EMPTY = (float) 0.4;
  boolean fixEmptyStart = false;
  float PROB_EMPTY_START = (float) .2;
  boolean smoothUniform = true;
  float lambda = (float) .2;
  private ArrayN prob_arr; //prob_arr will be formed by normalization from params
  static int MAX_FLDS = 4;

  public ATableHMM2EQ(int maxsize) {
    MAX_LENGTH = maxsize;
    params = new float[2 * MAX_LENGTH + 1][2 * MAX_FLDS];
    counts = new float[2 * MAX_LENGTH + 1][2 * MAX_FLDS];
    initialCounts = new float[MAX_LENGTH + 2]; // from 1 to MAX_LENGTH+1 filed 0 is not used
    prob_arr = new ArrayN(MAX_LENGTH + 2, MAX_LENGTH + 1, 2 * MAX_FLDS, MAX_LENGTH + 1);


  }

  public ATableHMM2EQ() {
  }


  /**
   * Get the initial probability p(i|l) i is in 0..l , l is the length of the english sentence
   */

  public float getInitialProb(int index, int l) {

    if (index > l + 1) {
      return 0;
    }
    return prob_arr.get(index, 0, MAX_FLDS, l);
  }


  public boolean isPopulated() {
    return count > countCutoff;
  }


  /**
   * Get the probability p(i|i_prev,i_prev2,l) i is from 1 to 2L+1 and i_prev,i_prev2 are in the same set as well without 2L+1
   */
  public float getProbHMM(int i, int i_prev, int j_prev2, int l) {
    boolean empty = false;

    if ((i_prev == 0) && (j_prev2 == MAX_FLDS)) {
      if (i == 2 * l + 1) {
        i -= l;
      }
      return getInitialProb(i, l);
    }

    if ((i == 0) && (i_prev > 0)) {
      return 0;
    }

    if (i_prev > l) {
      // the previous is null jump
      if (!isToNull(j_prev2)) {
        return 0;
      }
      if ((i > l) && (i < 2 * l + 1)) {

        if (i != i_prev) {
          return 0;
        }

        return prob_arr.get(0, i_prev - l, j_prev2, l);

      } else {

        if (i > l) {
          i -= l;
        }
        return prob_arr.get(i, i_prev - l, j_prev2, l);
      }


    }// i_prev2>l

    if (!possibleExternal(i_prev, j_prev2, l)) {
      return 0;
    }
    if ((i > l) && (i != (i_prev + l)) && (i < 2 * l + 1)) {
      return 0;
    }
    if (i > l) {
      if (i > 2 * l) {
        i -= l;
      } else {
        i = 0;
      }
    }
    return prob_arr.get(i, i_prev, j_prev2, l);


  }


  public boolean isToNull(int jump) {
    return (jump == 0);

  }

  /*
   * Is it possible to get with a jump jump to position i
   */

  public boolean possibleExternal(int i_prev, int jump, int l) {


    if ((i_prev == 0) && (jump != MAX_FLDS)) {
      return false;
    }


    if ((i_prev > l)) {
      return (jump == 0 ? true : false);
    }

    if (i_prev - jump + MAX_FLDS < 0) {
      return false;
    }

    if (i_prev - jump + MAX_FLDS > l) {
      return false;
    }

    return true;


  }


  public boolean possibleInternal(int i_prev, int jump, int l) {

    // in the internal i_prev is from 0 to l and jump is from 0 to 2MAXFLDS-1


    if ((i_prev == 0) && (jump != MAX_FLDS)) {
      return false;
    }

    if ((jump == 0) && (i_prev > 0)) {
      return true;
    }

    if (i_prev - (jump - MAX_FLDS) < 0) {
      return false;
    }

    if (i_prev - (jump - MAX_FLDS) > l) {
      return false;
    }

    return true;


  }


  /**
   * Smooth the basic probability if smoothUniform is on
   */

  public float getProb(int i, int i_prev, int i_prev2, int l) {
    float prob;


    if ((i < 2 * l + 1) && (i > l) && (i_prev != i) && (i_prev != (i - l))) {
      return 0;
    }

    if ((i == 0) && (i_prev > 0)) {
      return 0;
    }

    if (!this.possibleExternal(i_prev, i_prev2, l)) {
      return 0;
    }

    prob = getProbHMM(i, i_prev, i_prev2, l);
    //if(prob==0){return prob;}
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
    jump_1 = i_prev2;

    //if(i>2*l){System.out.println(" incrementing in error" );return;}

    if ((i > l) && (i < 2 * l + 1)) {

      if ((i_prev != i) && (i_prev != (i - l))) {
        System.out.println(" Incrementing in error ");
        return;
      }
      jump = -MAX_LENGTH;

    } else {
      if (i_prev > l) {
        i_prev -= l;
      }
      ;
      if (i == 2 * l + 1) {
        i -= l;
      }
      jump = i - i_prev;

    }


    if ((i_prev == 0) && (i_prev2 == MAX_FLDS)) {
      initialCounts[jump] += val;
    } else {
      incCount(jump, jump_1 - MAX_FLDS, val);
    }
  }


  /**
   * Increment the count for a jump of distance distance
   */
  public void incCount(int distance, int distance1, float cnt) {
    counts[distance + MAX_LENGTH][distance1 + MAX_FLDS] += cnt;

  }


  /**
   * Get the prior probability of jumping a distance distance
   */

  public float getProbJump(int distance, int distance1) {
    return params[distance + MAX_LENGTH][distance1 + MAX_FLDS];

  }

  /**
   * Increment the count for a zero jump with cnt
   */

  public void incEmpty(int absjump, float cnt) {
    counts[0][absjump + MAX_FLDS] += cnt;

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
        prob_arr.set((initialCounts[i] / total_mass), i, 0, MAX_FLDS, length);

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

    float total, prob_mass;
    float p, prob;
    int diff, jump, jump_1;
    float uniform = 1 / (float) (2 * MAX_LENGTH + 1);

    total = 0;

    for (int jump_1Abs = 0; jump_1Abs <= 2 * MAX_FLDS - 1; jump_1Abs++) {
      total = 0;
      for (int i = 1; i < 2 * MAX_LENGTH + 1; i++) {
        total += counts[i][jump_1Abs] + PROB_SMOOTH;
      }

      System.out.println(" total is " + total + " for abs jmp" + jump_1Abs);
      if (total == 0) {
        for (int i = 1; i < 2 * MAX_LENGTH + 1; i++) {
          counts[i][jump_1Abs] = uniform;
          total += uniform;
        }
        if (counts[0][jump_1Abs] == 0) {
          counts[0][jump_1Abs] = uniform;
        }


      }


      if (!this.fixEmpty) {
        params[0][jump_1Abs] = (counts[0][jump_1Abs] + PROB_SMOOTH) / (total + counts[0][jump_1Abs] + PROB_SMOOTH);
        pEmpty = params[0][jump_1Abs];
      } else {
        //System.out.println("pEmpty is "+pEmpty);
        params[0][jump_1Abs] = pEmpty;
      }

      //normalize

      for (int i = 1; i < 2 * MAX_LENGTH + 1; i++) {
        params[i][jump_1Abs] = (1 - pEmpty) * (counts[i][jump_1Abs] + PROB_SMOOTH) / total;
      }
      pEmpty = params[0][jump_1Abs]; //this one is used by initialParams


    }


    for (int l = 1; l <= MAX_LENGTH; l++) {

      for (int j_prev2 = 0; j_prev2 < 2 * MAX_FLDS; j_prev2++) {

        for (int i_prev = 0; i_prev <= l; i_prev++) {

          if (!possibleInternal(i_prev, j_prev2, l)) {
            continue;
          }

          if ((i_prev == 0) && (j_prev2 == MAX_FLDS)) {
            continue;
          } //leaving it for initial probs


          total = 0;

          for (int i = 0; i <= l + 1; i++) {

            if (i == 0) {
              jump = -MAX_LENGTH;
            } else {

              jump = i - i_prev; // the current jump
            }
            total += getProbJump(jump, j_prev2 - MAX_FLDS);

          }

          for (int i = 0; i <= l + 1; i++) {

            if (i == 0) {
              jump = -MAX_LENGTH;
            } else {

              jump = i - i_prev;
            }

            prob = getProbJump(jump, j_prev2 - MAX_FLDS) / total;

            prob_arr.set(prob, i, i_prev, j_prev2, l);


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
    float empty = (fixEmpty ? PROB_EMPTY : 1 / (float) (2 * MAX_LENGTH)); //making the empty twice lower than the others
    for (int i = 0; i <= MAX_LENGTH + 1; i++) {
      incCountInitPos(i, inc_init);
    }

    pEmpty = empty;
    //then the jump + empty probabilities
    for (int dist1 = -MAX_FLDS; dist1 <= MAX_FLDS - 1; dist1++) {
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

    ATableHMM2EQ a = (ATableHMM2EQ) a1; //a little dirty here
    this.MAX_FLDS = a.MAX_FLDS;
    this.MAX_LENGTH = a.MAX_LENGTH;
    params = new float[2 * MAX_LENGTH + 1][2 * MAX_FLDS];
    counts = new float[2 * MAX_LENGTH + 1][2 * MAX_FLDS];
    initialCounts = new float[MAX_LENGTH + 2]; // from 1 to MAX_LENGTH+1 filed 0 is not used
    prob_arr = new ArrayN(MAX_LENGTH + 2, MAX_LENGTH + 1, 2 * MAX_FLDS, MAX_LENGTH + 1);
    pEmpty = PROB_EMPTY;


    for (int i = 0; i < params.length; i++) {
      for (int j = 0; j < params[0].length; j++) {

        counts[i][j] = a.params[i][j];

      }

    }

    //the initial probs

    //now the initial probs
    for (int jump = 0; jump <= MAX_LENGTH + 1; jump++) {


      initialCounts[jump] = a.getProbHMM(jump, 0, MAX_FLDS, MAX_LENGTH);


    }//jump


    pEmpty = a.getEmpty();
    //normalize the tables
    float old;
    old = PROB_SMOOTH;
    PROB_SMOOTH = 0;
    normalize();
    PROB_SMOOTH = old;


  }


  public float getEmpty() {
    return pEmpty;
  }


  public boolean checkOK() {

    boolean ok = true;
    for (int len = 1; len <= MAX_LENGTH; len++) {
      ok = checkOK(len);
      if (!ok) {
        System.out.println(" not ok in " + len);
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
    total += getInitialProb(len + 1, len);
    if (Mabs(total - 1) > .001) {
      System.out.println(" not ok initial prob " + total + " len " + len);
      return false;
    }

    for (int i_prev2 = 0; i_prev2 < 2 * MAX_FLDS; i_prev2++) {
      for (int i_prev = 1; i_prev <= 2 * len; i_prev++) {
        //if((i_prev2==0)&&(i_prev>len)){continue;}
        total = 0;
        if (!possibleExternal(i_prev, i_prev2, len)) {
          continue;
        }

        for (int i = 0; i <= 2 * len + 1; i++) {
          total += getProb(i, i_prev, i_prev2, len);
        }
        if (Mabs(total - 1) > .001) {
          System.out.println(" total is " + total + " for len " + len + " and prev " + i_prev + " jump " + i_prev2);
          for (int i = 0; i <= 2 * len + 1; i++) {
            System.out.println(i + " " + i_prev + " " + len + " " + getProb(i, i_prev, i_prev2, len));
          }


          return false;
        }

      }
    }

    return true;


  }


  //assuming the configuration is correct, return the jump width
  public int jump(int i, int i_p, int l) {
    if (i_p > l) {
      i_p -= l;
    }
    if (i > l) {
      return 0;
    }// a jump to empty
    if ((i - i_p) >= MAX_FLDS - 1) {
      return 2 * MAX_FLDS - 1;
    }
    if ((i - i_p) <= -MAX_FLDS + 1) {
      return 1;
    }
    return (i - i_p + MAX_FLDS);

  }


  public void printProbs() {

    //print the initial probabilities

    int i_prev = 0, i_prev2 = MAX_FLDS;
    for (int l = 1; l <= MAX_LENGTH; l++) {

      for (int i = 0; i <= l + 1; i++) {
        System.out.println("P(" + i + "|" + i_prev + "," + i_prev2 + "," + l + ")" + getProb(i, i_prev, i_prev2, l));

      }
    }

    for (int l = 1; l <= MAX_LENGTH; l++) {
      for (i_prev2 = 0; i_prev2 < 2 * MAX_FLDS; i_prev2++) {
        for (i_prev = 1; i_prev <= l; i_prev++) {

          if (!possibleInternal(i_prev, i_prev2, l)) {
            continue;
          }
          if ((i_prev == 0) && (i_prev2 == MAX_FLDS)) {
            continue;
          } //initial prob

          if (i_prev2 == 0) {
            i_prev += l;
          } // if this is null

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

    for (int dist = -MAX_LENGTH; dist <= MAX_FLDS; dist++) {
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

    ATableHMM2EQ a = new ATableHMM2EQ(4);
    a.initializeUniform();
    System.out.println("Printing a ");
    a.printProbs();
    ok = a.checkOK();
    System.out.println("a is" + ok);


  }


  /**
   * Saving just the jump probabilities
   */

  public void save(String filename) {
    int ii;
    try {
      PrintStream out = new PrintStream(new FileOutputStream(filename, true));
      //MAX_LENGTH
      out.println(MAX_LENGTH);

      for (int jump_1 = -MAX_FLDS; jump_1 <= MAX_FLDS - 1; jump_1++) {
        for (int jump = -MAX_LENGTH; jump <= MAX_LENGTH; jump++) {


          out.print(jump + " " + jump_1 + " " + this.getProbJump(jump, jump_1) + "\t");


        }//jump
        out.println();
      }

      //now the initial probs
      for (int jump = 0; jump <= MAX_LENGTH + 1; jump++) {


        out.print(jump + " " + getProbHMM(jump, 0, MAX_FLDS, MAX_LENGTH) + "\t");


      }//jump

      out.println();
      out.close();
    } catch (Exception e) {
      e.printStackTrace();
    }


  }


  /*
   * calculate the kl divergance between this table and a in terms of the
   * jump probabilities
   */
  public double DKL(ATableHMM a) {

    double p, q;
    double d = 0;

    return 0;


  }


  /**
   * reading the jump probabilities and initializing
   */


  public void read(String filename) {
    int ii, current;
    String line, token;
    StringTokenizer st;
    try {
      InFile in = new InFile(filename);
      //MAX_LENGTH
      MAX_LENGTH = (new Integer(in.readLine())).intValue();
      params = new float[2 * MAX_LENGTH + 1][2 * MAX_FLDS];
      counts = new float[2 * MAX_LENGTH + 1][2 * MAX_FLDS];
      initialCounts = new float[MAX_LENGTH + 2]; // from 1 to MAX_LENGTH+1 filed 0 is not used
      prob_arr = new ArrayN(MAX_LENGTH + 2, MAX_LENGTH + 1, 2 * MAX_FLDS, MAX_LENGTH + 1);
      pEmpty = PROB_EMPTY;


      for (int current_jump_prev = 0; current_jump_prev < 2 * MAX_FLDS; current_jump_prev++) {
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
    int ii;
    String line, token;
    try {
      InFile in = new InFile(filename);
      //MAX_LENGTH
      MAX_LENGTH = (new Integer(in.readLine())).intValue();
      params = new float[2 * MAX_LENGTH + 1][2 * MAX_FLDS];
      counts = new float[2 * MAX_LENGTH + 1][2 * MAX_FLDS];
      initialCounts = new float[MAX_LENGTH + 2]; // from 1 to MAX_LENGTH filed 0 is not used
      prob_arr = new ArrayN(MAX_LENGTH + 2, MAX_LENGTH + 1, 2 * MAX_FLDS, MAX_LENGTH + 1);
      pEmpty = PROB_EMPTY;

      line = in.readLine();
      StringTokenizer st;
      st = new StringTokenizer(line, " \t");
      token = st.nextToken();
      token = st.nextToken();

      counts[0][0] = (float) (new Double(token)).doubleValue();
      for (int jmp = 0; jmp < 2 * MAX_FLDS; jmp++) {
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

      for (int jmp = 0; jmp < 2 * MAX_FLDS; jmp++) {
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


}
