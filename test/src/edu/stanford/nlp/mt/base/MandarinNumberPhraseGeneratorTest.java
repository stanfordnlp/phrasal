package edu.stanford.nlp.mt.base;

import junit.framework.TestCase;



/** @author Christopher Manning */
public class MandarinNumberPhraseGeneratorTest extends TestCase {

  public void testAddCommas() {
    MandarinNumberPhraseGenerator mnpg = new MandarinNumberPhraseGenerator(null, null);
    assertEquals("3,124,123,890,789", mnpg.addCommas(3124123890789L));
  }

  public void testDates() {
    MandarinNumberPhraseGenerator mnpg = new MandarinNumberPhraseGenerator(null, null);
    assertEquals("28th", mnpg.getTranslationOptions(IStrings.splitToIStrings("28日")).get(0).translation.get(0).toString());
  }
}
