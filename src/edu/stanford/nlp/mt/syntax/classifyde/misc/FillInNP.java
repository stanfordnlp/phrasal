package edu.stanford.nlp.mt.syntax.classifyde.misc;

import java.util.*;

import edu.stanford.nlp.io.IOUtils;

public class FillInNP {
  static Map<Integer, Map<Integer, NP>> linesToNPs(String[] lines) {
    Map<Integer, Map<Integer, NP>> nps = new TreeMap<Integer, Map<Integer, NP>>();

    for(String line : lines) {
      String[] toks = line.split("\\t");
      String en;
      if (toks.length == 4) {
        en = "";
      } else if (toks.length == 5) {
        en = toks[4];
      } else
        throw new RuntimeException("#toks="+toks.length+"\t"+line);
      NP theNP = new NP(toks[3], en, toks[2]);
      int fileid = Integer.parseInt(toks[0]);
      int npid = Integer.parseInt(toks[1]);
      Map<Integer, NP> np = nps.get(fileid);
      if (np==null) np = new TreeMap<Integer, NP>();
      np.put(npid, theNP);
      nps.put(fileid, np);
    }
    return nps;
  }

  /**
   * Takes two files (NPs.txt and all, currently in ~pichuan/javanlp/) and
   * make a new list of NPs.
   * Then I annotate the "new" ones myself and get a new file:
   * project/mt/src/mt/translationtreebank/data/finalCategories_all.txt
   */
  public static void main(String[] args) throws Exception {
    String[] lines_NPs = IOUtils.slurpFileNoExceptions("NPs.txt").split("\\n");
    String[] lines_all = IOUtils.slurpFileNoExceptions("all").split("\\n");
    Map<Integer, Map<Integer, NP>> nps_all = linesToNPs(lines_all);
    Map<Integer, Map<Integer, NP>> nps_NPs = linesToNPs(lines_NPs);

    for(int fileid : nps_NPs.keySet()) {
      Map<Integer, NP> np_NPs = nps_NPs.get(fileid);
      for(int npid : np_NPs.keySet()) {
        NP np = np_NPs.get(npid);
        System.err.println("NP:\t"+fileid+"\t"+npid+"\t"+np.type+"\t"+np.ch+"\t"+np.en);
      }
    }

    // go through each one in nps_all, find corresponding info in nps_NPs
    // If not found : output info from nps_all, newCounter++
    // If found     : use info from nps_NPs
    for(int fileid : nps_all.keySet()) {
      Map<Integer, NP> np_all = nps_all.get(fileid);
      Map<Integer, NP> np_NPs = nps_NPs.get(fileid);
      if (np_NPs==null) {
        // output info from nps_all
        System.err.println("not in NPs:"+fileid);
        for(int npid : np_all.keySet()) {
          NP np = np_all.get(npid);
          System.out.println("new"+fileid+"\t"+npid+"\t"+np.type+"\t"+np.ch+"\t"+np.en);
        }
      } else {
        int limitNPs = Collections.max(np_NPs.keySet());
        int ptr_np = 0;
        NP npN = null;
        for(int npid : np_all.keySet()) {
          NP np = np_all.get(npid);
          while (npN == null && ptr_np <= limitNPs) {
            npN = np_NPs.get(ptr_np);
            if (npN != null) 
              System.err.println("ptr_np="+ptr_np+", "+npN.ch);
            else 
              System.err.println("ptr_np="+ptr_np+", NULL");
            ptr_np++;
          }

          if (npN != null && npN.ch.equals(np.ch)) {
            System.out.println("NPs"+"\t"+fileid+"\t"+npid+"\t"+npN.type+"\t"+npN.ch+"\t"+npN.en);
            System.err.println("matched! ptr_np="+ptr_np+", npid="+npid+", "+npN.ch); 
            npN = null; // matched away
          } else {
            System.out.println("new"+"\t"+fileid+"\t"+npid+"\t"+np.type+"\t"+np.ch+"\t"+np.en);
            if (npN==null)
              System.err.println("can't find "+np.ch+" != NULL");
            else 
              System.err.println("can't find "+np.ch+" != "+npN.ch);
          }
        }
      }
    }
    


    System.err.println(lines_NPs.length);
    System.err.println(lines_all.length);
  }
}

class NP {
  String ch;
  String en;
  String type;
  public NP(String ch, String en, String type) {
    this.ch = ch;
    this.en = en;
    this.type = type;
  }
}
