package edu.stanford.nlp.mt.base;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Scorer;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;


/** The goal of this class is to provide a Pinyin transliteration of words as
 *  proper names. At present it only provides a very crude check of whether
 *  something is a proper name (first character in a common family names list
 *  and 2-3 characters long). If so, it renders a pinyin transliteration of it
 *  as a name.
 *
 *  @author Christopher Manning
 *
 *  @param <TK>
 *  @param <FV>
 */
public class PinyinNameTransliterationPhraseGenerator<TK extends IString, FV> extends
        AbstractPhraseGenerator<TK, FV> implements DynamicPhraseGenerator<TK> {

  public static final String PHRASE_TABLE_NAMES = "PinyinNameTransliterationPhraseGenerator(Dyn)";
  public static final String[] DEFAULT_SCORE_NAMES = { "p_py(t|f)" };
  public static final float[] SCORE_VALUES = { (float) 1.0 };
  public static final String DEBUG_PROPERTY = "PinyinNameTransliterationPhraseGeneratorDebug";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
          DEBUG_PROPERTY, "false"));

  // do we need to account for "(0) (1)", etc?
  public static final PhraseAlignment DEFAULT_ALIGNMENT = PhraseAlignment
          .getPhraseAlignment("I-I");

  private final String[] scoreNames;
  private final SequenceFilter<TK> filter;
  private final HanyuPinyinOutputFormat format;


  public PinyinNameTransliterationPhraseGenerator(IsolatedPhraseFeaturizer<TK, FV> phraseFeaturizer,
                                                  Scorer<FV> scorer) {
    super(phraseFeaturizer, scorer);
    filter = new ProbableChineseNameFilter<TK>();
    scoreNames = DEFAULT_SCORE_NAMES;
    format = new HanyuPinyinOutputFormat();
    format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
  }

  @Override
  public String getName() {
    return PHRASE_TABLE_NAMES;
  }

  @Override
  public List<TranslationOption<TK>> getTranslationOptions(Sequence<TK> sequence) {
    List<TranslationOption<TK>> list = new LinkedList<TranslationOption<TK>>();
    RawSequence<TK> raw = new RawSequence<TK>(sequence);
    if (filter.accepts(raw)) {
      String word = raw.toString();
      String transliteration = transliterate(word);
      // todo: fix the types somehow
      RawSequence<IString> trans = new RawSequence<IString>(IStrings.toIStringArray(transliteration.split("\\s+")));
      RawSequence<TK> trans2 = (RawSequence<TK>) trans;

      list.add(new TranslationOption<TK>(SCORE_VALUES, scoreNames,
              trans2, raw, DEFAULT_ALIGNMENT));
    }
    return list;
  }


  /* Very special cased for 2 or 3 character names. */
  private String transliterate(String input) {
    StringBuilder output = new StringBuilder();
    for (int i = 0; i < input.length(); i++) {
      String candidate = "";
      try {
        String[] opts =PinyinHelper.toHanyuPinyinStringArray(input.charAt(i), format);
        if (opts.length > 0) {
          candidate = opts[0];  // always just take first
        }
      } catch (BadHanyuPinyinOutputFormatCombination bad) {
        // do nothing
      }
      if (i <= 1) {
        output.append(Character.toUpperCase(candidate.charAt(0)));
        output.append(candidate.substring(1));
        if (i == 0) {
          output.append(' ');
        }
      } else {
        output.append(candidate);
      }
    }
    return output.toString();
  }

  @Override
  public int longestForeignPhrase() {
    return -Integer.MAX_VALUE;
  }

  @Override
  public void setCurrentSequence(Sequence<TK> foreign,
                                 List<Sequence<TK>> tranList) {
    // no op
  }

  public static void main(String[] args) throws IOException {
    PinyinNameTransliterationPhraseGenerator<IString,String> pnpg =
            new PinyinNameTransliterationPhraseGenerator<IString,String>(null, null);

    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, "utf-8"));
    PrintWriter writer = new PrintWriter(new OutputStreamWriter(System.out, "utf-8"));
    // BufferedReader reader = new BufferedReader(new java.io.StringReader("桑兰\n余艳\n连战\n苏东坡\n陈淑仪\n桑兰\n彭远文"));
    writer.println("PinyinNameTransliterationPhraseGenerator Interactive REPL");
    writer.println("(ctrl-c to quit)");
    writer.print("> ");
    writer.flush();
    for (String line; (line = reader.readLine()) != null; ) {
      String[] tokens = line.split("\\s+");
      Sequence<IString> phrase = new RawSequence<IString>(IStrings.toIStringArray(tokens));

      try {
        List<TranslationOption<IString>> opts = pnpg.getTranslationOptions(phrase);
        if (opts.isEmpty()) {
          writer.println("No translation available for " + line);
        } else {
          writer.println("Options");
          for (TranslationOption<IString> opt : opts) {
            writer.printf("\t%s%n", opt);
          }
        }
      } catch (Exception e) {
        System.out.println("Error getting translation:");
        e.printStackTrace();
      }
      writer.print("> ");
      writer.flush();
    }
    writer.close();
  }

  public static class ProbableChineseNameFilter<TK extends CharSequence> implements
          SequenceFilter<TK> {

    private static final char[] familyNameChars = {
            /* Rob's list of 100 most common names */
            '李', '王', '张', '刘', '陈', '杨', '黄','赵', '周', '吴',
            '徐', '孙', '朱', '马', '胡', '郭', '林', '何', '高', '梁',
            '郑', '罗', '宋', '谢', '唐', '韩', '曹', '许', '邓', '萧',
            '冯', '曾', '程', '蔡', '彭', '潘', '袁', '於', '董', '馀',
            '苏', '叶', '吕', '魏', '蒋', '田', '杜', '丁', '沈', '姜',
            '范', '江', '傅', '钟', '卢', '汪', '戴', '崔', '任', '陆',
            '廖', '姚', '方', '金', '邱', '夏', '谭', '韦', '贾', '邹',
            '石', '熊', '孟', '秦', '阎', '薛', '侯', '雷', '白', '龙',
            '段', '郝', '孔', '邵', '史', '毛', '常', '万', '顾', '赖',
            '武', '康', '贺', '严', '尹', '钱', '施', '牛', '洪', '龚',
            // Additional ones from Wikipedia English Common Chinese Names list top 100 in 2007
            '肖', '于', '余', '付', '闫', '陶', '黎', '覃', '莫', '向', '汤'
    };

//    private static final String wikiCommon =
//            "王李张刘陈杨黄赵吴周徐孙马朱胡郭何高林罗" +
//            "郑梁谢宋唐许韩冯邓曹彭曾肖田董袁潘于蒋蔡" +
//            "余杜叶程苏魏吕丁任沈姚卢姜崔钟谭陆汪范金" +
//            "石廖贾夏韦付方白邹孟熊秦邱江尹薛闫段雷侯" +
//            "龙史陶黎贺顾毛郝龚邵万钱严覃武戴莫孔向汤";

    private static final HashSet<Character> commonNames = new HashSet<Character>();

    static {
      for (char ch : familyNameChars) {
        commonNames.add(ch);
      }
//      for (int i = 0; i < wikiCommon.length(); i++) {
//        if ( ! commonNames.contains(wikiCommon.charAt(i))) {
//          EncodingPrintWriter.out.println("New names: " + wikiCommon.charAt(i), "utf-8");
//        }
//      }
    }

    @Override
    public boolean accepts(Sequence<TK> sequence) {
      if (sequence.size() != 1) {
        return false;
      }
      // System.out.println("Candidate is 1 word");
      TK token = sequence.get(0);
      int length =  token.length();
      // todo [cdm]: probably should do this properly to handle non-BMP chars...
      if (length < 2 || length > 3) {
        return false;
      }
      // System.out.println("Candidate is 2 or 3 chars");
      char first = token.charAt(0);
      // if (commonNames.contains(first)) {
      //   System.out.println("Candidate starts with common family name");
      // }
      return commonNames.contains(first);
    }

  } // end static class ProbableChineseNameFilter

}
