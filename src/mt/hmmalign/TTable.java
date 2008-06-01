package mt.hmmalign;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;


/** This class holds the translation table p(f_word|e_word).
 *  It is implemented as a HashMap. The keys are pairs of integer ids of words and the values
 *  are objects that hold 2 doubles - probability and count.
 *
 *  @author Kristina Toutanova (kristina@cs.stanford.edu)
 */
public class TTable {

  private HashMap<IntPair,ProbCountHolder> tMap;
  private DoubleIntHolder[] totals;

  private static final double PROB_CUTOFF = 1e-7;
  static final double PROB_SMOOTH = 1e-7;

  private SymbolTable sTableE;
  static int vSize;// this is for French vocabulary size
  boolean english;


  public TTable(boolean english) {
    tMap = new HashMap<IntPair, ProbCountHolder>();
    this.english = english;
    //maybe change that later
    if (english) {
      sTableE = SentenceHandler.sTableE;
    } else {
      sTableE = SentenceHandler.sTableF;
    }
    // we want to have sum 1 over the probabilities of the base ids ( just words)
    vSize = SentenceHandler.sTableF.getMaxSimpleIds();
  }


  public void add(IntPair wP, ProbCountHolder probC) {
    tMap.put(wP, probC);
  }

  public void insert(int e, int f, double prob, double count) {
    IntPair wp = new IntPair(e, f);
    ProbCountHolder pH = new ProbCountHolder(prob, count);
    tMap.put(wp, pH);

  }

  /* Returns the ProbCountHolder that corresponds to this
   * pair. Null if no correspondence in the map
   */

  public ProbCountHolder get(IntPair wP) {
    return tMap.get(wP);
  }


  public boolean erase(IntPair wP) {
    return (tMap.remove(wP) != null);
  }


  public Iterator<Map.Entry<IntPair, ProbCountHolder>> getIterator() {
    return tMap.entrySet().iterator();
  }


  public void incCount(IntPair wP, double cnt) {
    ProbCountHolder pH = get(wP);
    if (pH != null) {
      pH.incCount(cnt);
    } else {
      tMap.put(wP, new ProbCountHolder(0, cnt));
    }

  }


  public void incCount(IntPair wP, double cnt, boolean shallow) {
    ProbCountHolder pH = get(wP);
    if (pH != null) {
      pH.incCount(cnt);
    } else {
      IntPair wP1 = new IntPair(wP.getSource(), wP.getTarget());
      tMap.put(wP1, new ProbCountHolder(0, cnt));
    }

  }


  public double getProb(IntPair wP) {
    ProbCountHolder pH = get(wP);
    if (pH == null) {
      return PROB_SMOOTH;
    } else {
      return pH.getProb();
    }

  }

  /* Normalize the probability table
   * So the for every source word, the probabilities of the target words sum to 1
   * Also all words get probability >= PROB_CUTOFF
   */

  public void normalizeTable(int iteration) {
    totals = new DoubleIntHolder[sTableE.getNumAllIds()]; //will hold key Word value DoubleIntHolder
    if (iteration == 0) {
      System.out.println("Translation table has " + tMap.size() + " entries");
    }
    IntPair wP;
    ProbCountHolder pcH;
    DoubleIntHolder diH;
    int e;

    for (Map.Entry<IntPair,ProbCountHolder> eN : tMap.entrySet()) {
      wP = eN.getKey();
      pcH = eN.getValue();
      e = wP.getSource();
      diH = totals[e];
      if (diH == null) {
        diH = (totals[e] = new DoubleIntHolder());
      }
      diH.incCnt(1);
      diH.incVal(pcH.getCount());
      //System.out.println(e.getKey() + ": " + e.getValue());
    }


    //now the normalization, iterate again
    double total, probMass;

    //rescale the totals

    for (int i = 0; i < totals.length; i++) {
      diH = totals[i];
      if (diH != null) {
        probMass = (vSize - diH.getCount()) * PROB_SMOOTH;
        diH.incVal(diH.getValue() * probMass / (1 - probMass));
      }
    }

    boolean erased = false;
    for (Iterator<Map.Entry<IntPair, ProbCountHolder>> i = tMap.entrySet().iterator(); i.hasNext();) {
    	Map.Entry<IntPair, ProbCountHolder> eN = i.next();
      wP = eN.getKey();
      pcH = eN.getValue();
      e = wP.getSource();
      diH = totals[e];
      total = diH.getValue();
      double prob = pcH.getCount() / total;
      if (prob > PROB_CUTOFF) {

        if (iteration > 0) {
          pcH.setCount(prob);
          pcH.setProb(0);
        } else {
          pcH.setProb(prob);
          pcH.setCount(0);
        }

      } else {
        i.remove();
        erased = true;
      }

    }
    if (iteration > 0) {
      if (erased) {
        normalizeTable(iteration - 1);
      } else {
        swapProbCountTable();
        iteration = 0;
        //normalizeTable(0);
        System.out.println("Swapping table\n");
      }
    }
    //check the table is OK
    if (iteration == 0) {
      //collect counts again and check OK

      //System.out.println("Collectiong counts again\n");
      totals = new DoubleIntHolder[totals.length];
      for (Map.Entry<IntPair,ProbCountHolder> eN : tMap.entrySet()) {
        wP = eN.getKey();
        pcH = eN.getValue();
        e = wP.getSource();
        diH = totals[e];
        if (diH == null) {
          diH = (totals[e] = new DoubleIntHolder());
        }
        diH.incCnt(1);
        diH.incVal(pcH.getProb());
      }

      //check OK
      boolean okProbs = true;
      for (int i = 0; i < totals.length; i++) {
        diH = totals[i];
        if (diH != null) {
          probMass = (vSize - diH.getCount()) * PROB_SMOOTH;
          total = diH.getValue();
          //System.out.println("count is "+diH.getCount());
          //System.out.println("Total "+total+" mass "+probMass);
          //System.out.println("Ok Difference "+Math.abs(total+probMass-1));

          if (Math.abs(total + probMass - 1) > 1e-3) {
            okProbs = false;
            //System.out.println("Total "+total+" mass "+probMass);
            //System.out.println("Not Ok Difference "+Math.abs(total+probMass-1));
          }
        }
      }
      if (!okProbs) {
        System.err.println("Probabilities not Ok");
        System.exit(-1);
      }

    }//else iter is 0
  }


  public double getCondEntropy() {
    //just avg with equal prior on the Fs entropy
    double[] entropies = new double[sTableE.getNumAllIds()];
    totals = new DoubleIntHolder[sTableE.getNumAllIds()];
    int zeroW = 0;

    for (Map.Entry<IntPair,ProbCountHolder> eN : tMap.entrySet()) {
      IntPair wP = eN.getKey();
      ProbCountHolder pcH = eN.getValue();
      int e = wP.getSource();
      DoubleIntHolder diH = totals[e];
      if (diH == null) {
        diH = (totals[e] = new DoubleIntHolder());
      }
      diH.incCnt(1);
      entropies[e] += pcH.getProb() * Math.log(pcH.getProb());
    }

    double unif = 1 / (double) vSize;
    double fixedLow = -unif * Math.log(unif) * vSize;
    double unitLow = -PROB_SMOOTH * Math.log(PROB_SMOOTH);
    System.out.println("Fixed low is " + fixedLow);
    double total = 0;
    for (int e = 0; e < entropies.length; e++) {
      if (entropies[e] == 0) {
        entropies[e] = fixedLow;
        zeroW++;
      } else {
        int cnt1 = totals[e].getCount();
        int cnt = vSize - cnt1;
        entropies[e] = -entropies[e] + unitLow * cnt;
      }

      total += entropies[e];
      if (e == 0) {
        System.out.println("Entropy of empty " + entropies[e] / Math.log(2));
      }
    }

    System.out.println("Number of words without correspondences " + zeroW);
    total = total / vSize * Math.log(2);
    return total;
  }


  void swapProbCountTable() {
    for (Map.Entry<IntPair,ProbCountHolder> eN : tMap.entrySet()) {
      ProbCountHolder pcH = eN.getValue();
      pcH.swap();
    }
  }


  /**
   * save to a file with integer ids
   */

  public void save(String filename) {

    try {
      PrintStream p = new PrintStream(new FileOutputStream(filename, true));

      for (Map.Entry<IntPair,ProbCountHolder> eN : tMap.entrySet()) {
        ProbCountHolder pcH = eN.getValue();
        IntPair iP = eN.getKey();
        p.println(iP.getTarget() + " " + iP.getSource() + " " + pcH.getProb());
      }

      p.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  /**
   * Read state from a file.
   *
   * @param filename Filename to read from
   */
  public void read(String filename) {
    try {
      InFile in = new InFile(filename);

      for (String line ; (line = in.readLine()) != null; ) {
        StringTokenizer st = new StringTokenizer(line);
        int f = Integer.parseInt(st.nextToken());
        int e = Integer.parseInt(st.nextToken());
        double p = Double.parseDouble(st.nextToken());
        this.insert(e, f, p, 0);
      }

      in.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  /**
   * Save to a file with the words rather than ids.
   *
   * @param filename Filename to write to
   */

  public void saveNames(String filename) {

    try {
      PrintStream p = new PrintStream(new FileOutputStream(filename, true));

      IntPair iP = new IntPair();
      int max = SentenceHandler.sTableE.getNumAllIds();
      for (int index = 0; index < max; index++) {

        iP.setSource(index);
        for (int fr = 0; fr < vSize; fr++) {
          iP.setTarget(fr);
          ProbCountHolder pcH = get(iP);
          if (pcH == null) {
            continue;
          }
          p.println(SentenceHandler.sTableF.getName(iP.getTarget()) + " " + sTableE.getName(iP.getSource()) + " " + pcH.getProb());
        }
      }

      p.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  public void print() {
    Object[] keys = tMap.keySet().toArray();
    for (int i = 0; i < keys.length; i++) {
      IntPair wP = (IntPair) keys[i];
      wP.print();
      ProbCountHolder h = get(wP);
      System.out.println(h.getCount() + " " + h.getProb());
      System.out.println();
    }
  }

}
