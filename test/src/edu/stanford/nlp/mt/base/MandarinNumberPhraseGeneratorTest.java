package edu.stanford.nlp.mt.base;

import java.util.List;

import edu.stanford.nlp.mt.tm.Rule;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.MandarinNumberPhraseGenerator;
import edu.stanford.nlp.mt.util.RawSequence;
import edu.stanford.nlp.mt.util.Sequence;

import junit.framework.TestCase;



/** @author Christopher Manning */
public class MandarinNumberPhraseGeneratorTest extends TestCase {

  public void testAddCommas() {
    assertEquals("Didn't add commas", "3,124,123,890,789", MandarinNumberPhraseGenerator.addCommas(3124123890789L));
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
    MandarinNumberPhraseGenerator mnpg = new MandarinNumberPhraseGenerator(null);
    assertEquals(inputs.length, outputs.length);
    for (int i = 0; i < inputs.length; i++) {
      Sequence<IString> phrase = new RawSequence<IString>(
              IStrings.toIStringArray(new String[] { inputs[i] }));
      try {
        List<Rule<IString>> opts = mnpg.query(phrase);
        if (opts.isEmpty()) {
          fail("No translation available for " + inputs[i]);
        } else {
          boolean good = false;
          for (Rule<IString> opt : opts) {
            // if (("$num_(" + inputs[i] + "||" + outputs[i] + ")").equals(opt.translation.toString())) {
            if ((outputs[i]).equals(opt.target.toString())) {
              good = true;
              break;
            }
          }
          if ( ! good ) {
            assertEquals("Wrong number/date translation(s), choices were " + opts,
                  // "$num_(" + inputs[i] + "||" + outputs[i] + ")",
                  outputs[i],
                  opts.get(0).target.toString());
          }
        }
      } catch (Exception e) {
        fail(e.toString());
      }
    }
  }
}
