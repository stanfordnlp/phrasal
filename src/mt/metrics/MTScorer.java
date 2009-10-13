package mt.metrics;

import java.util.*;
import java.io.*;

import mt.base.*;
import mt.metrics.ter.TERcost;
import mt.metrics.ter.TERcalc;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.ClassicCounter;

/**
 * Implementation of ExternalMTScorer for MetricsMATR.
 * 
 * @author Michel Galley
 */
public class MTScorer implements ExternalMTScorer {

  private static final TERcalc ter = new TERcalc();

  Properties prop;

  public static final int MAX_VALUE = 100;
  
  boolean verbose, tokenize = true, lowercase = true;
  boolean withNIST = true, withMETEOR = true;
  
  List<double[]> terCosts;
  Map<Sequence<IString>,Double> ngramInfo;
  Map<String,Double> scoresMETEOR;

  static public void main(String[] args) throws Exception {

    MTScorer scorer = new MTScorer();

    if(args.length < 2 || args.length > 3) {
      System.err.println("Usage: MTScorer <reference_file> <hypothesis_file> [<configuration_file>]");
      System.exit(1);
    }
    
    scorer.init(args.length == 3 ? args[2] : null);

    String[] refs = StringUtils.slurpFile(args[0]).split("[\r\n]+");
    String[] hyps = StringUtils.slurpFile(args[1]).split("[\r\n]+");
    assert(refs.length == hyps.length);

    List<Pair<String,String>> data = new ArrayList<Pair<String,String>>();
    for(int i=0; i<refs.length; ++i) {
      data.add(new Pair<String,String>(refs[i],hyps[i]));
    }
    scorer.readAllReferencesAndHypotheses(data);

    for(int i=0; i<refs.length; ++i) {

      System.out.printf("===========\nref=%s\nhyp=%s\n",refs[i],hyps[i]);
      Counter<String> c = scorer.scoreMTOutput(refs[i],hyps[i]);
      List<String> keys = Arrays.asList(c.keySet().toArray(new String[c.size()]));
      Collections.sort(keys);
      for(String k : keys)
        System.out.printf("%s\t%.3g\n",k,c.getCount(k));
      
    }
  }

  /**
   * Initialize MTScorer with properties read from configuration file.
   * Contract: this function is called immediately after construction.
   * 
   * @param configFile Configuraiton file name. String may be null, in which case
   * init() loads default properties.
   */
  public void init(String configFile) {

    prop = new Properties();

    // Load configuration file:
    if(configFile != null) {
      try {
        IOTools.addConfigFileProperties(prop, configFile);
      } catch(IOException ioe) {
        ioe.printStackTrace();
      }
    }

    verbose = Boolean.parseBoolean(prop.getProperty("verbose","false"));
    if(verbose) System.err.println("properties: "+prop.toString());

    // Initialize TER:
    terCosts = getTERCosts(prop.getProperty("terCosts",
         "1:1:1:1,"+ "1:1:1:0.1,"+ "1:1:0.1:1,"+ "1:0.5:1:1,"+
         "1:0.2:1:1,"+ "1:0.1:1:1,"+ "1:0.05:1:1,"+ "1:0.02:1:1,"+
         "1:0.01:1:1,"+ "0.1:1:1:1"));
    ter.setNormalize(true);
  }

  /**
   * Provides all references and hypotheses to MTScorer before the score of the
   * first sentence pair is computed. Some metrics, e.g., NIST, need to precompute
   * statistics over the whole set before it can compute the first score.
   * 
   * @param data List of all (reference,hypothese) pairs that need to be scored.
   */
  public void readAllReferencesAndHypotheses(List<Pair<String,String>> data) {
    
    if(withNIST) {
      List<List<Sequence<IString>>> refs = new ArrayList<List<Sequence<IString>>>();
      for(Pair<String,String> pair : data) {
        List<Sequence<IString>> r = new ArrayList<Sequence<IString>>();
        r.add(str2seq(pair.first()));
        refs.add(r);
      }
      NISTMetric<IString,String> nist = new NISTMetric<IString,String>(refs);
      ngramInfo = nist.getNgramInfo();
      assert(ngramInfo != null);
    }
    
    if(withMETEOR)
      scoresMETEOR = METEOR0_7Metric.score(data, null); 
  }

  public Counter<String> scoreMTOutput(String ref, String hyp) {

    Counter<String> c = new ClassicCounter<String>();

    // Create references:
    List<List<Sequence<IString>>> refS = new ArrayList<List<Sequence<IString>>>();
    refS.add(new ArrayList<Sequence<IString>>());
    refS.get(0).add(str2seq(ref));

    // Create hypotheses:
    Sequence<IString> hypS = str2seq(hyp);
    
    // Add BLEU features:
    BLEUMetric<IString,String>.BLEUIncrementalMetric sbleuI = new BLEUMetric<IString,String>(refS,true).getIncrementalMetric();
    sbleuI.add(new ScoredFeaturizedTranslation<IString, String>(hypS, null, 0));
    addNgramPrecisionScores(c,sbleuI);

    // Add NIST features:
    if(withNIST) {
      NISTMetric<IString,String> nist = new NISTMetric<IString,String>(refS);
      if(ngramInfo != null)
        nist.setNgramInfo(ngramInfo);
      else
        System.err.println("WARNING: readAllReferences apparently wasn't called.");
      NISTMetric<IString,String>.NISTIncrementalMetric nistI = nist.getIncrementalMetric();
      nistI.add(new ScoredFeaturizedTranslation<IString, String>(hypS, null, 0));
      addNgramPrecisionScores(c,nistI);
    }

    // Add METEOR features:
    if(withMETEOR) {
      Pair<String,String> pair = new Pair<String,String>(ref,hyp);
      Double score = scoresMETEOR.get(pair.toString());
      double dscore = 0.0;
      // we get an empty pair when we query scoresProvided()...
      if(score != null && !(pair.first().isEmpty() && pair.second().isEmpty()))
        dscore = score;
      else
        System.err.println("WARNING: unseen (ref,hyp) pair:\n"+pair.toString());
      addToCounter(c, "mt.metrics.METEOR", dscore);
    }

    // Add TER features:
    for(double[] q : terCosts)
      addTERScores(c, refS, hypS, q[0], q[1], q[2], q[3]);
    
    return c;
  }

  /**
   * Returns the list of features.
   * 
   * @return
   */
  public Set<String> scoresProvided() {
    Counter<String> c = scoreMTOutput("","");
    return c.keySet();
  }

  private Sequence<IString> str2seq(String str) {
    String[] strs;
    if(lowercase)
      str = str.toLowerCase();
    strs = tokenize ? ter.tokenize(str) : str.split("\\s+");
    return new SimpleSequence<IString>(true, IStrings.toIStringArray(strs));
  }

  private void addTERScores(Counter<String> c, List<List<Sequence<IString>>> ref, Sequence<IString> hyp,
                            double subCost, double insCost, double delCost, double shiftCost) {

    TERcost.set_default_substitute_cost(subCost);
    TERcost.set_default_insert_cost(insCost);
    TERcost.set_default_delete_cost(delCost);
    TERcost.set_default_shift_cost(shiftCost);
    
    TERMetric<IString,String>.TERIncrementalMetric terI = new TERMetric<IString,String>(ref,true).getIncrementalMetric();
    
    terI.add(new ScoredFeaturizedTranslation<IString, String>(hyp, null, 0));
    Formatter f = new Formatter();
    f.format("mt.metrics.TER_%g_%g_%g_%g",subCost,insCost,delCost,shiftCost);

    addToCounter(c,f.toString(),1.0+terI.score());
    addToCounter(c,f.toString()+"_ins",-safeLog(1.0+terI.insCount()));
    addToCounter(c,f.toString()+"_del",-safeLog(1.0+terI.delCount()));
    addToCounter(c,f.toString()+"_sub",-safeLog(1.0+terI.subCount()));
    addToCounter(c,f.toString()+"_sft",-safeLog(1.0+terI.sftCount()));
    
  }
  
  
	private void addNgramPrecisionScores(Counter<String> c, NgramPrecisionIncrementalMetric<IString, String> m) {

    String name = m.getClass().toString().replace("class ","").replaceFirst("\\$.*","");

    boolean isBLEU = name.equals("mt.metrics.BLEUMetric");
    boolean isNIST = name.equals("mt.metrics.NISTMetric");
    name = name.replace("Metric", "");

    double score = m.score();
    double bp = m.brevityPenalty();
    double[] precisions = m.precisions();

    if(isNIST)
      for(int i=0; i<precisions.length; ++i)
        precisions[i] /= 20.0;    
    
    // Add standard BLEU/NIST features:
    addToCounter(c,name+"_score",score);
    addToCounter(c,name+"_bp",bp);
    addToCounter(c,name+"_precision",score/bp);
    
    // Add Unsmoothed ngram precisions:
    for(int i=1; i<=4; ++i)
      addToCounter(c,name+"_"+i+"gram_precision",precisions[i-1]);

    // BLEU equation:
    double sum = 0;
    for(int i=1; i<=4; ++i) {
      sum += safeLog(precisions[i-1]);
      addToCounter(c,name+"_bleu"+i,bp*Math.exp(sum/i));
    }

    // NIST equation:
    sum = 0;
    for(int i=1; i<=precisions.length; ++i) {
      sum += precisions[i-1];
      addToCounter(c,name+"_nist"+i,bp*(sum/i));
    }

    // Smoothed BLEU:
    if(isBLEU) {
      precisions = ((BLEUMetric<IString, String>.BLEUIncrementalMetric)m).ngramPrecisions();
      for(int i=1; i<=4; ++i)
        addToCounter(c,name+"_"+i+"gram_smoothed_precision",precisions[i-1]);
      sum = 0;
      for(int i=1; i<=4; ++i) {
        sum += safeLog(precisions[i-1]);
        addToCounter(c,name+"_sbleu"+i,bp*Math.exp(1.0/i*sum));
      }
    }
  }

  private void addToCounter(Counter<String> c, String feature, double v) {
    if(Double.isNaN(v) || Double.isInfinite(v)) {
      if(verbose)
        System.err.printf("Feature %s has bad value %f.\n", feature, v);
      v = 0.0;
    }
    if(Math.abs(v) > MAX_VALUE)
      v = 0.0;
    c.setCount(feature,v);
    c.setCount(feature+"_log",-safeLog(v));
  }

  private List<double[]> getTERCosts(String str) {
    List<double[]> terCosts = new ArrayList<double[]>();
    for(String el : str.split(",")) {
      String[] costStr = el.split(":");
      if(costStr.length != 4)
        throw new RuntimeException("TER needs four costs.");
      double[] c = new double[4];
      for(int i=0; i<4; ++i)
        c[i] = Double.parseDouble(costStr[i]);
      terCosts.add(c);
      if(verbose)
        System.err.println("New set of TER costs: "+terCosts.toArray().toString());
    }
    return terCosts;
  }

  private double safeLog(double x) {
    if(x <= 0)
      return -100;
    return Math.log(x);
  }
}
