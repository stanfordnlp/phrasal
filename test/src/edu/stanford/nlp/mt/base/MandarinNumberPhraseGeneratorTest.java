package edu.stanford.nlp.mt.base;

import java.util.List;

import junit.framework.TestCase;



/** @author Christopher Manning */
public class MandarinNumberPhraseGeneratorTest extends TestCase {

  public void testAddCommas() {
    MandarinNumberPhraseGenerator mnpg = new MandarinNumberPhraseGenerator(null, null);
    assertEquals("Didn't add commas", "3,124,123,890,789", mnpg.addCommas(3124123890789L));
  }

  public void testPhraseGeneration() {
    String[] inputs = {"28日",
            "10331.51万",
            "六 个 月"
    };
    String[] outputs = {"28th",
            "103,315,100",
            "six months",
    };
    MandarinNumberPhraseGenerator mnpg = new MandarinNumberPhraseGenerator(null, null);
    assertEquals(inputs.length, outputs.length);
    for (int i = 0; i < inputs.length; i++) {
      Sequence<IString> phrase = new RawSequence<IString>(
              IStrings.toIStringArray(new String[] { inputs[i] }));
      try {
        List<TranslationOption<IString>> opts = mnpg.getTranslationOptions(phrase);
        if (opts.isEmpty()) {
          fail("No translation available for " + inputs[i]);
        } else {
          boolean good = false;
          for (TranslationOption<IString> opt : opts) {
            if (("$num_(" + inputs[i] + "||" + outputs[i] + ")").equals(opt.translation.toString())) {
              good = true;
              break;
            }
          }
          if ( ! good ) {
            assertEquals("Wrong number/date translation(s), showing 1st of " + opts.size(),
                  "$num_(" + inputs[i] + "||" + outputs[i] + ")",
                  opts.get(0).translation.toString());
          }
        }
      } catch (Exception e) {
        fail(e.toString());
      }
    }
  }
}
