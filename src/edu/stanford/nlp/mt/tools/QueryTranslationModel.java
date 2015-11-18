package edu.stanford.nlp.mt.tools;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.List;

import edu.stanford.nlp.mt.tm.ConcreteRule;
import edu.stanford.nlp.mt.tm.TranslationModel;
import edu.stanford.nlp.mt.tm.TranslationModelFactory;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.Sequence;

/**
 * Query a translation model.
 * 
 * @author Spence Green
 *
 */
public class QueryTranslationModel {

  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.printf("Usage: java %s tm_file < string%n", QueryTranslationModel.class.getName());
      System.exit(-1);
    }
    final String tmFile = args[0];
    try {
      TranslationModel<IString,String> tm = TranslationModelFactory.factory(tmFile);
//      try(LineNumberReader reader = new LineNumberReader(new InputStreamReader(System.in))) {
        int sourceId = 0;
        InputProperties inProps = new InputProperties();
//        for (String line; (line = reader.readLine()) != null; ) {
        String line = "my";
          Sequence<IString> source = IStrings.tokenize(line);
          List<ConcreteRule<IString,String>> ruleList = 
              tm.getRules(source, inProps, sourceId++, null);
          System.out.printf("= %d : %d rules ========%n", sourceId, ruleList.size());
          for (ConcreteRule<IString,String> rule : ruleList) {
            System.out.println(rule);
          }
          System.out.println("################");
//        }
//      }

    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
