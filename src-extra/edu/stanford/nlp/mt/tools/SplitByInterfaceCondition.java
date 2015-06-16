package edu.stanford.nlp.mt.tools;

import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.mt.train.SymmetricalWordAlignment;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.InputProperties;
import edu.stanford.nlp.mt.util.InputProperty;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.util.StringUtils;

/**
 * Split the output of interactive MT system.
 * 
 * @author Spence Green
 *
 */
public class SplitByInterfaceCondition {

  private static class SentencePair {
    public final String provenance;
    public final Sequence<IString> humanHyp;
    public final Sequence<IString> machineHyp;
    public final Sequence<IString> ref;
    public final Sequence<IString> src;
    public SentencePair(String provenance, Sequence<IString> humanHyp, Sequence<IString> machineHyp, Sequence<IString> ref, Sequence<IString> src) {
      this.provenance = provenance;
      this.humanHyp = humanHyp;
      this.machineHyp = machineHyp;
      this.ref = ref;
      this.src = src;
    }
  }
  
  private static class UserDerivation {
    public final String mt;
    public final String user;
    public final SymmetricalWordAlignment s2t;
    public final String src;
    public UserDerivation(String mt, String user, String s2t, String src) {
      this.mt = mt;
      this.user = user;
      this.s2t = new SymmetricalWordAlignment(src, mt, s2t);
      this.src = src;
    }
    @Override
    public String toString() {
      return String.format("%s\t%s\t%s\t%s", mt, user, s2t.toString(), src);
    }
  }
  
  private static String usage() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    sb.append("Usage: java ").append(SplitByInterfaceCondition.class.getName()).append(" [OPTIONS] src ref ref_id mt_file file [file]").append(nl)
      .append(nl)
      .append(" Options:").append(nl)
      .append("   -u fname  : Combine with user derivations dumped from MakePTMPhrasalInput.").append(nl)
      .append("   -d str    : Domain that the user derivation file corresponds to").append(nl);
      
    return sb.toString();
  }

  private static Map<String,Integer> argDefs() {
    Map<String,Integer> argDefs = new HashMap<>();
    argDefs.put("u", 1);
    argDefs.put("d", 1);
    return argDefs;
  }
  
  /**
   * @param args
   */
  public static void main(String[] args) {
    if (args.length < 5) {
      System.err.print(usage());
      System.exit(-1);
    }
    Properties options = StringUtils.argsToProperties(args, argDefs());
    final String userFileName = options.getProperty("u", null);
    final Map<String,UserDerivation> idToUserDerivation = userFileName == null ? null : loadUserFile(userFileName);
    final String targetDomain = options.getProperty("d", "");
    String[] positionalArgs = options.getProperty("").split("\\s+");
    final String srcFilename = positionalArgs[0];
    final String refFilename = positionalArgs[1];
    final String refIdFilename = positionalArgs[2];
    final String mtFilename = positionalArgs[3];
    final List<Sequence<IString>> srcList = IStrings.tokenizeFile(srcFilename);
    final List<Sequence<IString>> refList = IStrings.tokenizeFile(refFilename);
    final List<Sequence<IString>> mtList = IStrings.tokenizeFile(mtFilename);
    final List<String> srcProvenance = loadProvenanceFile(refIdFilename);
    
    // Parse the input files
    Map<String,List<SentencePair>> domainToSentencePairs = new HashMap<>();
    for (int i = 4; i < positionalArgs.length; ++i) {
      String transFileName = positionalArgs[i];
      System.err.println("Reading: " + transFileName);
      List<Sequence<IString>> hypList = IStrings.tokenizeFile(transFileName);
      assert hypList.size() == refList.size();
      int delim = transFileName.lastIndexOf('.');
      String filePrefix = transFileName.substring(0, delim);
      String propsFile = filePrefix + ".props";
      File f = new File(transFileName);
      String userName = f.getName();
      delim = userName.lastIndexOf('.');
      userName = userName.substring(0, delim);
      System.out.println("Processing: " + userName);
      List<InputProperties> inProps = InputProperties.parse(new File(propsFile));
      assert hypList.size() == inProps.size();
      for (int j = 0, limit = hypList.size(); j < limit; ++j) {
        if (hypList.get(j).toString().trim().length() == 0) continue;
        InputProperties hypProperties = inProps.get(j);
        String domain = (String) hypProperties.get(InputProperty.Domain);
        boolean isValid = (Boolean) hypProperties.get(InputProperty.IsValid);
        if ( ! isValid) continue;
        if ( ! domainToSentencePairs.containsKey(domain)) {
          domainToSentencePairs.put(domain, new ArrayList<SentencePair>());
        }
        String provenance = String.format("%s:%s", userName, srcProvenance.get(j));
        domainToSentencePairs.get(domain).add(new SentencePair(provenance, hypList.get(j), 
            mtList.get(j), refList.get(j), srcList.get(j)));
      }
    }
    
    // Create the output files
    for (String domain : domainToSentencePairs.keySet()) {
      PrintStream indexWriter = IOTools.getWriterFromFile(domain + ".ids");
      PrintStream refWriter = IOTools.getWriterFromFile(domain + ".ref");
      PrintStream machineWriter = IOTools.getWriterFromFile(domain + ".mt");
      PrintStream hypWriter = IOTools.getWriterFromFile(domain + ".trans");
      PrintStream srcWriter = IOTools.getWriterFromFile(domain + ".src");
      PrintStream userWriter = IOTools.getWriterFromFile(domain + ".user");
      System.err.printf("Writing %d segments for domain %s%n", domainToSentencePairs.get(domain).size(), domain);
      for (SentencePair pair : domainToSentencePairs.get(domain)) {
        indexWriter.println(pair.provenance);
        refWriter.println(pair.ref.toString());
        machineWriter.println(pair.machineHyp.toString());
        hypWriter.println(pair.humanHyp.toString());
        srcWriter.println(pair.src.toString());
        if (domain.equals(targetDomain)) {
          UserDerivation d = idToUserDerivation.get(pair.provenance);
          if (d != null) {
            userWriter.printf("%s\t%s%n", pair.provenance, d.toString());
          } else {
            System.err.printf("Missing derivation: %s%n", pair.provenance);
            userWriter.println();
          }
        } else {
          userWriter.println(pair.provenance);
        }
      }
      indexWriter.close();
      refWriter.close();
      machineWriter.close();
      hypWriter.close();
      srcWriter.close();
      userWriter.close();
    }
  }

  /**
   * Load the output of <code>MakePTMPhrasalInput</code>.
   * 
   * @param userFileName
   * @return
   */
  private static Map<String,UserDerivation> loadUserFile(String userFileName) {
    Map<String,UserDerivation> map = new HashMap<>();
    LineNumberReader reader = IOTools.getReaderFromFile(userFileName);
    try {
      boolean seenHeader = false;
      for (String line; (line = reader.readLine()) != null;) {
        String[] fields = line.trim().split("\\t");
        assert fields.length == 7 : "Ill-formed line: " + String.valueOf(reader.getLineNumber());
        if ( ! seenHeader ) {
          seenHeader = true;
          continue;
        }
        String id = String.format("%s:%s:%s", fields[2], fields[0], fields[1]);
        id = id.replace(".src.json", ".tgt");
        assert ! map.containsKey(id) : "Duplicate key: " + id;
        map.put(id, new UserDerivation(fields[3], fields[4], fields[5], fields[6]));
      }
      reader.close();
      
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return map;
  }

  /**
   * Load a file that records the provenance of each segment in the src/tgt files.
   * Sould correspond to the ids in the output of <code>MakePTMPhrasalInput</code>.
   * 
   * @param refIdFilename
   * @return
   */
  private static List<String> loadProvenanceFile(String refIdFilename) {
    List<String> provenance = new ArrayList<>();
    LineNumberReader reader = IOTools.getReaderFromFile(refIdFilename);
    try {
      for (String line; (line = reader.readLine()) != null;) {
        provenance.add(line.trim());
      }
      reader.close();
      
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return provenance;
  }
}
