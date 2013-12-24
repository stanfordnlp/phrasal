package edu.stanford.nlp.mt.metrics;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.Arrays;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import com.bbn.mt.terp.TERalignment;
import com.bbn.mt.terp.TERcost;
import com.bbn.mt.terp.TERcalc;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * Sentence Level TERp Metric for use in online tuning.
 *
 * Possible todo: merge this with TERpMetric
 *
 * @author danielcer
 *
 * @param <TK>
 * @param <FV>
 */
public class SLTERpMetric<TK,FV> implements SentenceLevelMetric<TK, FV> {

  static final int EJECTION_HASH_SIZE = 100000;
  public static final int DEFAULT_BEAM_SIZE = 20;
  public static final boolean VERBOSE = false;

  private final TERcost terCost;
  private final int beamSize;
  

  public SLTERpMetric() {
    this(DEFAULT_BEAM_SIZE);
  } 

  public SLTERpMetric(int beamSize) {
    terCost = new TERcost();
    this.beamSize = beamSize; 
  }
 
  String[] ejectionHashStrings = new String[EJECTION_HASH_SIZE];
  double[] ejectionHashValues  = new double[EJECTION_HASH_SIZE];

  /**
    * Warning refWeights[] is ignored since this has no obvious interpretation for TER
    */
  @Override
  public double score(int sourceId, Sequence<TK> source, List<Sequence<TK>> references, Sequence<TK> translation) {
    TERcalc terCalc = new TERcalc(terCost);
    terCalc.BEAM_WIDTH = beamSize;
    Set<String> refs = new TreeSet<String>();

    // Take the min reference length
    int minLength = Integer.MAX_VALUE;
    for (Sequence<TK> sentence : references) {
      if (sentence.size() < minLength) {
        minLength = sentence.size();
      }
    }

    // don't score against duplicated references
    for (Sequence<TK> ref : references) {
       refs.add(ref.toString());
    }

    String hyp = translation.toString();
    double bestTER = Double.POSITIVE_INFINITY;
    for (String ref : refs) {
      String key = hyp + "|||" + ref;
      int hashIdx = key.hashCode() % EJECTION_HASH_SIZE;
      hashIdx = (hashIdx < 0 ? -hashIdx : hashIdx);
      synchronized(ejectionHashStrings) { 
        if (ejectionHashStrings[hashIdx] != null &&
            key.equals(ejectionHashStrings[hashIdx])) {
           double ter = ejectionHashValues[hashIdx];
           if (ter < bestTER) bestTER = ter; 
           continue;
        }
      }
      TERalignment align = terCalc.TER(hyp, ref);
      double ter = align.numEdits / align.numWords;
      if (VERBOSE) {
        System.err.printf("ref: %s\n", ref);
        System.err.printf("numEdits: %f\n", align.numEdits);
        System.err.printf("numWords: %f\n", align.numWords);
      }
      if (ter < bestTER) bestTER = ter; 
      synchronized(ejectionHashStrings) { 
         ejectionHashStrings[hashIdx] = key;
         ejectionHashValues[hashIdx] = ter;
      }
    }

    return -bestTER*minLength;
  }

  @Override
  public void update(int sourceId, List<Sequence<TK>> references,
      Sequence<TK> translation) {
  }

  @Override
  public boolean isThreadsafe() {
    return true;
  } 

  public static void main(String[] args) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    SLTERpMetric slTERp = new SLTERpMetric();

    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
      String[] fields = line.split("\\|\\|\\|");
      Sequence<IString> hyp = IStrings.tokenize(fields[0]);
      List<Sequence<IString>> refs = new ArrayList<Sequence<IString>>();
      for (int i = 1; i < fields.length; i++) {
        refs.add(IStrings.tokenize(fields[i]));
      }
      double[] rWeights = new double[refs.size()];
      Arrays.fill(rWeights, 1); 
      System.out.printf("ter: %.3f\n", slTERp.score(0, null, refs, hyp)*100);
    }
  }
}
