package edu.stanford.nlp.mt.tools;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.InputProperty;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.util.Generics;

/**
 * Split the output of interactive MT system.
 * 
 * @author Spence Green
 *
 */
public class SplitByInterfaceCondition {

  private static class SentencePair {
    public final Sequence<IString> humanHyp;
    public final Sequence<IString> machineHyp;
    public final Sequence<IString> ref;
    public SentencePair(Sequence<IString> humanHyp, Sequence<IString> machineHyp, Sequence<IString> ref) {
      this.humanHyp = humanHyp;
      this.machineHyp = machineHyp;
      this.ref = ref;
    }
  }
  
  /**
   * @param args
   */
  public static void main(String[] args) {
    if (args.length < 3) {
      System.err.printf("Usage: java %s ref mt_file file [file]%n", SplitByInterfaceCondition.class.getName());
      System.err.println("Assumes that for every input file there is an associated .props file with the InputProperties");
      System.exit(-1);
    }
    final String refFilename = args[0];
    final String mtFilename = args[1];
    final List<Sequence<IString>> refList = IStrings.tokenizeFile(refFilename);
    final List<Sequence<IString>> mtList = IStrings.tokenizeFile(mtFilename);
    
    Map<String,List<SentencePair>> domainToSentencePairs = Generics.newHashMap();
    for (int i = 2; i < args.length; ++i) {
      String transFileName = args[i];
      System.err.println("Reading: " + transFileName);
      List<Sequence<IString>> hypList = IStrings.tokenizeFile(transFileName);
      assert hypList.size() == refList.size();
      int delim = transFileName.lastIndexOf('.');
      String propsFile = transFileName.substring(0, delim) + ".props";
      List<InputProperties> inProps = InputProperties.parse(new File(propsFile));
      assert hypList.size() == inProps.size();
      for (int j = 0, limit = hypList.size(); j < limit; ++j) {
        if (hypList.get(j).toString().trim().length() == 0) continue;
        InputProperties hypProperties = inProps.get(j);
        String domain = (String) hypProperties.get(InputProperty.Domain);
        boolean isValid = Boolean.valueOf((String) hypProperties.get(InputProperty.IsValid));
        if ( ! isValid) continue;
        if ( ! domainToSentencePairs.containsKey(domain)) {
          domainToSentencePairs.put(domain, new ArrayList<SentencePair>());
        }
        domainToSentencePairs.get(domain).add(new SentencePair(hypList.get(j), mtList.get(j), refList.get(j)));
      }
    }
    
    for (String domain : domainToSentencePairs.keySet()) {
      PrintStream refWriter = IOTools.getWriterFromFile(domain + ".ref");
      PrintStream machineWriter = IOTools.getWriterFromFile(domain + ".mt");
      PrintStream hypWriter = IOTools.getWriterFromFile(domain + ".trans");
      System.err.printf("Writing %d segments for domain %s%n", domainToSentencePairs.get(domain).size(), domain);
      for (SentencePair pair : domainToSentencePairs.get(domain)) {
        refWriter.println(pair.ref.toString());
        machineWriter.println(pair.machineHyp.toString());
        hypWriter.println(pair.humanHyp.toString());
      }
      refWriter.close();
      machineWriter.close();
      hypWriter.close();
    }
  }
}
