package edu.stanford.nlp.mt.classifyde;

import java.util.*;
import java.io.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.ling.*;

public class DEinTextReorderer {
  public static void main(String[] args) throws IOException {
    Properties props = StringUtils.argsToProperties(args);
    Boolean noreorder = Boolean.parseBoolean(props.getProperty("noreorder", "false"));
    Boolean betterRange = Boolean.parseBoolean(props.getProperty("betterRange", "false"));
    Boolean strictRange = Boolean.parseBoolean(props.getProperty("strictRange", "false"));

    // (2) setting up the tree & sentence files
    String sentFile = props.getProperty("markedFile", null);
    String treeFile = props.getProperty("treeFile", null);
    SentTreeFileReader reader = new SentTreeFileReader(sentFile, treeFile);
    Tree parsedSent = null;

    while((parsedSent=reader.next())!=null) {
      // Get the index of the DEs
      List<Integer> markedDEIdxs = ExperimentUtils.getMarkedDEIndices(parsedSent.yield());
      List<String> yield = new ArrayList<String>();
      for (Label w : parsedSent.yield()) {
        yield.add(w.value());
      }
      Map<SortByEndPair<Integer, Integer>, Integer> toOperate = new TreeMap<SortByEndPair<Integer, Integer>, Integer>();

      // collect the ones to operate on
      for (int deIdx : markedDEIdxs) {
        if (!yield.get(deIdx).startsWith("的_"))
          throw new RuntimeException(yield.get(deIdx)+"("+deIdx+") in "+StringUtils.join(yield, " ")+" is not a valid DE");
        Pair<Integer,Integer> r;
        if (betterRange) 
          r = ExperimentUtils.getNPwithDERangeFromIdx_DNPorCP(parsedSent, deIdx);
        else if (strictRange) {
          String dnpcpLabel = ExperimentUtils.getNPwithDE_DNPorCPLabel(parsedSent, deIdx);
          if (!dnpcpLabel.equals("DNP") && !dnpcpLabel.equals("CP")) {
            if (!yield.get(deIdx).startsWith("的_")) throw new RuntimeException("...");
            yield.set(deIdx, "的");
            continue;
          }
          r = ExperimentUtils.getNPwithDERangeFromIdx_DNPorCP(parsedSent, deIdx);
        }
        else
          r = ExperimentUtils.getNPwithDERangeFromIdx(parsedSent, deIdx);
        
        SortByEndPair<Integer,Integer> range = new SortByEndPair<Integer,Integer>(r.first, r.second);
        String rootLabel = ExperimentUtils.getNPwithDE_rootLabel(parsedSent, deIdx);
        if (range.first == -1) {
          throw new RuntimeException("ERROR: Flat tree shouldn't have been marked");
        }
        if (!rootLabel.equals("NP")) {
          if (!yield.get(deIdx).startsWith("的_")) throw new RuntimeException("...");
          yield.set(deIdx, "的");
          continue;
        }
        //System.err.printf("put %s(%d) in deIdx\n", yield.get(deIdx), deIdx);
        toOperate.put(range, deIdx);
      }

      // check overlap
      // check error
      for(SortByEndPair<Integer, Integer> p1 : toOperate.keySet()) {
        for(SortByEndPair<Integer, Integer> p2 : toOperate.keySet()) {
          if (p1.first > p2.second || p1.second < p2.first) continue;
          if ((p1.first == p2.second || p1.second == p2.first) && p1.first != p1.second) {
            System.err.println("P1="+p1);
            System.err.println("P1 deIdx="+toOperate.get(p1));
            for(int i = p1.first; i <= p1.second; i++) {
              System.err.print(yield.get(i)+" ");
            }
            System.err.println();
            System.err.println("P2="+p2);
            System.err.println("P2 deIdx="+toOperate.get(p2));
            parsedSent.pennPrint(System.err);
            throw new RuntimeException("");
          }
          if((p2.first-p1.first)*(p2.second-p1.second) > 0) {
            System.err.println("P1="+p1);
            System.err.println("P2="+p2);
            throw new RuntimeException(""); 
          }
        }
      }

      //System.err.println("(0): "+StringUtils.join(yield, " "));
      int counter = 1;
      for(Map.Entry<SortByEndPair<Integer, Integer>, Integer> e : toOperate.entrySet()) {
        SortByEndPair<Integer, Integer> p = e.getKey();
        int deIdx = e.getValue();
        String de = yield.get(deIdx);
        if (de.equals("的_AsB") || de.equals("的_AprepB") || 
            de.equals("的_AB")  || de.equals("的_noB") ||
            de.equals("的_ordered")) {
          continue;
        } else if (de.equals("的_BprepA") || de.equals("的_relc") || 
                   de.equals("的_swapped")) {
          if (!noreorder) {
            ExperimentUtils.ReverseSublist(yield, p.first, deIdx-1);
            ExperimentUtils.ReverseSublist(yield, deIdx+1, p.second);
            ExperimentUtils.ReverseSublist(yield, p.first, p.second);
          }
          //System.err.println("("+counter+"): "+StringUtils.join(yield, " "));
          counter++;
        } else if (de.startsWith("的_")){
          throw new RuntimeException("error: "+de);
        } else {
          System.err.println("(S) Skip this operation:");
          System.err.println("(S) P="+p);
          System.err.println("(S) deIdx="+deIdx);
          parsedSent.pennPrint(System.err);
          System.err.println("(S) "+de+"("+deIdx+") in "+StringUtils.join(yield, " ")+" is not a valid DE");
          System.err.println("(S) --------------------");
        }
      }

      System.out.println(StringUtils.join(yield, " "));
    }
  }
}

