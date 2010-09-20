package edu.stanford.nlp.mt.train;

/**
 * @author Michel Galley
 */
public interface PhrasePrinter {

  public String toString(AlignmentTemplateInstance phrase, boolean withAlignment);

}