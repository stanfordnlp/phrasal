package edu.stanford.nlp.mt.base;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import junit.framework.TestCase;


/** @author Christopher Manning */
public class PinyinNameTransliterationPhraseGeneratorTest extends TestCase {

  public void testTranslatingNames() {
    String[] inputs = { "余艳", "苏东坡", "陈淑仪", "彭远文"};
    String[] outputArray = { "yu yan", "su dongpo", "chen shuyi", "peng yuanwen" };
    assertEquals(inputs.length, outputArray.length);
    PinyinNameTransliterationPhraseGenerator<IString,String> pnpg =
            new PinyinNameTransliterationPhraseGenerator<IString,String>(null);
    Iterator<String> outputs = Arrays.asList(outputArray).iterator();
    for (String str : inputs) {
      Sequence<IString> phrase = new RawSequence<IString>(
              IStrings.toIStringArray(new String[] { str}));
      try {
        List<TranslationOption<IString>> opts = pnpg.getTranslationOptions(phrase);
        if (opts.isEmpty()) {
          fail("No translation available for " + str);
        } else {
          assertEquals("Too many options generated", 1, opts.size());
          assertEquals(outputs.next(), opts.get(0).target.toString());
        }
      } catch (Exception e) {
        fail(e.toString());
      }
    }
  }

  public void testNotTranslatingNonNames() {
    String[] inputs = { "桑兰", "然后", "西瓜团子" };
    PinyinNameTransliterationPhraseGenerator<IString,String> pnpg =
            new PinyinNameTransliterationPhraseGenerator<IString,String>(null);
    for (String str : inputs) {
      Sequence<IString> phrase = new RawSequence<IString>(
              IStrings.toIStringArray(new String[] { str }));
      try {
        List<TranslationOption<IString>> opts = pnpg.getTranslationOptions(phrase);
        if ( ! opts.isEmpty()) {
          fail("Should be no translation available for " + str);
        }
      } catch (Exception e) {
        fail(e.toString());
      }
    }
  }

}
