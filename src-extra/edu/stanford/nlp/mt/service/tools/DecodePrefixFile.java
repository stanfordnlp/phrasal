package edu.stanford.nlp.mt.service.tools;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.mt.Phrasal;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.InputProperty;
import edu.stanford.nlp.mt.util.RichTranslation;
import edu.stanford.nlp.mt.util.Sequence;


/**
 * 
 * @author Spence Green
 *
 */
public class DecodePrefixFile {

  private static final int N_BEST_SIZE = 10;
  
  /**
   * @param args
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      System.err.printf("Usage: java %s phrasal_ini source_file prefix_file%n", DecodePrefixFile.class.getName());
      System.exit(-1);
    }
    
    String phrasalIni = args[0];
    String sourceFile = args[1];
    String prefixFile = args[2];
    
    Map<String, List<String>> config = IOTools.readConfigFile(phrasalIni);
    Phrasal p = Phrasal.loadDecoder(config);

    LineNumberReader sourceReader = IOTools.getReaderFromFile(sourceFile);
    LineNumberReader prefixReader = IOTools.getReaderFromFile(prefixFile);
    int sourceId = 0;
    for (String line; (line = sourceReader.readLine()) != null;) {
      Sequence<IString> source = IStrings.tokenize(line);
      String prefix = prefixReader.readLine();
      Sequence<IString> targetPrefix = IStrings.tokenize(prefix);
      List<Sequence<IString>> prefixes = new LinkedList<>();
      prefixes.add(targetPrefix);
      InputProperties inputProperties = new InputProperties();
      inputProperties.put(InputProperty.TargetPrefix, true);
      List<RichTranslation<IString,String>> nbestList = 
          p.decode(source, sourceId, 0, N_BEST_SIZE, prefixes, inputProperties);
      for (RichTranslation<IString,String> t : nbestList) {
        System.out.printf("%d\t%.3f\t%s%n", sourceId, t.score, t.translation.toString());
      }
      ++sourceId;
    }
    sourceReader.close();
    prefixReader.close();
  }

}
