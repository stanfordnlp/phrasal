package edu.stanford.nlp.mt.metrics;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.mt.decoder.recomb.RecombinationFilter;
import edu.stanford.nlp.mt.decoder.util.State;
import edu.stanford.nlp.mt.tools.NISTTokenizer;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.NBestListContainer;
import edu.stanford.nlp.mt.util.ScoredFeaturizedTranslation;
import edu.stanford.nlp.mt.util.Sequence;

import com.bbn.mt.terp.PhraseTable;
import com.bbn.mt.terp.TERcalc;
import com.bbn.mt.terp.TERcost;
import com.bbn.mt.terp.TERalignment;
import com.bbn.mt.terp.phrasedb.PhraseDB;
import com.bbn.mt.terp.WordNet;
import com.bbn.mt.terp.TERpara;
import com.bbn.mt.terp.NormalizeText;

/**
 * Implementation of the TERp metric (Snover et al., 2009). If invoked from the command line,
 * applies NIST tokenization to the input before computing the score.
 * 
 * @author Daniel Cer
 *
 * @param <TK>
 * @param <FV>
 */
public class TERpMetric<TK, FV> extends AbstractMetric<TK, FV> {
  final List<List<Sequence<TK>>> referencesList;

  enum EditType {
    ins, del, sub, sft
  }

  private boolean countEdits = false;
  private int beamWidth = 20;
  private int maxShiftDist = 50;

  public TERpMetric(List<List<Sequence<TK>>> referencesList, boolean countEdits) {
    this.referencesList = referencesList;
    this.countEdits = countEdits;
    WordNet.setWordNetDB(TERpara.para().get_string(
        TERpara.OPTIONS.WORDNET_DB_DIR));
    NormalizeText.init();
  }

  public TERpMetric(List<List<Sequence<TK>>> referencesList,
      boolean countEdits, boolean terpa) {
    this.referencesList = referencesList;
    this.countEdits = countEdits;
    if (terpa) {
      TERpara.getOpts(new String[] {
          "/u/nlp/packages/TERp/terp.v1/data/terpa.param",
          "/u/nlp/packages/TERp/terp.v1/data/data_loc.param", "-r", "dyn",
          "-h", "dyn" });
    }
    WordNet.setWordNetDB(TERpara.para().get_string(
        TERpara.OPTIONS.WORDNET_DB_DIR));
    String phrasedbFn = TERpara.para().get_string(TERpara.OPTIONS.PHRASE_DB);
    if (phrasedbFn != null && !(phrasedbFn.equals(""))) {
      // System.err.printf("loading phrasedb\n");
      PhraseDB phrasedb = new PhraseDB(phrasedbFn);
      phrasedb.openDB();
      this.phrasetable = new PhraseTable(phrasedb);      
    }
    NormalizeText.init();
  }

  public TERpMetric(List<List<Sequence<TK>>> referencesList) {
    this(referencesList, 0, 0);
  }

  public TERpMetric(List<List<Sequence<TK>>> referencesList, int beamWidth,
      int maxShiftDist) {
    this.referencesList = referencesList;
    if (beamWidth > 0)
      this.beamWidth = beamWidth;
    if (maxShiftDist > 0)
      this.maxShiftDist = maxShiftDist;
    WordNet.setWordNetDB(TERpara.para().get_string(
        TERpara.OPTIONS.WORDNET_DB_DIR));
    NormalizeText.init();
  }

  /**
   * Compute the sentence-level TER score (pseudo-percentage).
   * @param translation
   * @param references
   * @return
   */
  public static <TK> double computeLocalTERScore(Sequence<TK> translation, List<Sequence<TK>> references) {
    TERcalc terCalc = new TERcalc(new TERcost());
    terCalc.BEAM_WIDTH = 20;
    
    // uniq references to prevent (expensive) redundant calculation.
    Set<Sequence<TK>> uniqRefs = new HashSet<Sequence<TK>>(references);

    final String hyp = translation.toString();
    double bestTER = Double.POSITIVE_INFINITY;
    for (Sequence<TK> refSeq : uniqRefs) {
      String ref = refSeq.toString();
      TERalignment align = terCalc.TER(hyp, ref);
      double ter = align.numEdits / align.numWords;
      if (ter < bestTER) {
        bestTER = ter;
      }
    }
    
    return bestTER;
  }
  
  @Override
  public TERpIncrementalMetric getIncrementalMetric() {
    return new TERpIncrementalMetric();
  }

  @Override
  public IncrementalEvaluationMetric<TK, FV> getIncrementalMetric(
      NBestListContainer<TK, FV> nbestList) {
    throw new UnsupportedOperationException();
  }

  @Override
  public RecombinationFilter<IncrementalEvaluationMetric<TK, FV>> getIncrementalMetricRecombinationFilter() {
    throw new UnsupportedOperationException();
  }

  @Override
  public double maxScore() {
    return 0.0;
  }

  PhraseTable phrasetable;

  TERcost costfunc = new TERcost();
  
  public TERalignment calcTER(ScoredFeaturizedTranslation<TK, FV> trans,
      int idx, double[] editCounts) {
    return calcTER(trans, idx, editCounts);
  }

  public TERalignment calcTER(Sequence<TK> trans,
      int idx, double[] editCounts) {
    List<Sequence<TK>> refsSeq = referencesList.get(idx);
    String[] refs = new String[refsSeq.size()];
    TERalignment bestAl = null;

    double best = Double.POSITIVE_INFINITY;
    String hyp = trans.toString();
    for (int i = 0; i < refs.length; i++) {
      refs[i] = refsSeq.get(i).toString();
    }      
    TERcalc calc = new TERcalc(costfunc);
    costfunc.setPhraseTable(phrasetable);     
    calc.BEAM_WIDTH = beamWidth;
    calc.setShiftSize(maxShiftDist);
    int totalWords = 0;
    for (Sequence<TK> ref : refsSeq) {
      TERalignment terAl = calc.TER(hyp, ref.toString());
      totalWords += terAl.numWords;
      if (terAl.numEdits < best) {
        best = terAl.numEdits;
        bestAl = terAl;
      }
    }
    assert (bestAl != null);
    bestAl.numWords = totalWords / (double) refs.length;

    if (editCounts != null) {
      bestAl.scoreDetails();
      editCounts[EditType.ins.ordinal()] += bestAl.numIns;
      editCounts[EditType.del.ordinal()] += bestAl.numDel;
      editCounts[EditType.sub.ordinal()] += bestAl.numSub;
      editCounts[EditType.sft.ordinal()] += bestAl.numSft;
    }

    return bestAl;
  }

  public class TERpIncrementalMetric implements
      IncrementalEvaluationMetric<TK, FV> {
    TERalignment[] aligns = new TERalignment[referencesList.size()];
    boolean[] nulls = new boolean[referencesList.size()];
    double[] editCounts = null;

    double editsTotal = 0;
    double numWordsTotal = 0;
    int cnt = 0;
    int nullCnt = 0;

    public TERpIncrementalMetric() {
      if (countEdits)
        editCounts = new double[EditType.values().length];
    }

    public TERpIncrementalMetric(TERpIncrementalMetric p) {
      aligns = p.aligns.clone();
      cnt = p.cnt;
      editsTotal = p.editsTotal;
      numWordsTotal = p.numWordsTotal;
      nullCnt = p.nullCnt;
      nulls = p.nulls;
    }

    public IncrementalEvaluationMetric<TK, FV> add(
        ScoredFeaturizedTranslation<TK, FV> trans) {
      return add(trans == null ? null : trans.translation);
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> add(
        Sequence<TK> translation) {
      if (translation == null) {
        nulls[cnt++] = true;
        nullCnt++;
      } else {
        aligns[cnt] = calcTER(translation, cnt, editCounts);
        editsTotal += aligns[cnt].numEdits;
        numWordsTotal += aligns[cnt].numWords;
        cnt++;
      }
      return this;
    }

    @Override
    public double maxScore() {
      return 0.0;
    }

    @Override
    public IncrementalEvaluationMetric<TK, FV> replace(int index,
        ScoredFeaturizedTranslation<TK, FV> trans) {
      if (countEdits)
        throw new RuntimeException(
            "TERpMetric: can't both use edit counts and replace().");
      if (aligns[index] != null) {
        editsTotal -= aligns[index].numEdits;
        numWordsTotal -= aligns[index].numWords;
      }
      if (trans == null) {
        aligns[index] = null;
        if (!nulls[index]) {
          nulls[index] = true;
          nullCnt++;
        }
      } else {
        if (nulls[index]) {
          nulls[index] = false;
          nullCnt--;
        }
        aligns[index] = calcTER(trans, index, null);
        editsTotal += aligns[index].numEdits;
        numWordsTotal += aligns[index].numWords;
      }
      return this;
    }

    @Override
    public double score() {
      return -editsTotal / (numWordsTotal);
    }

    @Override
    public int size() {
      return cnt;
    }

    @Override
    public int compareTo(IncrementalEvaluationMetric<TK, FV> o) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int depth() {
      throw new UnsupportedOperationException();
    }

    @Override
    public State<IncrementalEvaluationMetric<TK, FV>> parent() {
      throw new UnsupportedOperationException();
    }

    @Override
    public double partialScore() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
      super.clone();
      return new TERpIncrementalMetric(this);
    }

    @Override
    public String scoreDetails() {
      return "None";
    }
    
    // public double insCount() { return editCounts[EditType.ins.ordinal()]; }
    // public double delCount() { return editCounts[EditType.del.ordinal()]; }
    // public double subCount() { return editCounts[EditType.sub.ordinal()]; }
    // public double sftCount() { return editCounts[EditType.sft.ordinal()]; }
  }

  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err
          .println("Usage:\n\tjava TERpMetric (ref 1) (ref 2) ... (ref n) < canidateTranslations\n");
      System.exit(-1);
    }
    boolean doNIST = true;
    List<List<Sequence<IString>>> referencesList = Metrics.readReferences(args, doNIST);
    System.out.printf("Metric: TERp with %d references (lower is better)%n", args.length);

    TERpMetric<IString, String> ter = new TERpMetric<IString, String>(
        referencesList, false, System.getProperty("terpa") != null);
    TERpMetric<IString, String>.TERpIncrementalMetric incMetric = ter
        .getIncrementalMetric();

    LineNumberReader reader = new LineNumberReader(new InputStreamReader(
        System.in));

    for (String line; (line = reader.readLine()) != null;) {
      if (doNIST) line = NISTTokenizer.tokenize(line);
      Sequence<IString> translation = IStrings.tokenize(line);
      ScoredFeaturizedTranslation<IString, String> tran = new ScoredFeaturizedTranslation<IString, String>(
          translation, null, 0);
      incMetric.add(tran);
    }

    reader.close();

    // TODO(spenceg) This score is negative because of how it is used in the optimization
    // code. Apply abs() so as to not break anything. Should really refactor this so that
    // the optimization code applies its own transformations.
    double score = 100 * incMetric.score();
    score = Math.abs(score);
    System.out.printf("TERp = %.3f%n", score);
  }
}
