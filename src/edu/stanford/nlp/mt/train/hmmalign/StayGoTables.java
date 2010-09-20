package edu.stanford.nlp.mt.train.hmmalign;

/**
 * stores several stay go tables for different contexts
 * 
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */
public class StayGoTables {

  StayGoTable[] arr;
  int numTables;

  public StayGoTables(int numTables, SentenceHandler sH) {
    this.numTables = numTables;
    arr = new StayGoTable[numTables];
    for (int i = 0; i < numTables; i++) {
      arr[i] = new StayGoTable(sH);

    }

  }

  public void initialize(double pstay) {
    for (int i = 0; i < numTables; i++) {
      arr[i].initialize(pstay);
    }

  }

  public void normalize() {

    System.out.println("normalizing ... ");
    for (int i = 0; i < numTables; i++) {
      arr[i].normalize();
    }
  }

  public void save(String filename) {

    for (int i = 0; i < numTables; i++) {

      arr[i].save(filename);

    }

  }

  public ProbCountHolder getEntryStay(int index, int jump) {

    int i = getMap(jump);
    return arr[i].getEntryStay(index);

  }

  public ProbCountHolder getEntryGo(int index, int jump) {
    int i = getMap(jump);
    return arr[i].getEntryGo(index);

  }

  public double getProbStay(int index, int jump) {
    int i = getMap(jump);
    return arr[i].getProbStay(index);
  }

  public double getProbGo(int index, int jump) {

    int i = getMap(jump);
    return arr[i].getProbGo(index);

  }

  public void incCountStay(int index, double val, int jump) {

    int i = getMap(jump);
    // System.out.println(" incrementing stay for "+index + " value "+val);
    arr[i].incCountStay(index, val);

  }

  public void incCountGo(int index, double val, int jump) {

    int i = getMap(jump);

    // System.out.println(" incrementing go for "+index + " value "+val);
    arr[i].incCountGo(index, val);

  }

  public int getMap(int jump) {

    if ((jump == ATableHMM2EQ.MAX_FLDS) || (jump == 0)) {
      return 0;
    } else {
      return 1;
    }

  }

}
