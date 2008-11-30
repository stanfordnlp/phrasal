package mt.translationtreebank;

import java.util.*;
import java.io.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.ling.*;

public class DEinTextReorderer {
  public static void main(String[] args) throws IOException {
    Properties props = StringUtils.argsToProperties(args);

    // (2) setting up the tree & sentence files
    String sentFile = props.getProperty("markedFile", null);
    String treeFile = props.getProperty("treeFile", null);
    SentTreeFileReader reader = new SentTreeFileReader(sentFile, treeFile);
    Tree parsedSent = null;

    while((parsedSent=reader.next())!=null) {
      // Get the index of the DEs
      List<Integer> markedDEIdxs = ExperimentUtils.getMarkedDEIndices(parsedSent.yield());
      List<String> yield = new ArrayList<String>();
      for (HasWord w : parsedSent.yield()) {
        yield.add(w.word());
      }
      Map<SortByEndPair<Integer, Integer>, Integer> toOperate = new TreeMap<SortByEndPair<Integer, Integer>, Integer>();

      // collect the ones to operate on
      for (int deIdx : markedDEIdxs) {
        Pair<Integer,Integer> r = ExperimentUtils.getNPwithDERangeFromIdx(parsedSent, deIdx);
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
        toOperate.put(range, deIdx);
      }

      // check overlap
      // check error
      boolean overlap = false;
      for(SortByEndPair<Integer, Integer> p1 : toOperate.keySet()) {
        for(SortByEndPair<Integer, Integer> p2 : toOperate.keySet()) {
          if (p1.first > p2.second || p1.second < p2.first) continue;
          if (p1.first == p2.second || p1.second == p2.first) {
            System.err.println("P1="+p1);
            System.err.println("P2="+p2);
            throw new RuntimeException("");
          }
          if((p2.first-p1.first)*(p2.second-p1.second) > 0) {
            System.err.println("P1="+p1);
            System.err.println("P2="+p2);
            throw new RuntimeException(""); 
          } else {
            overlap = true;
          }
        }
      }

      for(Map.Entry<SortByEndPair<Integer, Integer>, Integer> e : toOperate.entrySet()) {
        SortByEndPair<Integer, Integer> p = e.getKey();
        int deIdx = e.getValue();
        String de = yield.get(deIdx);
        if (de.equals("的_AsB") || de.equals("的_AprepB") || de.equals("的_AB") || 
            de.equals("的_ordered")) {
          continue;
        } else if (de.equals("的_BprepA") || de.equals("的_relc") || 
                   de.equals("的_swapped")) {
          ExperimentUtils.ReverseSublist(yield, p.first, deIdx-1);
          ExperimentUtils.ReverseSublist(yield, deIdx+1, p.second);
          ExperimentUtils.ReverseSublist(yield, p.first, p.second);
        } else {
          throw new RuntimeException(deIdx+" in "+StringUtils.join(yield, " ")+" is not a valid DE");
        }
      }
    }
  }
}

class SortByEndPair<T1,T2> implements Comparable<SortByEndPair<T1,T2>> {
  public T1 first;
  public T2 second;

  public SortByEndPair(T1 first, T2 second) {
    this.first = first;
    this.second = second;
  }

  @Override
  public String toString() {
    return "(" + first + "," + second + ")";
  }

  public int compareTo(SortByEndPair<T1,T2> another) {
    //System.err.println("CompareTo");
    int comp = ((Comparable) second).compareTo(another.second);
    if (comp != 0) {
      //System.err.println("Compared: "+comp);
      return comp;
    } else {
      return ((Comparable) first).compareTo(another.first);
    }
  }

  @Override
  public int hashCode() {
    return (((first == null) ? 0 : first.hashCode()) << 16) ^ ((second == null) ? 0 : second.hashCode());
  }

}
