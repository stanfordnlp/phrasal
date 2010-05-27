package edu.stanford.nlp.mt.train;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;

import edu.stanford.nlp.mt.base.IBMModel1;
import edu.stanford.nlp.mt.base.IString;

/**
 * 
 * @author Pi-Chuan Chang
 */

public class ShortestDistanceWordAligner implements WordAligner {

  private static final boolean REPORT = true;

  public static int MAXSTEP = 2;

  public static int INTERSECTED = 0;
  //public static int[] STEPINTERSECTED = new int[MAXSTEP];
  public static int APPROXFOUND=0;
  public static int NOT=0;
  public static int COMMA = 0;
  public static int TOTAL = 0;

  public static final int RANGE = 2;

  IBMModel1 model1 = null;

  /**
   * constructor with a hard-coded IBM model 1:
   * "/scr/nlp/data/gale2/acl08dd/resources/model1/zh_en.model.actual.t1"
   */
  public ShortestDistanceWordAligner() {
    this("/scr/nlp/data/gale2/acl08dd/resources/model1/zh_en.model.actual.t1");
  }


  /**
   * @param filename the model 1 file
   **/
  public ShortestDistanceWordAligner(String filename) {
    try {
      long startTimeMillis = System.currentTimeMillis();
      System.err.println("loading IBM Model 1 for ShortestDistanceWordAligner from file: "+filename);
      model1 = IBMModel1.load(filename);
      double totalSecs = (System.currentTimeMillis() - startTimeMillis)/1000.0;
      System.err.printf("done loading IBM Model. Time = %.3f secs\n", totalSecs);
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("IBMModel1 generated an exception");
    }
  }

  @SuppressWarnings("unused")
  public ShortestDistanceWordAligner(IBMModel1 model) {
    this.model1 = model;
  }

  /**
   * @param sp Contains information of the foreign sentence, the English sentence, and the GIZA++ style alignment
   * @param foreignIdx the index of the foreign word that we want a specific alignment to
   * @return the index of the particular English word this foreign word (with index foreignIdx) is aligned to. If return -1, no good word alignment is produced
   */
  public int getAlignedEnglishIndex(WordAlignment sp, int foreignIdx) {
    if (!(sp instanceof DualWordAlignment))
      throw new RuntimeException
        ("in ShortestDistanceWordAligner: sp should be an instance of DualWordAlignment.");

    TOTAL++;
    
    // "f2e" is the intersect one
    // "e2f" is whatever the second one, (probably grow-diag)

    IString is = sp.f().get(foreignIdx);
    // We don't want commas
    if (is.toString().equals(",")) {
      COMMA++;
      return -1;
    }


    if (REPORT) {
      System.out.println("---------------------------------------------------------------");
      System.out.println("For Sentence Pair:");
      System.out.println("  "+sp.f());
      System.out.println("  "+sp.e());
    }

    int currIntersectedEnglishIdx = getIntersectedEnglishIdx(sp, foreignIdx);
    // if it's already aligned in the intersection
    if (currIntersectedEnglishIdx != -1) {
      if (REPORT) {
        System.out.println(" "+foreignIdx+" INTERSECT to "+currIntersectedEnglishIdx);
      }
      INTERSECTED++;
      return currIntersectedEnglishIdx;
    }

    //int maxStep = Math.max(Math.abs(sp.f().size()-foreignIdx-1), Math.abs(foreign));
    for(int step=1; step <= MAXSTEP; step++) {
      int intersectNeighorCounts = 0;
      int intersectIndiceSum = 0;
      
      int prevIntersectedEnglishIdx = getIntersectedEnglishIdx(sp, foreignIdx-step);
      int nextIntersectedEnglishIdx = getIntersectedEnglishIdx(sp, foreignIdx+step);

      if (prevIntersectedEnglishIdx != -1) {
        if (REPORT) System.out.println(" "+foreignIdx+" prev INTERSECT to "+prevIntersectedEnglishIdx);
        intersectNeighorCounts++;
        intersectIndiceSum += prevIntersectedEnglishIdx;
      }
      if (nextIntersectedEnglishIdx != -1) {
        if (REPORT) System.out.println(" "+foreignIdx+" next INTERSECT to "+nextIntersectedEnglishIdx);
        intersectNeighorCounts++;
        intersectIndiceSum += nextIntersectedEnglishIdx;
      }
      if (intersectNeighorCounts > 0) {
        double avgIdx = (double)intersectIndiceSum/intersectNeighorCounts;
        if (REPORT) {
          System.out.printf(" %d find one idx close to %d/%d=%.3f\n", 
                            foreignIdx,
                            intersectIndiceSum, intersectNeighorCounts,
                            avgIdx);
        }
        //STEPINTERSECTED[step-1]++;
        // good enough. Let's break
        int appEidx = getClosestGrowDiagEnglishIdx(sp, foreignIdx, (int)avgIdx);
        if (appEidx != -1) {
          if (REPORT) System.out.printf(" -> found: %d\n",appEidx); 
          APPROXFOUND++; 
          return appEidx;
        }
        //found = true;
        //break;
      }
    }

    //if (!found) 
    NOT++;
    return -1;
  }

  private int getIntersectedEnglishIdx(WordAlignment sp, int fidx) {
    // out of boundary
    if (fidx < 0 || fidx >= sp.f().size()) {
      return -1;
    }
    
    // no COMMAs!
    IString is = sp.f().get(fidx);
    if (is.toString().equals(",")) {
      return -1;
    }
    
    Set<Integer> intersect = sp.f2e(fidx);
    if (intersect.size() == 1) {
      // yay! Let's get this aligned..
      Object[] a = intersect.toArray();
      return (Integer)(a[0]);
    } else if (intersect.size() == 0) {
      return -1;
    } else {
      throw new RuntimeException
        ("the 1st align file should be intersection. Now it has an alignment set of size = "+intersect.size());
    }
  }

  private int getClosestGrowDiagEnglishIdx(WordAlignment sp, int fidx, int approxEid) {
    // out of boundary
    if (fidx < 0 || fidx >= sp.f().size()) {
      return -1;
    }
    
    // no COMMAs!
    IString is = sp.f().get(fidx);
    if (is.toString().equals(",")) {
      return -1;
    }

    Set<Integer> growdiag = sp.e2f(fidx); // "e2f" stores GrowDiag alignment
    // get the word with the highest model 1 score in the range of [approxEid-2, approxEid+2]

    List<Integer> inRange = new ArrayList<Integer>();

    if (growdiag.contains(approxEid))
      inRange.add(approxEid);
    for(int r = 1; r <= RANGE; r++) {
      if (growdiag.contains(approxEid+r))
        inRange.add(approxEid+r);
      if (growdiag.contains(approxEid-r))
        inRange.add(approxEid-r);
    }
    if (REPORT) {
      System.out.println("  trying to find words with highest IBMmodel1 in "+inRange);
    }
    
    if (inRange.size()==1) {
      return inRange.get(0);
    }
    
    int maxIdx = -1;
    double maxScore = Double.MIN_VALUE;
    for(int idxInRange : inRange) {
      IString enWord = sp.e().get(idxInRange);
      IString chWord = sp.f().get(fidx);
      double score = model1.score(chWord, enWord);
      System.out.printf("model1.score(%s, %s)=%.3f\n", chWord, enWord, score);
      if (score > maxScore) {
        maxScore = score;
        maxIdx = idxInRange;
      }
    }
    if (REPORT)
      System.out.printf(" End up aligning to %d (approxEid+(%d))\n", maxIdx, maxIdx-approxEid);
    return maxIdx;
    //return -1;
  }
}
