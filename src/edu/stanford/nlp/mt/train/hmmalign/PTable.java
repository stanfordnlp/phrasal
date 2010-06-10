package edu.stanford.nlp.mt.train.hmmalign;


/* This table encodes probabilities for the
 * production of words by some word
 *@author Kristina Toutanova (kristina@cs.stanford.edu)
 */

import java.io.FileOutputStream;
import java.io.PrintStream;

public class PTable {

  /* will keep choose probabilities for each word for now
   */
  private ProbCountHolder[] table;

  public PTable(SentenceHandler sH) {
    this.table = new ProbCountHolder[SentenceHandler.sTableE.getNumAllIds()];
  }


  public ProbCountHolder getEntryChoose(int index) {
    return table[index];

  }


  public double getProbChoose(int index) {
    return table[index].getProb();

  }


  public void incCountChoose(int index, double val) {

    table[index].incCount(val);

  }


  /**
   * Initialize the table with some given value of probability of choose
   */
  public void initialize(double pch) {


    int bound = table.length;

    for (int index = 0; index < bound; index++) {

      table[index] = new ProbCountHolder();

    }


  }


  public void normalize() {
    int bound = table.length;
    double total = 0;

    for (int index = 0; index < bound; index++) {
      total += table[index].getCount();

    }

    for (int index = 0; index < bound; index++) {
      table[index].setProb(table[index].getCount() / total);
      table[index].setCount(0);
    }


  }

  public void save(String filename) {

    try {
      PrintStream p = new PrintStream(new FileOutputStream(filename, true));

      int bound = table.length;
      for (int index = 0; index < bound; index++) {
        p.println(SentenceHandler.sTableE.getName(index) + " " + table[index].getProb());

      }


      p.close();
    } catch (Exception e) {
      e.printStackTrace();
    }

  }


}
