package edu.stanford.nlp.mt.train;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.efeat.WordLevelDiscrimDistortionFeaturizer;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.stats.ClassicCounter;

/**
 * @author Pi-Chuan Chang
 */
public class WordPairDistortionStatisticsCollector implements StatisticsCollector {

  final boolean DETAILED_DEBUG = false;

  //private IBMModel1 model1 = null;
  private WordAligner waligner = null;
  private TwoDimensionalCounter<String,String> counterF1F2 = null;
  private TwoDimensionalCounter<String,String> counterF1 = null;
  private TwoDimensionalCounter<String,String> counterF2 = null;
  private ClassicCounter<String> distortionTypeCounter = null;

  // some variables for global statistics
  private int sentPair_skipped = 0;
  private int sentPair_processed = 0;
  private int fWords_aligned = 0;
  private int fWords_notAligned = 0;
  private int wordPair_notAligned = 0;
  private int wordPair_aligned = 0;

  // this is hard coded for now
  private final static String serializedFileName = "WordLevelStatistics2.ser.gz";

  private void initWordAligner() {
    //waligner = new IBMModel1InformedWordAligner();
    waligner = new ShortestDistanceWordAligner();
  }


  public WordPairDistortionStatisticsCollector() {
      // init TwoDimensionalCounter
      counterF1F2 = new TwoDimensionalCounter<String, String>();
      counterF1 = new TwoDimensionalCounter<String, String>();
      counterF2 = new TwoDimensionalCounter<String, String>();
      distortionTypeCounter = new ClassicCounter<String>();
  }


  public int getNumPasses() {
    return 1;
  }

  public void collect(WordAlignment sent) {
    Sequence<IString> f = sent.f();
    Sequence<IString> e = sent.e();

    if (f.size()>100 || e.size()>100) {
      sentPair_skipped++;
      return;
    } else {
      sentPair_processed++;
    }

    if (DETAILED_DEBUG) {
      System.err.println("collect some stats for: ");
      System.err.println("  f="+f);
      System.err.println("  f.class="+f.getClass().getName());
      System.err.println("  f(0).class="+f.get(0).getClass().getName());
      System.err.println("  e="+e);
      System.err.println("  e.class="+e.getClass().getName());
      System.err.println("  align="+sent);
    }
    

    /* this array records what is the one E that aligns to each f by the WordAligner */
    int[] ftoE = new int[f.size()];
    
    for(int fidx = 0; fidx < f.size(); fidx++) {
      // eidx is the one best English word for the Foreing word fidx
      // (according to whatever WordAligner you use) 
      if (waligner == null) initWordAligner();
      int eidx = waligner.getAlignedEnglishIndex(sent, fidx);
      ftoE[fidx] = eidx;
      if (eidx < 0) {
        fWords_notAligned++;
      } else {
        fWords_aligned++;
      }

      if (DETAILED_DEBUG) {
        System.err.printf("f(%d)=%s\n", fidx, f.get(fidx));
        
        if (eidx >= 0) {
          System.err.printf("e(%d)=%s\n", eidx, e.get(eidx));
        } else {
          System.err.printf("e=not aligned\n");
        }
        System.err.println("===================================");
      }

    }

    
    for(int curr=0; curr < f.size()-1; curr++) {
      int next = curr+1;
      int eCurr = ftoE[curr];
      int eNext = ftoE[next];

      // one of the word pair is not aligned, skip
      if (eCurr < 0 || eNext < 0) {
        wordPair_notAligned++;
        continue;
      } else {
        wordPair_aligned++;
      }
      int distance = eNext - eCurr;

      /*
      if (distance >= 90 || distance <= -90) {
        System.err.println("Warning: distance="+distance);
        System.err.println("f = "+f);
        System.err.println("e = "+e);
        System.err.println("a = "+sent);
        System.err.printf("f[%d]=%s --> e[%d]=%s\n", curr, f.get(curr), eCurr, e.get(eCurr));
        System.err.printf("f[%d]=%s --> e[%d]=%s\n", next, f.get(next), eNext, e.get(eNext));
      }
      */

      //String distortionType = edu.stanford.nlp.mt.ExperimentalFeaturizers.WordLevelDiscrimDistortionFeaturizer.getDistortionType(distance);
      String distortionType = WordLevelDiscrimDistortionFeaturizer.getDistortionTypeAll(distance);

      IString f_first  = f.get(curr);
      IString f_second = f.get(next);
      if (DETAILED_DEBUG) System.err.printf("%s\t%s\t%s\n", f_first, f_second, distortionType);
      StringBuilder f1f2 = new StringBuilder();
      f1f2.append(f_first).append("-").append(f_second);
      counterF1F2.incrementCount(f1f2.toString(), distortionType);
      counterF1.incrementCount(f_first.toString(), distortionType);
      counterF2.incrementCount(f_second.toString(), distortionType);
      distortionTypeCounter.incrementCount(distortionType);
    }
  }

  public void postProcess() {
    System.err.println("----------postProcess step for WordPairDistortionStatisticsCollector----------");
    System.err.println("0) Settings");
    System.err.println("0.1) the WordAligner used is : "+waligner.getClass().getName());
    System.err.println();
    System.err.println("1) Output some global statistics");
    System.err.println("1.1) Sentence Pairs processed = "+sentPair_processed);
    System.err.println("1.2) Sentence Pairs skipped   = "+sentPair_skipped);
    System.err.println("1.3) Word Pairs not counted   = "+wordPair_notAligned);
    System.err.println("1.4) Word Pairs counted       = "+wordPair_aligned);
    System.err.printf ("1.5) Word Pairs total         = %d\n",wordPair_aligned+wordPair_notAligned);
    System.err.println("1.6) Foreign words that are aligned = "+fWords_aligned);
    System.err.printf ("1.7) Foreign words that are NOT aligned = %d , perc = %.3f%%\n",
                      fWords_notAligned, 
                      (double)fWords_notAligned/(fWords_notAligned+fWords_aligned)*100);
    System.err.println("1.8) counterF1F2.size = "+counterF1F2.size());
    System.err.println("1.9) counterF1.size = "+counterF1.size());
    System.err.println("1.10) counterF2.size = "+counterF2.size());
    System.err.println("1.11) distortionTypeCounter #type = "+distortionTypeCounter.size());
    System.err.println("1.12) distortionTypeCounter totalCounts = "+distortionTypeCounter.totalCount());
    System.err.println("1.10) distortionTypeCounter = "+distortionTypeCounter);
    System.err.println();
    System.err.println("2) Serialize the counters to a file");
    serializeTo(serializedFileName);
    System.err.println();
    System.err.println("3) Aligned Words count (DEBUG)");
    double total=0;
    System.err.printf("3.1) INTERSECTED = %d (%.3f)\n",
                      ShortestDistanceWordAligner.INTERSECTED,
                      (double)ShortestDistanceWordAligner.INTERSECTED/ShortestDistanceWordAligner.TOTAL*100);
    total+=ShortestDistanceWordAligner.INTERSECTED;
    /*
    for(int i = 0; i < ShortestDistanceWordAligner.MAXSTEP; i++) {
      System.err.printf("3.2.%d) STEPINTERSECTED[%d] = %d (%.3f)\n", 
                        i+1, 
                        i,
                        ShortestDistanceWordAligner.STEPINTERSECTED[i],
                        ShortestDistanceWordAligner.STEPINTERSECTED[i]/total*100);
      total+=ShortestDistanceWordAligner.STEPINTERSECTED[i];
      }*/
    System.err.printf("3.2) APPROXFOUND = %d (%.3f)\n",
                      ShortestDistanceWordAligner.APPROXFOUND,
                      (double)ShortestDistanceWordAligner.APPROXFOUND/ShortestDistanceWordAligner.TOTAL*100);
    total+=ShortestDistanceWordAligner.APPROXFOUND;

    System.err.printf("3.3) NOT = %d (%.3f)\n",
                      ShortestDistanceWordAligner.NOT,
                      (double)ShortestDistanceWordAligner.NOT/ShortestDistanceWordAligner.TOTAL*100);
    total+=ShortestDistanceWordAligner.NOT;
    System.err.printf("3.4) COMMA = %d (%.3f)\n",
                      ShortestDistanceWordAligner.COMMA,
                      (double)ShortestDistanceWordAligner.COMMA/ShortestDistanceWordAligner.TOTAL*100);
    total+=ShortestDistanceWordAligner.COMMA;
    System.err.printf("3.5) total = %d\n",(int)total);
    System.err.printf("3.6) TOTAL = %d\n",ShortestDistanceWordAligner.TOTAL);
  }
  
  public void serializeTo(String filename) {
    System.err.print("Serializing statistics to " + filename + "...");
    long startTimeMillis = System.currentTimeMillis();

    try {
      ObjectOutputStream oos = IOUtils.writeStreamFromString(filename);

      oos.writeObject(counterF1F2);
      oos.writeObject(counterF1);
      oos.writeObject(counterF2);
      oos.writeObject(distortionTypeCounter);

      oos.close();
      double totalSecs = (System.currentTimeMillis() - startTimeMillis)/1000.0;
      System.err.printf("done. Time = %.3f secs\n", totalSecs);

    } catch (Exception e) {
      System.err.println("Failed");
      e.printStackTrace();
      System.exit(1);
    }
  }


  public static WordPairDistortionStatisticsCollector load(String filename) {
    WordPairDistortionStatisticsCollector c = new WordPairDistortionStatisticsCollector();
    c.loadFrom(filename);
    return c;
  }
  
  @SuppressWarnings("unchecked")
  public void loadFrom(String filename) {
    try {
      ObjectInputStream ois = IOUtils.readStreamFromString(filename);
      
      counterF1F2  = (TwoDimensionalCounter<String, String>) ois.readObject();
      counterF1    = (TwoDimensionalCounter<String, String>) ois.readObject();
      counterF2    = (TwoDimensionalCounter<String, String>) ois.readObject();
      distortionTypeCounter = (ClassicCounter<String>) ois.readObject();
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  public static void main(String[] args) {
    //String filename = serializedFileName;
    String filename = args[0];
    String newSerializedFile = args[1];

    long startTimeMillis = System.currentTimeMillis();
    System.err.println("load from "+filename);
    WordPairDistortionStatisticsCollector c = WordPairDistortionStatisticsCollector.load(serializedFileName);
    double totalSecs = (System.currentTimeMillis() - startTimeMillis)/1000.0;
    System.err.printf("done loading. Time = %.3f secs\n", totalSecs);

    System.err.println("counterF1F2.size="+c.counterF1F2.size());
    System.err.println("counterF1.size="+c.counterF1.size());
    System.err.println("counterF2.size="+c.counterF2.size());
    System.err.println("distortionTypeCounter.size="+c.distortionTypeCounter.size());
    System.err.println("distortionTypeCounter="+c.distortionTypeCounter);
    System.err.println("distortionTypeCounter.all="+c.distortionTypeCounter.totalCount());

    ClassicCounter<String> newDistortionTypeCounter = new ClassicCounter<String>();
    for(String distance : c.distortionTypeCounter.keySet()) {
      int count = (int)c.distortionTypeCounter.getCount(distance);
      // the "distance string" is always in format of "=%d"
      int dis = 0;
      try {
        dis = Integer.parseInt(distance.substring(1));
      } catch (Exception e) {
        e.printStackTrace();
        System.exit(1);
      }
      newDistortionTypeCounter.incrementCount(WordLevelDiscrimDistortionFeaturizer.getDistortionType(dis), count);
    }

    c.distortionTypeCounter = newDistortionTypeCounter;
    c.serializeTo(newSerializedFile);
    System.err.println("counterF1F2.size="+c.counterF1F2.size());
    System.err.println("counterF1.size="+c.counterF1.size());
    System.err.println("counterF2.size="+c.counterF2.size());
    System.err.println("distortionTypeCounter.size="+c.distortionTypeCounter.size());
    System.err.println("distortionTypeCounter="+c.distortionTypeCounter);
    System.err.println("distortionTypeCounter.all="+c.distortionTypeCounter.totalCount());

    //int numKeys = edu.stanford.nlp.mt.ExperimentalFeaturizers.WordLevelDiscrimDistortionFeaturizer.getNumDistortionType();
    /*
    for(Map.Entry<String,Counter<String>> e : c.counterF1F2.entrySet()) {
      System.err.println(e.getKey());
      //Distribution<String> d = Distribution.goodTuringSmoothedCounter(e.getValue(), numKeys);
      Distribution<String> d = Distribution.getDistribution(e.getValue());
      System.err.println(d);
      System.err.println("============================================");
    }

    for(Map.Entry<String,Counter<String>> e : c.counterF1.entrySet()) {
      System.err.println(e.getKey());
      Distribution<String> d = Distribution.getDistribution(e.getValue());
      System.err.println(d);
      System.err.println("============================================");
    }
    */
  }
}
