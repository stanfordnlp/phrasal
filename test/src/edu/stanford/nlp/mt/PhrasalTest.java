package edu.stanford.nlp.mt;

import edu.stanford.nlp.mt.base.RichTranslation;
import edu.stanford.nlp.mt.base.IString;

import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import junit.framework.TestCase;
import static org.junit.Assert.* ;

/**
 * @author Karthik Raghunathan
 * @author Michel Galley (conversion from testng to junit)
 */
public class PhrasalTest extends TestCase {

  public void testReadConfig() throws IOException {
    Map<String, List<String>> config = Phrasal.readConfig("projects/mt/test/inputs/sample.ini");

    List<String> test = new ArrayList<String>();
    test.add("0.085872");
    assertTrue(config.get("weight-l").equals(test));

    test = new ArrayList<String>();
    test.add("20");
    test.add("0");
    assertTrue(config.get("ttable-limit").equals(test));

    test = new ArrayList<String>();
    test.add("projects/mt/test/inputs/mt06.phrase-table.gz");
    assertTrue(config.get("ttable-file").equals(test));

    test = new ArrayList<String>();
    test.add("6");
    assertTrue(config.get("distortion-limit").equals(test));

    test = new ArrayList<String>();
    test.add("-0.202287");
    assertTrue(config.get("weight-w").equals(test));

    test = new ArrayList<String>();
    test.add("0.019243");
    test.add("0.052945");
    test.add("0.042900");
    test.add("0.025499");
    test.add("0.073802");
    assertTrue(config.get("weight-t").equals(test));

    test = new ArrayList<String>();
    test.add("0.008814");
    assertTrue(config.get("weight-d").equals(test));

    test = new ArrayList<String>();
    test.add("0-0");
    test.add("msd-bidirectional-fe");
    test.add("6");
    test.add("projects/mt/test/inputs/mt06.msd-bidirectional-fe.gz");
    assertTrue(config.get("distortion-file").equals(test));

    test = new ArrayList<String>();
    test.add("projects/mt/test/inputs/mt06.flt_giga.lm.gz");
    assertTrue(config.get("lmodel-file").equals(test));
  }

  public void testDecodeOnly() throws Exception {
    Map<String, List<String>> config = Phrasal.readConfig("projects/mt/test/inputs/sample.ini");
    Phrasal p = new Phrasal(config);
    String line = "代表";
    String[] tokens = line.split("\\s+");
    RichTranslation<IString, String> translation = p.decodeOnly(tokens, 1, 1, 0);
    assertTrue(translation.score == -1.3398349461093697);
    assertTrue(translation.translation.toString().equals("representatives of the"));
    assertTrue(translation.foreign.toString().equals("代表"));
		assertTrue(translation.nbestToString(1).equals("1 ||| representatives of the ||| LM: -1.8078E1 LinearDistortion: 0 TM:lex(f|t): -7.485E0 TM:lex(t|f): -9.3295E-1 TM:phi(f|t): -4.6372E0 TM:phi(t|f): -1.5041E0 TM:phrasePenalty: 9.999E-1 UnknownWord: 0 WordPenalty: -3 ||| -1.3398E0"));
  }
}

