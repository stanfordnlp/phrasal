package edu.stanford.nlp.mt.train.hmmalign;


/**
 * Serves to keep the stay and go probabilities for words
 * I am keeping it as an array
 * For each English word - 2 ProbCountHolders at indexes 2k (go) and 2k+1 (stay)
 *@author Kristina Toutanova (kristina@cs.stanford.edu)
 */

import java.io.FileOutputStream;
import java.io.PrintStream;

public class StayGoTable {
  private ProbCountHolder[] table;

  public StayGoTable(SentenceHandler sH) {
    this.table = new ProbCountHolder[2 * SentenceHandler.sTableE.getNumAllIds()];
  }


  public ProbCountHolder getEntryStay(int index) {
    return table[2 * index + 1];

  }


  public ProbCountHolder getEntryGo(int index) {
    return table[2 * index];

  }


  public double getProbStay(int index) {
    return table[2 * index + 1].getProb();

  }


  public double getProbGo(int index) {
    return table[2 * index].getProb();

  }


  public void incCountStay(int index, double val) {

    table[2 * index + 1].incCount(val);

  }


  public void incCountGo(int index, double val) {

    table[2 * index].incCount(val);

  }


  /**
   * Initialize the table with some given value of stay
   */
  public void initialize(double pstay) {

    int bound = table.length / 2;

    for (int index = 0; index < bound; index++) {

      table[2 * index] = new ProbCountHolder();
      table[2 * index + 1] = new ProbCountHolder();
      table[2 * index].setProb(1 - pstay);
      table[2 * index + 1].setProb(pstay);

    }


  }


  public void normalize() {
    int bound = table.length / 2;
    double total = 0;
    double pstay, pgo;
    int countCutoff = 3;
    double priorGo = 0;
    double priorStay = 0;

    for (int index = 0; index < bound; index++) {
      total = 0;
      total += priorGo + (pgo = table[2 * index].getCount());
      total += priorStay + (pstay = table[2 * index + 1].getCount());
      if (total == priorGo + priorStay) {
        continue;
      }
      System.out.println("cnt go " + table[2 * index].getCount());
      System.out.println("cnt stay " + table[2 * index + 1].getCount());
      System.out.println("cnt word " + SentenceHandler.sTableE.getEntry(index).getCount());
      if (SentenceHandler.sTableE.getEntry(index).getCount() > countCutoff) {
        table[2 * index].setProb(.8 + .2 * (pgo + priorGo) / total);
        table[2 * index + 1].setProb(.2 * (pstay + priorStay) / total);
      }
      table[2 * index].setCount(0);
      table[2 * index + 1].setCount(0);
      //if(Math.abs(pgo/total+pstay/total-1)>.00001){System.out.println("probabilities stay go
      //not ok ");}

    }


  }

  public void save(String filename) {

    try {
      PrintStream p = new PrintStream(new FileOutputStream(filename, true));

      int bound = table.length / 2;
      for (int index = 0; index < bound; index++) {
        p.println(SentenceHandler.sTableE.getName(index) + " " + table[2 * index].getProb() + " " + table[2 * index + 1].getProb());

      }


      p.close();
    } catch (Exception e) {
      e.printStackTrace();
    }

  }


}
