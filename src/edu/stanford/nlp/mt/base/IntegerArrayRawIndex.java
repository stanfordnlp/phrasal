package edu.stanford.nlp.mt.base;

/**
 * @author Michel Galley
 */
public interface IntegerArrayRawIndex {

  int getIndex(int[] array);

  int insertIntoIndex(int[] array);

}
