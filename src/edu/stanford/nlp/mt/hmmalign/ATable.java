package edu.stanford.nlp.mt.hmmalign;

/**
 * This is a base class for aligment tables
 *@author Kristina Toutanova (kristina@cs.stanford.edu)
 */

import java.io.FileOutputStream;
import java.io.PrintStream;

public class ATable {

  int count; // this is used when we have a lot of these tables for different contexts and we want to keep only the ones with enough support
  static int countCutoff = 150;
  int MAX_LENGTH;//this is the maximum allowable sentence length
  boolean fixEmpty = true;
  String name = "none";

  public ATable() {
  }


  public ATable(int maxsize) {
    this.MAX_LENGTH = maxsize;
  }

  public void normalize() {
  };

  public void initializeUniform() {
  };

  public void initialize(ATable a) {
  };

  public void printProbs() {
  };
  public void incCount(int inc) {
    count += inc;
  }

  public int getCount() {
    return count;
  }

  public boolean checkOK() {
    return true;
  }

  public float getProb(int i, int i_p, int l) {
    return 0;
  }

  public float getProb(int i, int i_p, int i_pp, int l) {
    return 0;
  }

  public void incCount(int i, int i_p, int l, double val) {
  }

  public void incCount(int i, int i_p, int i_pp, int l, double val) {
  }

  public boolean isPopulated() {
    return true;
  }

  public float getEmpty() {
    return 0;
  }


  /**
   * Actually appends to file.  Used when we want to print all tables for
   * e.g. different contexts.
   */
  public void save(String filename) {
    int ii;
    try {
      PrintStream out = new PrintStream(new FileOutputStream(filename, true));
      //MAX_LENGTH
      out.println(MAX_LENGTH);


      for (int l = 1; l <= MAX_LENGTH; l++) {

        for (int i_p = 0; i_p <= l; i_p++) {

          for (int i = 0; i <= l; i++) {
            if ((i == 0) && (i_p > 0)) {
              ii = i_p + l;
            } else {
              ii = i;
            }
            out.println(i + " " + i_p + " " + l + " " + getProb(ii, i_p, l));

          }//i

        }//i_p


      }//l

      out.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  public double DKL(ATable a) {
    return 2;
  }

}
