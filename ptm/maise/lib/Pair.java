import java.util.*;
import java.io.*;
import java.text.DecimalFormat;

public class Pair<F,S> implements Comparable {
  public F first;
  public S second;
  public Pair() { first = null; second = null; }
  public Pair(F x, S y) { first = x; second = y; }
  public Pair(String pairStr) {
    if (pairStr.startsWith("<")) {
      pairStr = pairStr.substring(1,pairStr.length()-1); // remove < and >
    }

    if (pairStr.contains("|||")) {
      first = (F)(pairStr.split("\\|\\|\\|")[0]);
      second = (S)(pairStr.split("\\|\\|\\|")[1]);
    } else if (pairStr.contains(",")) {
      first = (F)(pairStr.split(",")[0]);
      second = (S)(pairStr.split(",")[1]);
    } else if (pairStr.contains("-")) {
      first = (F)(pairStr.split("-")[0]);
      second = (S)(pairStr.split("-")[1]);
    }
  }
  public String toString() { return "<" + first + "," + second + ">"; }
  public int compareTo(Object obj) { return (this.toString()).compareTo(obj.toString()); }
}
