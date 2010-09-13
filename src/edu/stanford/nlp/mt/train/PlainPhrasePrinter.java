package edu.stanford.nlp.mt.train;

/**
 * @author Michel Galley
 */
@SuppressWarnings("unused")
public class PlainPhrasePrinter implements PhrasePrinter {

  @Override
  public String toString(AlignmentTemplateInstance phrase, boolean withAlignment) {
    return phrase.toString(withAlignment);
  }

}
