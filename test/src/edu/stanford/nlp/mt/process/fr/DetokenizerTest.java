package edu.stanford.nlp.mt.process.fr;

import junit.framework.TestCase;
/**
 * @author Christopher Manning
 */
public class DetokenizerTest extends TestCase {

  Detokenizer detokenizer;

  public void setUp() {
    detokenizer = Detokenizer.getInstance();
  }

  public void testQuotes() {
    assertEquals("Wrong quote detokenization",
            "\"Nous devons vivre avec eux.\" Se débarrasser d'eux n'est pas une option.\"",
            detokenizer.detok("\" Nous devons vivre avec eux . \" Se débarrasser d' eux n' est pas une option . \" "));
    assertEquals("Wrong quote detokenization",
            "\"J'aime jouer avec lui.\"",
            detokenizer.detok("\" J' aime jouer avec lui . \""));
  }

  public void testTime() {
    assertEquals("Wrong time detokenization",
            "Avant d'affronter les Blues mardi soir (20h45 heures),",
            detokenizer.detok("Avant d' affronter les Blues mardi soir ( 20 h 45 heures ) ,"));
  }

  public void testApple() {
    assertEquals("Wrong iPhone",
            "l'iPhone",
            detokenizer.fixUp("l'iphone"));
  }

}
