package mt.hmmalign;

/**
 * The purpose of this class is to hold separate ATableHMMs for different contexts. With
 * the idea to have a context of 2 French tags, I am implementing the key as an IntPair.
 *@author Kristina Toutanova (kristina@cs.stanford.edu)
 */

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ATableHMMHolder {
  private HashMap tables;
  private ATable uniform;
  private ATable smoothTable; // this table is optionally used for smoothing all other tables
  boolean smooth = true;
  int mask;

  public ATableHMMHolder() {
    tables = new HashMap();
  }


  public ATableHMMHolder(int mask) {
    tables = new HashMap();
    this.mask = mask;
  }


  public ATable getSmoothTable() {
    return smoothTable;
  }

  public void setUniform(ATable u) {
    uniform = u;
    System.out.println("Just set uniform");
    //uniform.printProbs();
  }


  public void setSmoothing(ATable u) {
    smoothTable = u;

  }


  public ATable getUniform() {

    //if(smooth){return smoothTable;}
    return uniform;

  }

  public void add(IntTuple wP, ATable t) {
    tables.put(wP, t);

  }

  public ATable get(IntTuple wP) {
    ATable u = (ATable) tables.get(wP);
    if (u == null) {

      if (GlobalParams.verbose) {
        wP.print();
        System.out.println("not found");
      }

      return uniform;
    } else {
      return u;
    }
  }


  public void printProbs() {

    ATable aT;
    IntTuple wP;

    System.out.println("**** uniform ****");
    uniform.printProbs();

    if (smooth) {

      System.out.println("*** smoothing ****");
      smoothTable.printProbs();
    }

    for (Iterator i = tables.entrySet().iterator(); i.hasNext();) {
      Map.Entry eN = (Map.Entry) i.next();
      wP = (IntTuple) eN.getKey();
      wP.print();
      aT = (ATable) eN.getValue();
      aT.printProbs();
    }


  }


  public void clearInfrequent() {

    ATable aT;


    for (Iterator i = tables.entrySet().iterator(); i.hasNext();) {
      Map.Entry eN = (Map.Entry) i.next();
      aT = (ATable) eN.getValue();
      if (!aT.isPopulated()) {
        //remove aT
        i.remove();

      }
    }


  }


  /**
   * Save all alignment probabilities to a file
   */
  public void save(String filename) {

    ATable aT;
    IntTuple wP;

    try {
      PrintStream p = new PrintStream(new FileOutputStream(filename, true));

      p.println("uniform");
      p.flush();
      uniform.save(filename);

      if (smooth) {
        p.println("smoothing");
        p.flush();
        smoothTable.save(filename);

      }

      for (Iterator i = tables.entrySet().iterator(); i.hasNext();) {
        Map.Entry eN = (Map.Entry) i.next();
        wP = (IntTuple) eN.getKey();
        p.println(wP.toString());
        p.flush();
        aT = (ATable) eN.getValue();
        //aT.save(filename);
      }


    } catch (Exception e) {
      e.printStackTrace();
    }

  }


  /**
   * Save all alignment probabilities to a file with the words rather than their Ids
   */
  public void saveNames(String filename) {

    ATable aT;
    IntTuple wP;

    try {
      PrintStream p = new PrintStream(new FileOutputStream(filename, true));

      p.println("uniform");
      p.flush();
      uniform.save(filename);

      if (smooth) {
        p.println("smoothing");
        p.flush();
        smoothTable.save(filename);

      }

      for (Iterator i = tables.entrySet().iterator(); i.hasNext();) {
        Map.Entry eN = (Map.Entry) i.next();
        wP = (IntTuple) eN.getKey();
        p.println(wP.toNameStringE());
        p.flush();
        aT = (ATable) eN.getValue();
        aT.save(filename);
      }


    } catch (Exception e) {
      e.printStackTrace();
    }

  }


  /**
   * Save all alignment probabilities to a file with the words rather than their Ids
   */
  public void saveNames(int mask, String filename) {

    ATable aT;
    IntTuple wP;

    try {
      PrintStream p = new PrintStream(new FileOutputStream(filename, true));

      p.println("uniform");
      p.flush();
      uniform.save(filename);

      if (smooth) {
        p.println("smoothing");
        p.flush();
        smoothTable.save(filename);

      }

      for (Iterator i = tables.entrySet().iterator(); i.hasNext();) {
        Map.Entry eN = (Map.Entry) i.next();
        wP = (IntTuple) eN.getKey();
        p.println(wP.toNameString(mask));
        p.flush();
        aT = (ATable) eN.getValue();
        aT.save(filename);
      }


    } catch (Exception e) {
      e.printStackTrace();
    }

  }


  public void normalize() {

    ATable aT;
    ATable haT, un;

    un = uniform;
    if (GlobalParams.verbose) {
      System.out.println("Normalizing uniform ");
    }
    uniform.normalize();
    un.checkOK();

    if (smooth) {

      smoothTable.normalize();
      System.out.println("Normzliging smooth");
      smoothTable.checkOK();
    }

    for (Iterator i = tables.entrySet().iterator(); i.hasNext();) {
      Map.Entry eN = (Map.Entry) i.next();
      aT = (ATable) eN.getValue();
      if (GlobalParams.verbose || true) {
        System.out.println(((IntTuple) eN.getKey()).toNameString(mask));
      }
      aT.normalize();
      if (!aT.checkOK()) {
        aT.printProbs();
        System.exit(0);
      }
      haT = aT;
      double d = un.DKL(haT);
      System.out.println("Divergence " + d);
      //if(d<.20){i.remove();}
    }


  }//normalize


}
