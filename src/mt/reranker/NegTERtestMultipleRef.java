package mt.reranker;

import java.io.*;
import java.util.*;

import mt.metrics.ter.*;

/**
 * @author Pi-Chuan Chang
 */
public class NegTERtestMultipleRef {

  private static final TERcalc ter = new TERcalc();

  static double computeTER(String hyp, List<String> refs) {
    System.err.println("Compute '"+hyp+"' vs.");
    double minEdits = Double.MAX_VALUE;
    double sumlen = 0.0;
    for(int i = 0; i < refs.size(); i++) {
      TERalignment result = ter.TER(hyp, refs.get(i));
      System.err.println("\t"+refs.get(i));
      minEdits = Math.min(result.numEdits, minEdits);
      sumlen += result.numWords;
    }
    double avglen = sumlen/refs.size();
    return minEdits / avglen;
  }

  public static void main(String[] args) {
    if (args.length < 2) {
      System.err.println("usage: java NegTERtestMultipleRef <hypfile> <reffile1> <reffile2> ...");
      System.exit(-1);
    }

    BufferedReader hypstream;
    List<BufferedReader> refstreams = new ArrayList<BufferedReader>();

    try {
      hypstream = new BufferedReader(new FileReader (args[0]));
      for (int i=1; i < args.length; i++) {
        refstreams.add(new BufferedReader(new FileReader(args[i])));
      }
    } catch(IOException ioe) {
      System.out.println(ioe);
      return;
    }

    try {
      String hyp;
      Map<Integer,List<String>> refs = new HashMap<Integer, List<String>>();

      //refs = new ArrayList<List<String>>();
      for (BufferedReader refstream : refstreams) {
        int i = 0;
        String ref = null;
        while((ref = refstream.readLine())!=null) {
          List<String> refl = refs.get(i);
          if (refl==null) {
            refl = new ArrayList<String>();
          }
          refl.add(ref);
          refs.put(i,refl);
          i++;
        }
      }

      while((hyp = hypstream.readLine())!=null) {
        String[] fields = hyp.split("\\t");
        if (fields.length != 2) {
          throw new RuntimeException
            ("format of nbest lists file: sentId and hypId should be separated from the sentence using a TAB.\n");
        }
        String[] sIdPair = fields[0].split(",");
        int dataPt = Integer.parseInt(sIdPair[0]);

        List<String> refl = refs.get(dataPt);
        double ter = computeTER(fields[1], refl);
        System.out.println(fields[0]+"\t"+-ter);

      }
    } catch(IOException ioe) {
      System.out.println(ioe);
      return;
    }
  }
}
