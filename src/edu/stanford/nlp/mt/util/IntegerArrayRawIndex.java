package edu.stanford.nlp.mt.util;

/**
 * @author Michel Galley
 */
public interface IntegerArrayRawIndex {

  int getIndex(int[] array);

  int insertIntoIndex(int[] array);

}
