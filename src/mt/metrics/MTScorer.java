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

  boolean verbose;
  List<Quadruple<Double,Double,Double,Double>> terCosts;
  Map<Sequence<IString>,Double> ngramInfo = null;
  boolean tokenize = true, lowercase = true;
  boolean withNIST = true;

  // TODO: tuned linear combinations

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
    Properties prop = new Properties();
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
    if(withNIST) {
      List<List<Sequence<IString>>> refs = new ArrayList<List<Sequence<IString>>>();
      for(String ref : refStr) {
        List<Sequence<IString>> r = new ArrayList<Sequence<IString>>();
        r.add(str2seq(ref));
        refs.add(r);
      }
      NISTMetric<IString,String> nist = new NISTMetric<IString,String>(refs);
      ngramInfo = nist.getNgramInfo();
      assert(ngramInfo != null);
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
    BLEUMetric.BLEUIncrementalMetric bleuI = new BLEUMetric(ref).getIncrementalMetric();
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
    bleuI.add(new ScoredFeaturizedTranslation<IString, String>(hyp, null, 0));
    sbleuI.add(new ScoredFeaturizedTranslation<IString, String>(hyp, null, 0));
    if(withNIST)
      nistI.add(new ScoredFeaturizedTranslation<IString, String>(hyp, null, 0));
    // Add features:
    Counter<String> c = new ClassicCounter<String>();
    addNgramPrecisionScores(c,bleuI);
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
    TERMetric.TERIncrementalMetric terI = new TERMetric(ref).getIncrementalMetric();
    terI.add(new ScoredFeaturizedTranslation<IString, String>(hyp, null, 0));
    Formatter f = new Formatter();
    f.format("ter_score_%g_%g_%g_%g",subCost,insCost,delCost,shiftCost);
    addToCounter(c,f.toString(),1.0+terI.score());
  }
  
  private void addNgramPrecisionScores(Counter<String> c, NgramPrecisionIncrementalMetric m) {
    String name = m.getClass().toString().replace("class ","").replaceFirst("\\$.*","");
    double score = m.score();
    double bp = m.brevityPenalty();
    double[] precisions = m.precisions();
    addToCounter(c,name+"_score",score);
    addToCounter(c,name+"_bp",bp);
    addToCounter(c,name+"_precision",score/bp);
    addToCounter(c,"log_"+name+"_score",-Math.log(score));
    addToCounter(c,"log_"+name+"_bp",-Math.log(bp));
    addToCounter(c,"log_"+name+"_precision",-Math.log(score/bp));
    addToCounter(c,name+"_1gram_precision",precisions[0]);
    addToCounter(c,name+"_2gram_precision",precisions[1]);
    addToCounter(c,name+"_3gram_precision",precisions[2]);
    addToCounter(c,name+"_4gram_precision",precisions[3]);
  }

  @SuppressWarnings("unchecked")
  private void addToCounter(Counter<String> c, String feature, double v) {
    if(v != v) {
      if(verbose)
        System.err.printf("Feature %s has bad value %f.\n", feature, v);
      v = 0.0;
    }
    if(Math.abs(v) > 100)
      v = 0.0;
    c.setCount(feature,v);
  }
}
