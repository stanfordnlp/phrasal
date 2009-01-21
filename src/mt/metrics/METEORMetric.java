package mt.metrics;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.StreamGobbler;
import mt.base.IOTools;

/**
 * @author Michel Galley
 */
public class METEORMetric {
  // TODO: have METEOR extend AbstractMetric<TK,FV>

  private static final String rootName = "meteor.tmp.";
  private static final boolean verbose = false;
  private static final int maxLen = 120; // 150

  /**
   * Score a list of hypotheses again a single list of references.
   * There is currently no support for multi-references.
   *
   * @param refAndHyp
   * @return
   */
  public static Map<String,Double> score(List<Pair<String,String>> refsAndHyps, String modules) {

    Map<String,Double> scores = new HashMap<String,Double>();

    try {

      File refFile = File.createTempFile(rootName,".ref.sgm", new File("/tmp"));
      File hypFile = File.createTempFile(rootName,".hyp.sgm", new File("/tmp"));
      File outFile = File.createTempFile(rootName,".meteor", new File("/tmp"));
      File logFile = File.createTempFile(rootName,".meteor.log", new File("/tmp"));

      createSGML(refsAndHyps,refFile.getAbsolutePath(),true);
      createSGML(refsAndHyps,hypFile.getAbsolutePath(),false);

      Runtime rt = Runtime.getRuntime();

      String cmd =
        "perl /u/nlp/packages/meteor-0.7/meteor.pl -s machine1 " +
        " -r " + refFile.getAbsolutePath() +
        " -t "+ hypFile.getAbsolutePath() +
        " -plainOutput -outFile " + outFile.getAbsolutePath();
      if(modules != null)
        cmd += " -modules " + modules;
      if(verbose)
        System.err.println("Running command: "+cmd);
      Process p = rt.exec(cmd);

      Writer log = new BufferedWriter(new FileWriter(logFile));
      StreamGobbler s1 = new StreamGobbler(p.getInputStream(), log);
      StreamGobbler s2 = new StreamGobbler(p.getErrorStream(), log);
      s1.start();
      s2.start();

      p.waitFor();
      log.close();
      if(verbose)
        System.out.println("Process exit value: " + p.exitValue());

      int i=0;
      for(String line : StringUtils.slurpFileNoExceptions(outFile).split("\\n")) {

        line = line.replaceAll("^\\s+", "");  // no leading space
        line = line.replaceAll("\\s+$", "");  // no trailing space
        if(line.charAt(0) == '#')
          continue;
        
        String[] toks = line.split("\\s+");
        if(toks.length != 2)
          throw new IOException("Wrong number of tokens on line: "+line);
        if(!toks[0].startsWith("doc.1::"))
          throw new IOException("Wrong format at line: "+line);
        int sid = Integer.parseInt(toks[0].substring(7))-1;
        if(sid != i)
          throw new IOException(String.format("segment ids somehow got out of sync. Expected id=%d, but found id=%d.", i, sid));

        String pair = refsAndHyps.get(i).toString();
        double score = Double.parseDouble(toks[1]);
        if(verbose)
          System.err.printf("pair: %s score: %f\n", pair, score);
        scores.put(pair, score);
        ++i;
      }
      // may not match because of duplicate (ref,hyp) pairs:
      //if(scores.size() != refsAndHyps.size())
      //  throw new IOException(String.format
      //     ("Number of scores (%d) does not match the number of sentences (%d)",scores.size(), refsAndHyps.size()));

      refFile.deleteOnExit();
      hypFile.deleteOnExit();
      outFile.deleteOnExit();
      logFile.deleteOnExit();
      
    } catch(IOException e) {
      e.printStackTrace(); 
    } catch(InterruptedException e) {
      e.printStackTrace();
    }

    return scores;
  }

  /**
   * Create NIST SGML file with dummy values for setid, docid, etc.
   */
  private static void createSGML(List<Pair<String,String>> refsAndHyps, String outputFile, boolean refset) {
    PrintStream out = IOTools.getWriterFromFile(outputFile);
    out.append("<");
    out.append(refset ? "refset" : "tstset");
    out.append("setid=\"setid\" srclang=\"s\" trglang=\"t\">\n");
    out.append("<DOC docid=\"doc.1\" sysid=\"");
    out.append(refset ? "human1" : "machine1");
    out.append("\">\n");
    int segid=0;
    for(Pair<String,String> sent : refsAndHyps) {
      out.append("<seg id=");
      out.append(Integer.toString(++segid));
      out.append("> ");
      out.append(truncate(refset ? sent.first() : sent.second()));
      out.append(" </seg>\n");
    }
    out.append("</DOC>\n</");
    out.append(refset ? "refset" : "tstset");
    out.append(">\n");
    //out.flush();
    out.close();
  }

  private static String truncate(String in) {
    String[] toks = in.split("\\s+");
    if(toks.length <= maxLen)
      return in;
    return StringUtils.join(Arrays.copyOf(toks, maxLen)," ");
  }

  public static void main(String[] args) {
    List<Pair<String,String>> data = new ArrayList<Pair<String,String>>();

    data.add(new Pair<String,String>
     ("ezzat ibrahim receives responsible economically denied permission to baghdad",
      "Izzet Ibrahim Meets Saudi Trade Official in Baghdad"));

    data.add(new Pair<String,String>
     ("baghdad 1-1 ( afp ) - news agency reported the official iraqi vice president of the revolution in iraq ezzat ibrahim met here today wednesday in baghdad board chairman of the saudi center for developing export abdel rahman al-zamil .",
      "Baghdad 1-1 (AFP) - Iraq's official news agency reported that the Deputy Chairman of the Iraqi Revolutionary Command Council, Izzet Ibrahim, today met with Abdul Rahman al-Zamil, Managing Director of the Saudi Center for Export Development."));

    data.add(new Pair<String,String>
     ("agency reported that ibrahim welcomed the appropriate level of cooperation and trade relations between iraq and saudi arabia .",
      "The agency said Ibrahim welcomed this occasion for trade exchange and cooperation between Iraq and Saudi Arabia."));

    for(Map.Entry<String,Double> datum : score(data,"exact").entrySet()) {
       System.err.printf("pair: %s\nscore: %f\n\n", datum.getKey(), datum.getValue()); 
    }
  }

}
