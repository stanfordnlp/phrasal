package edu.stanford.nlp.mt.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import edu.stanford.nlp.mt.tm.DynamicTranslationModel;
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
public final class DynamicRuleQuery {

  public static void main(String[] args) {
    if (args.length != 1) {
      System.out.printf("Usage: java %s tm_name%n", DynamicRuleQuery.class.getName());
      System.exit(-1);
    }

    final String fileName = args[0];
    try(BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
      DynamicTranslationModel<String> tm = DynamicTranslationModel.load(fileName, true, DynamicTranslationModel.DEFAULT_NAME);
      InputProperties inProps = new InputProperties();
      int sourceId = 0;
      for (String line; (line = reader.readLine()) != null;) {
        System.out.println(line.trim());
        Sequence<IString> source = IStrings.tokenize(line);
        tm.getRules(source, inProps, sourceId++, null).forEach(r -> System.out.printf("%s ==> %s%n", 
            r.abstractRule.source.toString(), r.abstractRule.target.toString()));
        System.out.println("=============================================================");
      }
      
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
