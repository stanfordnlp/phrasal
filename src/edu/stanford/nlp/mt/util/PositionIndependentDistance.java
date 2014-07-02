package edu.stanford.nlp.mt.util;

import java.util.Hashtable;

import edu.stanford.nlp.util.Characters;

/** Find the (Levenshtein) Position Independent edit distance between two Strings or Character
 *  arrays.  
 *  @author Ankush Singla
 */
public class PositionIndependentDistance {
  //final boolean allowTranspose;// check what it should do

  protected double score;

  public PositionIndependentDistance() {
  }

  // CONSTRAINT SEMIRING START

  protected double unit() {
    return 1.0;
  }

  
  protected int smaller(int x, int y) {
    if (x < y) {
      return x;
    }
    return y;
  }

  // CONSTRAINT SEMIRING END

  // COST FUNCTION BEGIN

  protected double insertCost() {
    return unit();
  }

  protected double deleteCost() {
    return unit();
  }

  protected double substituteCost() {
    return unit();
  }

  double score(Object[] source, int sPos, Object[] target, int tPos) {
    Hashtable<Object, Integer> hashSource = new Hashtable<Object, Integer>(sPos);
    int currentPos;
    int match = 0;
    int insertions = 0;
    int deletions = 0;
    int substitutions = 0;
    for(currentPos = 0; currentPos < sPos; currentPos++){
      int countOfWord = 1;
      if(hashSource.containsKey(source[currentPos])){
        countOfWord = hashSource.get(source[currentPos]) + 1;
      }
      hashSource.put(source[currentPos],countOfWord);
    }
    
    for(currentPos = 0; currentPos < tPos; currentPos++){
      if(hashSource.containsKey(target[currentPos]) && (hashSource.get(target[currentPos]) > 0)){
        hashSource.put(target[currentPos],hashSource.get(target[currentPos]) - 1);
        match++;
      }
    }
    
    substitutions =  smaller(sPos - match, tPos - match);
    if(sPos > tPos){
      deletions = sPos-tPos;
    }else{
      insertions = tPos -sPos;
    }
    score = substitutions * substituteCost() + deletions * deleteCost() + insertions * insertCost();
    return score;  
  }
  

  public double score(Object[] source, Object[] target) {
    return score(source, source.length, target, target.length);
  }

  public double score(String sourceStr, String targetStr) {
    Object[] source = Characters.asCharacterArray(sourceStr);
    Object[] target = Characters.asCharacterArray(targetStr);
    return score(source, source.length, target, target.length);
  }

  public static void main(String[] args) {
    if (args.length >= 2) {
      PositionIndependentDistance d = new PositionIndependentDistance();
      System.out.println(d.score(args[0], args[1]));
    } else {
      System.err.println("usage: java PositionIndependentDistance str1 str2");
    }
  }

}
