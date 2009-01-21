package mt.metrics;

import java.util.*;
import java.io.*;

import mt.base.*;
import mt.reranker.ter.TERcost;
import mt.reranker.ter.TERcalc;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.ClassicCounter;

/**
 * Implementation of ExternalMTScorer for MetricsMATR.
 * 
 * @author Michel Galley
 */
public class MTScorer implements ExternalMTScorer {

  public static final int MAX_VALUE = 100;
  Properties prop;

  boolean verbose;

  List<Quadruple<Double,Double,Double,Double>> terCosts;
  Map<Sequence<IString>,Double> ngramInfo = null;
  
  boolean tokenize = true, lowercase = true;
  
  boolean withNIST = true;
  boolean withMETEOR = true;

  static public void main(String[] args) throws Exception {
    MTScorer scorer = new MTScorer();
    if(args.length < 2 || args.length > 3) {
      System.err.println("Usage: MTScorer <reference_file> <hypothesis_file> [<configuration_file>]");
      System.exit(1);
    }
    scorer.init(args.length == 3 ? args[2] : null);
    String[] refs = StringUtils.slurpFile(args[0]).split("[\r\n]+");
    String[] hyps = StringUtils.slurpFile(args[1]).split("[\r\n]+");
    scorer.readAllReferences(Arrays.asList(refs));
    assert(refs.length == hyps.length);
    for(int i=0; i<refs.length; ++i) {
      System.out.printf("===========\nref=%s\nhyp=%s\n",refs[i],hyps[i]);
      Counter<String> c = scorer.scoreMTOutput(refs[i],hyps[i]);
      List<String> keys = Arrays.asList(c.keySet().toArray(new String[c.size()]));
      Collections.sort(keys);
      for(String k : keys)
        System.out.printf("%s\t%.3g\n",k,c.getCount(k));
    }
  }

  public void init(String configFile) {
    prop = new Properties();
    if(configFile != null) {
      try {
        IOTools.addConfigFileProperties(prop, configFile);
      } catch(IOException ioe) {
        ioe.printStackTrace();
      }
    }
    verbose = Boolean.parseBoolean(prop.getProperty("verbose","false"));
    if(verbose)
      System.err.println("properties: "+prop.toString());
    terCosts = getTERCosts(prop.getProperty("terCosts",
         "1:1:1:1,"+
         "1:1:1:0.1,"+
         "1:1:0.1:1,"+
         "1:0.5:1:1,"+
         "1:0.2:1:1,"+
         "1:0.1:1:1,"+
         "1:0.05:1:1,"+
         "1:0.02:1:1,"+
         "1:0.01:1:1,"+
         "0.1:1:1:1"));
    TERcalc.setNormalize(true);
  }

  public void readAllReferences(List<String> refStr) {
  }

  public void readAllReferencesAndHypotheses(List<Pair<String,String>> refAndHyp) {
    if(withNIST) {
      List<List<Sequence<IString>>> refs = new ArrayList<List<Sequence<IString>>>();
      for(Pair<String,String> pair : refAndHyp) {
        List<Sequence<IString>> r = new ArrayList<Sequence<IString>>();
        r.add(str2seq(pair.first()));
        refs.add(r);
      }
      NISTMetric<IString,String> nist = new NISTMetric<IString,String>(refs);
      ngramInfo = nist.getNgramInfo();
      assert(ngramInfo != null);
    }
    if(withMETEOR) {
      
    }
  }

  public List<Quadruple<Double,Double,Double,Double>> getTERCosts(String str) {
    List<Quadruple<Double,Double,Double,Double>> terCosts = new ArrayList<Quadruple<Double,Double,Double,Double>>();
    for(String el : str.split(",")) {
      String[] costStr = el.split(":");
      if(costStr.length != 4)
        throw new RuntimeException("TER needs four costs.");
      double[] c = new double[4];
      for(int i=0; i<4; ++i)
        c[i] = Double.parseDouble(costStr[i]);
      terCosts.add(new Quadruple<Double,Double,Double,Double>(c[0],c[1],c[2],c[3]));
      if(verbose)
        System.err.println("New set of TER costs: "+terCosts.toArray().toString());
    }
    return terCosts;
  }

  @SuppressWarnings("unchecked")
  public Counter<String> scoreMTOutput(String reference, String mtoutput) {
    // Create references:
    List<List<Sequence<IString>>> ref = new ArrayList<List<Sequence<IString>>>();
    ref.add(new ArrayList<Sequence<IString>>());
    ref.get(0).add(str2seq(reference));
    // Create hypotheses:
    Sequence<IString> hyp = str2seq(mtoutput);
    // Create metrics:
    BLEUMetric.BLEUIncrementalMetric sbleuI = new BLEUMetric(ref,true).getIncrementalMetric();
    NISTMetric nist = null;
    NISTMetric.NISTIncrementalMetric nistI = null;
    if(withNIST) {
        nist = new NISTMetric(ref);
      if(ngramInfo != null)
        nist.setNgramInfo(ngramInfo);
      else
        System.err.println("WARNING: readAllReferences apparently wasn't called.");
      nistI = nist.getIncrementalMetric();
    }
    sbleuI.add(new ScoredFeaturizedTranslation<IString, String>(hyp, null, 0));
    if(withNIST)
      nistI.add(new ScoredFeaturizedTranslation<IString, String>(hyp, null, 0));
    // Add features:
    Counter<String> c = new ClassicCounter<String>();
    addNgramPrecisionScores(c,sbleuI);
    if(withNIST)
      addNgramPrecisionScores(c,nistI);
    for(Quadruple<Double,Double,Double,Double> q : terCosts)
      addTERScores(c,ref,hyp,q.first(),q.second(),q.third(),q.fourth());
    return c;
  }

  public Set<String> scoresProvided() {
    Counter<String> c = scoreMTOutput("","");
    return c.keySet();
  }

  private Sequence<IString> str2seq(String str) {
    String[] strs;
    if(lowercase)
      str = str.toLowerCase();
    strs = tokenize ? TERcalc.tokenize(str) : str.split("\\s+");
    return new SimpleSequence<IString>(true, IStrings.toIStringArray(strs));
  }

  @SuppressWarnings("unchecked")
  private void addTERScores(Counter<String> c, List<List<Sequence<IString>>> ref, Sequence<IString> hyp,
                            double subCost, double insCost, double delCost, double shiftCost) {
    TERcost.set_default_substitute_cost(subCost);
    TERcost.set_default_insert_cost(insCost);
    TERcost.set_default_delete_cost(delCost);
    TERcost.set_default_shift_cost(shiftCost);
    TERMetric.TERIncrementalMetric terI = new TERMetric(ref,true).getIncrementalMetric();
    terI.add(new ScoredFeaturizedTranslation<IString, String>(hyp, null, 0));
    Formatter f = new Formatter();
    f.format("ter_score_%g_%g_%g_%g",subCost,insCost,delCost,shiftCost);
    addToCounter(c,f.toString(),1.0+terI.score());
    addToCounter(c,f.toString()+"_ins",-safeLog(1.0+terI.insCount()));
    addToCounter(c,f.toString()+"_del",-safeLog(1.0+terI.delCount()));
    addToCounter(c,f.toString()+"_sub",-safeLog(1.0+terI.subCount()));
    addToCounter(c,f.toString()+"_sft",-safeLog(1.0+terI.sftCount()));
  }
  
  private void addNgramPrecisionScores(Counter<String> c, NgramPrecisionIncrementalMetric<String, String> m) {
    String name = m.getClass().toString().replace("class ","").replaceFirst("\\$.*","");
    boolean isBLEU = name.equals("mt.metrics.BLEUMetric");
    boolean isNIST = name.equals("mt.metrics.NISTMetric");
    double score = m.score();
    double bp = m.brevityPenalty();
    double[] precisions = m.precisions();
    if(isNIST)
      for(int i=0; i<precisions.length; ++i)
        precisions[i] /= 20.0;
    addToCounter(c,name+"_score",score);
    addToCounter(c,name+"_bp",bp);
    addToCounter(c,name+"_precision",score/bp);
    // Unsmoothed ngram precisions:
    for(int i=1; i<=4; ++i)
      addToCounter(c,name+"_"+i+"gram_precision",precisions[i-1]);
    double sum = 0;
    // BLEU equation:
    for(int i=1; i<=4; ++i) {
      sum += safeLog(precisions[i-1]);
      addToCounter(c,name+"_bleu"+i,bp*Math.exp(sum/i));
    }
    sum = 0;
    // NIST equation:
    for(int i=1; i<=precisions.length; ++i) {
      sum += precisions[i-1];
      addToCounter(c,name+"_nist"+i,bp*(sum/i));
    }
    if(isBLEU) {
      // Smoothed BLEU:
      precisions = ((BLEUMetric.BLEUIncrementalMetric)m).smoothNgramPrecisions();
      for(int i=1; i<=4; ++i)
        addToCounter(c,name+"_"+i+"gram_smoothed_precision",precisions[i-1]);
      sum = 0;
      for(int i=1; i<=4; ++i) {
        sum += safeLog(precisions[i-1]);
        addToCounter(c,name+"_sbleu"+i,bp*Math.exp(1.0/i*sum));
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void addToCounter(Counter<String> c, String feature, double v) {
    if(v != v) {
      if(verbose)
        System.err.printf("Feature %s has bad value %f.\n", feature, v);
      v = 0.0;
    }
    if(Math.abs(v) > MAX_VALUE)
      v = 0.0;
    c.setCount(feature,v);
    c.setCount(feature+"_log",-safeLog(v));
  }

  private double safeLog(double x) {
    if(x <= 0)
      return -100;
    return Math.log(x);
  }
}
