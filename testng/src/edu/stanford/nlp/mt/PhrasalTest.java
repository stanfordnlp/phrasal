package edu.stanford.nlp.mt;

import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.IString;

import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import org.testng.annotations.*;

/**
 *
 * @author Karthik Raghunathan
 *
 */

@Test (groups = "mt")
public class PhrasalTest {

  @Test
  public void testReadConfig() throws IOException{
    Map<String, List<String>> config = Phrasal.readConfig("/u/nlp/data/testng/inputs/sample.ini");

    List<String> test = new ArrayList<String>();
    test.add("0.085872");
    assert(config.get("weight-l").equals(test));

    test = new ArrayList<String>();
    test.add("20");
    test.add("0");
    assert(config.get("ttable-limit").equals(test));

    test = new ArrayList<String>();
    test.add("/u/nlp/data/testng/inputs/mt06.tables/phrase-table.gz");
    assert(config.get("ttable-file").equals(test));

    test = new ArrayList<String>();
    test.add("6");
    assert(config.get("distortion-limit").equals(test));

    test = new ArrayList<String>();
    test.add("-0.202287");
    assert(config.get("weight-w").equals(test));

    test = new ArrayList<String>();
    test.add("0.019243");
    test.add("0.052945");
    test.add("0.042900");
    test.add("0.025499");
    test.add("0.073802");
    assert(config.get("weight-t").equals(test));

    test = new ArrayList<String>();
    test.add("0.008814");
    assert(config.get("weight-d").equals(test));

    test = new ArrayList<String>();
    test.add("0-0");
    test.add("msd-bidirectional-fe");
    test.add("6");
    test.add("/u/nlp/data/testng/inputs/mt06.tables/msd-bidirectional-fe.gz");
    assert(config.get("distortion-file").equals(test));

    test = new ArrayList<String>();
    test.add("/u/nlp/data/testng/inputs/mt06.flt_giga.lm.gz");
    assert(config.get("lmodel-file").equals(test));
  }

  @Test
  public void testDecodeOnly() throws Exception{
    Map<String, List<String>> config = Phrasal.readConfig("/u/nlp/data/testng/inputs/sample.ini");
    Phrasal p = new Phrasal(config);
    String line = "代表";
    String[] tokens = line.split("\\s+");
    RichTranslation<IString, String> translation = p.decodeOnly(tokens, 1, 1, 0);
    assert(translation.score == -1.3413452979742324);
    assert(translation.translation.toString().equals("on behalf of the"));
    assert(translation.foreign.toString().equals("代表"));
    assert(translation.nbestToMosesString(1).equals("1 ||| on behalf of the ||| LM: -1.9327E1 LexR::monotoneWithPrevious: -2.7454E0 LinearDistortion: 0 TM:lex(f|t): -1.1804E1 TM:lex(t|f): -1.5146E0 TM:phi(f|t): -3.8449E0 TM:phi(t|f): -9.6418E-1 TM:phrasePenalty: 9.999E-1 UnknownWord: 0 WordPenalty: -4 ||| -1.3413E0 ||| 0=0-3"));
  }

}

