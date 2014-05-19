package edu.stanford.nlp.mt.tools;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.InputProperty;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

/**
 * Split the output of interactive MT system.
 * 
 * @author Spence Green
 *
 */
public class SplitByInterfaceCondition {

  private static class SentencePair {
    public final int index;
    public final String userName;
    public final Sequence<IString> humanHyp;
    public final Sequence<IString> machineHyp;
    public final Sequence<IString> ref;
    public final Sequence<IString> src;
    public SentencePair(int index, String userName, Sequence<IString> humanHyp, Sequence<IString> machineHyp, Sequence<IString> ref, Sequence<IString> src) {
      this.index = index;
      this.userName = userName;
      this.humanHyp = humanHyp;
      this.machineHyp = machineHyp;
      this.ref = ref;
      this.src = src;
    }
  }
  
  private static String usage() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    sb.append("Usage: java ").append(SplitByInterfaceCondition.class.getName()).append(" [OPTIONS] ref mt_file file [file]").append(nl)
      .append(nl)
      .append(" Options:").append(nl);
    return sb.toString();
  }

  private static Map<String,Integer> argDefs() {
    Map<String,Integer> argDefs = Generics.newHashMap();
    return argDefs;
  }
  
  /**
   * @param args
   */
  public static void main(String[] args) {
    if (args.length < 3) {
      System.err.print(usage());
      System.exit(-1);
    }
    Properties options = StringUtils.argsToProperties(args, argDefs());
    String[] positionalArgs = options.getProperty("").split("\\s+");
    final String srcFilename = positionalArgs[0];
    final String refFilename = positionalArgs[1];
    final String mtFilename = positionalArgs[2];
    final List<Sequence<IString>> srcList = IStrings.tokenizeFile(srcFilename);
    final List<Sequence<IString>> refList = IStrings.tokenizeFile(refFilename);
    final List<Sequence<IString>> mtList = IStrings.tokenizeFile(mtFilename);
    
    Map<String,List<SentencePair>> domainToSentencePairs = Generics.newHashMap();
    for (int i = 3; i < positionalArgs.length; ++i) {
      String transFileName = positionalArgs[i];
      System.err.println("Reading: " + transFileName);
      List<Sequence<IString>> hypList = IStrings.tokenizeFile(transFileName);
      assert hypList.size() == refList.size();
      int delim = transFileName.lastIndexOf('.');
      String userName = transFileName.substring(0, delim);
      String propsFile = userName + ".props";
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
        domainToSentencePairs.get(domain).add(new SentencePair(j, userName, hypList.get(j), 
            mtList.get(j), refList.get(j), srcList.get(j)));
      }
    }
    
    for (String domain : domainToSentencePairs.keySet()) {
      PrintStream indexWriter = IOTools.getWriterFromFile(domain + ".ids");
      PrintStream refWriter = IOTools.getWriterFromFile(domain + ".ref");
      PrintStream machineWriter = IOTools.getWriterFromFile(domain + ".mt");
      PrintStream hypWriter = IOTools.getWriterFromFile(domain + ".trans");
      PrintStream srcWriter = IOTools.getWriterFromFile(domain + ".src");
      System.err.printf("Writing %d segments for domain %s%n", domainToSentencePairs.get(domain).size(), domain);
      for (SentencePair pair : domainToSentencePairs.get(domain)) {
        indexWriter.printf("%s\t%d%n", pair.userName, pair.index);
        refWriter.println(pair.ref.toString());
        machineWriter.println(pair.machineHyp.toString());
        hypWriter.println(pair.humanHyp.toString());
        srcWriter.println(pair.src.toString());
      }
      indexWriter.close();
      refWriter.close();
      machineWriter.close();
      hypWriter.close();
      srcWriter.close();
    }
  }
}
