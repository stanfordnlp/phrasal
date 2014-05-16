package edu.stanford.nlp.mt.metrics;

import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Arrays;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import com.bbn.mt.terp.TERalignment;
import com.bbn.mt.terp.TERcost;
import com.bbn.mt.terp.TERcalc;

import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.Sequence;

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

  @Override
  public double score(int sourceId, Sequence<TK> source, List<Sequence<TK>> references, Sequence<TK> translation) {
    TERcalc terCalc = new TERcalc(terCost);
    terCalc.BEAM_WIDTH = beamSize;
    
    // uniq references to prevent (expensive) redundant calculation.
    Set<Sequence<TK>> uniqRefs = new HashSet<Sequence<TK>>(references);
    
    final String hyp = translation.toString();
    double bestTER = Double.POSITIVE_INFINITY;
    int lengthScale = Integer.MAX_VALUE;
    for (Sequence<TK> refSeq : uniqRefs) {
      double ter;
      if (refSeq.size() == 0) {
        ter = translation.size();
      } else {
        String ref = refSeq.toString();
        TERalignment align = terCalc.TER(hyp, ref);
        ter = align.numEdits / align.numWords;
        if (VERBOSE) {
          System.err.printf("ref: %s%n", ref);
          System.err.printf("numEdits: %f%n", align.numEdits);
          System.err.printf("numWords: %f%n", align.numWords);
        }        
      }
      if (ter < bestTER) {
        bestTER = ter;
        lengthScale = refSeq.size() == 0 ? 1 : refSeq.size();
      }
    }
    
    return -bestTER*lengthScale;
  }

  @Override
  public void update(int sourceId, List<Sequence<TK>> references,
      Sequence<TK> translation) {
  }

  @Override
  public boolean isThreadsafe() {
    return true;
  } 

  /**
   * For debugging.
   * 
   * @param args
   * @throws IOException
   */
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
