package mt.translationtreebank.misc;

import java.util.*;
import java.io.*;

class NPdata {
  String autocat;
  String frags;
  String chNP;
  String entrans;
  String handcat = null;
  String finalcat = null;
  
  public NPdata(String autocat, String frags, String chNP, String entrans) {
    this.autocat = autocat;
    this.frags = frags;
    this.chNP = chNP;
    this.entrans = entrans;
  }
}

class LabelNewData {
  public static void main(String[] args) throws IOException{
    BufferedReader br = new BufferedReader(new FileReader("newNPs.txt"));
    String line;

    Map<String, Map<String, NPdata>> newNPs = new TreeMap<String, Map<String, NPdata>>();

    while((line=br.readLine())!=null) {
      //System.out.println(line);
      String[] data = line.split("\t");

      String sentid, npid, autocat, frags, chNP, entrans;
      if (data.length==5) {
        sentid = data[0];
        npid   = data[1];
        autocat= data[2];
        frags  = data[3];
        chNP   = data[4];
        entrans= "";
        if (!frags.equals("0")) throw new RuntimeException(line);
      } else if (data.length==6) {
        sentid = data[0];
        npid   = data[1];
        autocat= data[2];
        frags  = data[3];
        chNP   = data[4];
        entrans= data[5];
      } else {
        throw new RuntimeException(line);
      }
      Map<String, NPdata> sentNPs = newNPs.get(sentid);
      if (sentNPs == null) {
        sentNPs = new TreeMap<String, NPdata>();
        newNPs.put(sentid, sentNPs);
      }
      
      NPdata npdata = sentNPs.get(npid);
      if (npdata != null) {
        throw new RuntimeException("multiple ids: "+line);
      }

      npdata = new NPdata(autocat, frags, chNP, entrans);
      sentNPs.put(npid, npdata);
      newNPs.put(sentid, sentNPs);
      
    }

    // (2) now, read in oldNP and label new NP with it
    br = new BufferedReader(new FileReader("oldNPs.txt"));

    while((line=br.readLine())!=null) {
      String[] data = line.split("\t");
      String sentid;
      String autocat;
      String handcat;
      String finalcat;
      String frags;
      String chNP;
      String entrans;

      if (data.length==8) {
        sentid = data[0];
        autocat = data[2];
        handcat = data[3];
        finalcat = data[4];
        frags = data[5];
        chNP = data[6];
        entrans = data[7];
      } else if (data.length==7) {
        sentid = data[0];
        autocat = data[2];
        handcat = data[3];
        finalcat = data[4];
        frags = data[5];
        chNP = data[6];
        entrans = "";
      } else {
        throw new RuntimeException("data length error: "+line);
      }
      
      Map<String, NPdata> sentNPs = newNPs.get(sentid);
      if (sentNPs != null) {
        for (Map.Entry<String, NPdata> e : sentNPs.entrySet()) {
          NPdata npdata = e.getValue();
          if (npdata.chNP.equals(chNP)) {
            if (!npdata.entrans.equals(entrans)) System.err.println("ch matched but not en: "+line+"\n-----------------");
            if (!npdata.autocat.equals(autocat)) System.err.println("autocat not matched: "+line+"\n-----------------");
            npdata.handcat = handcat;
            npdata.finalcat = finalcat;
            sentNPs.put(e.getKey(), npdata);
          }
        }
      }
    }

    // (3) check how many are not labeled...
    int totalCount = 0;
    int unlabeled = 0;
    for(Map.Entry<String, Map<String, NPdata>> e : newNPs.entrySet()) {
      String sentid = e.getKey();
      Map<String, NPdata> sentNPs = e.getValue();
      for(Map.Entry<String, NPdata> e2 : sentNPs.entrySet()) {
        NPdata npdata = e2.getValue();
        if (npdata.finalcat == null) {
          // try to fix it first
          if (npdata.autocat.startsWith("A B")) {npdata.handcat = ""; npdata.finalcat = "A B";}
          if (npdata.autocat.startsWith("no B")) {npdata.handcat = ""; npdata.finalcat = "no B";}
          if (npdata.autocat.startsWith("other")) {npdata.handcat = ""; npdata.finalcat = "other";}
          if (npdata.autocat.startsWith("relative clause")) {npdata.handcat = ""; npdata.finalcat = "relative clause";}
          if (npdata.autocat.startsWith("B of A")) {npdata.handcat = ""; npdata.finalcat = "B of A";}
          if (npdata.autocat.startsWith("B prep A")) {npdata.handcat = ""; npdata.finalcat = "B prep A";}
        }

        if (npdata.finalcat == null) { unlabeled++; }
        totalCount++;

        // print out stuff...
        print(sentid, e2.getKey(), npdata);
      }
    }
    System.err.println("totalCount = "+totalCount);
    System.err.println("unlabeled = "+unlabeled);
  }

  static void print(String sentid, String npid, NPdata npdata) {
    System.out.print(sentid);
    System.out.print("\t");
    System.out.print(npid);
    System.out.print("\t");
    System.out.print(npdata.autocat);
    System.out.print("\t");
    if (npdata.handcat!=null) System.out.print(npdata.handcat); else System.out.print("");
    System.out.print("\t");
    if (npdata.finalcat!=null) System.out.print(npdata.finalcat); else System.out.print("");
    System.out.print("\t");
    System.out.print(npdata.frags);
    System.out.print("\t");
    System.out.print(npdata.chNP);
    System.out.print("\t");
    System.out.print(npdata.entrans);
    System.out.println();
  }
}
