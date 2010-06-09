package edu.stanford.nlp.mt.train;
//Object2IntOpenHashMap
//it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<K>


import edu.stanford.nlp.util.StringUtils;

import java.util.*;
import java.io.*;
import java.util.zip.GZIPInputStream;


/**
 * go through the SentencePairs and collect global information
 * General structure following PhraseExtract class
 *
 * @author Pi-Chuan Chang
 */
public class CombinedStatisticsCollector {

  public static final String DEBUG_PROPERTY = "CombinedStatisticsCollector";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  private List<StatisticsCollector> collectors;

  public CombinedStatisticsCollector(List<StatisticsCollector> collectors) {
    this.collectors = collectors;
  }

  private static LineNumberReader getReaderFromFileName(String name) {
    // Add this to StringUtils once using the main branch of javanlp:
    LineNumberReader reader = null;
    File f = new File(name);
    try {
      if (f.getAbsolutePath().endsWith(".gz")) {
        reader = new LineNumberReader
          (new InputStreamReader(new GZIPInputStream(new FileInputStream(f))));
      } else {
        reader = new LineNumberReader(new FileReader(f));
      }
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
    }
    return reader;
  }

  /**
   * Make as many passes over training data as needed to extract features.
   *
   */
  public void collectFromTrainingCorpus(String fCorpus, String eCorpus, String a1Corpus, String a2Corpus, int numLines) {
    
    long startTimeMillis = System.currentTimeMillis();
    long startStepTimeMillis = startTimeMillis;

    DualWordAlignment sent = new DualWordAlignment();

    try {
      for(int passNumber=0; passNumber<getNumPasses(); ++passNumber) {
        System.err.printf("Pass %d on training data)...\n", passNumber+1);
        LineNumberReader
          fReader = getReaderFromFileName(fCorpus),
          eReader = getReaderFromFileName(eCorpus),
          a1Reader = getReaderFromFileName(a1Corpus),
          a2Reader = getReaderFromFileName(a2Corpus);
        int line=0;
        for (String fLine;; ++line) {
          fLine = fReader.readLine();
          boolean done = (fLine == null || line == numLines);
          if(line % 1000 == 0 || done) {
            long totalMemory = Runtime.getRuntime().totalMemory()/(1<<20);
            long freeMemory = Runtime.getRuntime().freeMemory()/(1<<20);
            double totalStepSecs = (System.currentTimeMillis() - startStepTimeMillis)/1000.0;
            startStepTimeMillis = System.currentTimeMillis();
            System.err.printf("line %d (secs = %.3f, totalmem = %dm, freemem = %dm)...\n",
                              line, totalStepSecs, totalMemory, freeMemory);
          }
          if(done)
            break;
          String eLine = eReader.readLine();
          if(eLine == null)
            throw new IOException("Target-language corpus is too short!");
          String a1Line = a1Reader.readLine(),
            a2Line = a2Reader.readLine();
          if(a1Line == null)
            throw new IOException("Alignment file 1 is too short!");
          if(a2Line == null)
            throw new IOException("Alignment file 2 is too short!");
          if(a1Line.equals("") && a2Line.equals(""))
            continue;
          (sent).init(fLine,eLine,a1Line,a2Line);
          statsCollect(sent);
        }
        if(eReader.readLine() != null && numLines < 0)
          throw new IOException("Target-language corpus contains extra lines!");
        if(a1Reader.readLine() != null && numLines < 0)
          throw new IOException("Alignment file contains extra lines!");
        fReader.close();
        eReader.close();
        a1Reader.close();

        statsPostProcess();

        double totalTimeSecs = (System.currentTimeMillis() - startTimeMillis)/1000.0;
        System.err.printf("Done with pass %d. Seconds: %.3f.\n", passNumber+1, totalTimeSecs);
      }
    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  private void statsPostProcess() {
    for(StatisticsCollector c : collectors)
      c.postProcess();
  }

  private void statsCollect(DualWordAlignment sent) {
    for(StatisticsCollector c : collectors)
      c.collect(sent);
  }


  private int getNumPasses() {
    int maxP=0;
    for(StatisticsCollector cl : collectors) {
      int p = cl.getNumPasses();
      if(p > maxP)
        maxP = p;
    }
    return maxP;
  }
  
  public static void usage() {
    System.err.print
      ("Usage: java CombinedStatisticsCollector [ARGS]\n"+
       "Mandatory arguments:\n"+
       " -fCorpus <file> : source-language corpus\n"+ 
       " -eCorpus <file> : target-language corpus\n"+
       " -align1 <file> : alignment file 1\n"+
       " -align2 <file> : alignment file 2\n"+
       " -collectors <class1> [<class2> ... <classN>]\n"+
       " -numLines <n> : number of lines to process (<0 : all)\n"+
       " -noWrite : do not write features to stdout (only useful for reading log files, debugging, etc.)\n");
    System.exit(1);
  }
  
  public static void main(String[] args) {
    Properties prop = StringUtils.argsToProperties(args);
    String fCorpus = prop.getProperty("fCorpus");
    String eCorpus = prop.getProperty("eCorpus");
    String align1 = prop.getProperty("align1");
    String align2 = prop.getProperty("align2");
    int numLines = Integer.parseInt(prop.getProperty("numLines","-1"));
    String clsString = prop.getProperty("collectors");
    if(fCorpus == null || eCorpus == null || align1 == null || align2 == null || clsString == null)
      usage();
    
    List<StatisticsCollector> collectors = new ArrayList<StatisticsCollector>();

    for(String clStr : clsString.split("\\s+")) {
      try {
      	@SuppressWarnings("unchecked")
        Class<StatisticsCollector> ct = (Class<StatisticsCollector>)Class.forName(clStr);
        StatisticsCollector fe = ct.newInstance();
        collectors.add(fe);
        System.err.println("New class instance: "+fe.getClass());
      } catch (Exception e) {
        e.printStackTrace();
        usage();
      }
    }
    
    CombinedStatisticsCollector combined = new CombinedStatisticsCollector(collectors);
    combined.collectFromTrainingCorpus(fCorpus, eCorpus, align1, align2, numLines);
    // TODO revise
    //if(!noWrite)
    //combined.write(System.out);
  }
}
