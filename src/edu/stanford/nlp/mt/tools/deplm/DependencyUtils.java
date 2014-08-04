package edu.stanford.nlp.mt.tools.deplm;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;

public class DependencyUtils {

  
  /*
   * returns true if token is a word 
   * (starts with a letter or a digit)
   */
  public static boolean isWord(String token) {
    return Character.isAlphabetic(token.charAt(0)) || Character.isDigit(token.charAt(0));
  }
  
  
  /*
   * Convert a forward dependency HashMap (indexed by heads) to a reverse 
   * dependency HashMap (indexed by dependent).
   */
  public static Map<Integer, Integer> getReverseDependencies(HashMap<Integer, Pair<String, List<Integer>>> forwardDependencies) {
    if (forwardDependencies == null)
      return null;
    
    Map<Integer, Integer> reverseDependencies = Generics.newHashMap();
    for (Integer gov : forwardDependencies.keySet()) {
      for (Integer dep : forwardDependencies.get(gov).second) {
        reverseDependencies.put(dep, gov);
      }
    }
    return reverseDependencies;
  }
  
  /*
   * Extracts the next dependency structure from a reader that reads from
   * a file in CoNLL format and puts it into a HashMap index by the head.
   * The ROOT has index 0, fragments have head -1.
   */
  public static HashMap<Integer, Pair<String, List<Integer>>> getDependenciesFromCoNLLFileReader(BufferedReader reader, boolean zeroIndexed) {
    HashMap<Integer, Pair<String, List<Integer>>> forwardDependencies = new HashMap<Integer, Pair<String, List<Integer>>>();
    
    String line = null;
    try {
      while ((line = reader.readLine()) != null && line.length() > 1) {
        String[] fields = line.split("\t");
        int dep = Integer.parseInt(fields[0]) - (zeroIndexed ? 1 : 0);
        int gov = Integer.parseInt(fields[6]);
        if (gov == 0 && fields[7].equals("frag"))
          gov = -1;
        if (zeroIndexed)
          gov--;
        String word = fields[1];
        if (forwardDependencies.get(gov) == null) {
          List<Integer> l = Generics.newLinkedList();
          Pair<String, List<Integer>> p = Generics.newPair(null, l);
          forwardDependencies.put(gov, p);
        }
        if (forwardDependencies.get(dep) == null) {
          List<Integer> l = Generics.newLinkedList();
          Pair<String, List<Integer>> p = Generics.newPair(word, l);
          forwardDependencies.put(dep, p);
        }
        forwardDependencies.get(dep).first = word;
        forwardDependencies.get(gov).second.add(dep);
      }
    
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
    return forwardDependencies.size() > 0 ? forwardDependencies : null;
    
  }
  
}
