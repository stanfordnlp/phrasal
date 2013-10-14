package edu.stanford.nlp.mt.decoder.feat;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.OpenAddressCounter;
import edu.stanford.nlp.util.Generics;

import edu.stanford.nlp.mt.decoder.feat.RichCombinationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.NeedsState;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.metrics.NISTTokenizer;

import java.util.List;
import java.util.Map;
import java.util.LinkedList;
import java.io.PrintStream;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

/**
 * BLEU score as a feature.
 * 
 * @author Michel Galley
 */
public class BLEUFeaturizer extends NeedsState<IString, String>
    implements RichCombinationFeaturizer<IString, String> {

  private static final int ORDER = 4;
  private static final boolean BP_BEFORE_FINAL = System
      .getProperty("bpBeforeFinal") != null; // better be false

  private final String featureName;
  private final boolean featurizeDuringDecoding;

  // Stores the count for each ngram:
  private final String[][] lines;
  private final Trie[] refTries;
  private final int[][] refLengths;

  private boolean rerankingStage = false;

  public static void main(String[] args) throws Exception {
    String[] args2 = new String[args.length + 2];
    args2[0] = "bleu";
    args2[1] = "true";
    System.arraycopy(args, 0, args2, 2, args.length);
    BLEUFeaturizer f = new BLEUFeaturizer(args2);
    for (int i = 0; i < f.refTries.length; ++i)
      f.dumpRefCounts(System.out, i);
  }

  public void dumpRefCounts(PrintStream out, int senti) {
    refTries[senti].dump(out);
  }

  public BLEUFeaturizer(String... args) throws Exception {

    if (args.length < 3)
      throw new RuntimeException(
          "Usage: edu.stanford.nlp.mt.decoder.feat.oracle.BLEUFeaturizer (feature name) (featurize during decoding) (ref0) ... (refN)");

    this.featureName = args[0];
    this.featurizeDuringDecoding = Boolean.parseBoolean(args[1]);
    String[] refs = new String[args.length - 2];
    System.arraycopy(args, 2, refs, 0, refs.length);

    // Read sentences:
    if (refs.length == 0)
      throw new UnsupportedOperationException(
          "Need at least one reference file.");
    lines = new String[refs.length][];
    System.err.printf("Reading %s...\n", refs[0]);
    lines[0] = IOUtils.slurpFile(refs[0]).split("\\n");
    for (int i = 1; i < refs.length; i++) {
      System.err.printf("Reading %s...\n", refs[i]);
      lines[i] = IOUtils.slurpFile(refs[i]).split("\\n");
      if (lines[i].length != lines[0].length)
        throw new RuntimeException(String.format(
            "References with mismatched number of lines: %d != %d.\n",
            lines[i].length, lines[0].length));
    }
    int len = lines[0].length;
    System.err.println("Reference sentences: " + len);
    refTries = new Trie[len];
    refLengths = new int[len][refs.length];

    // NIST tokenization:
    for (int senti = 0; senti < len; ++senti) {
      for (int refi = 0; refi < lines.length; ++refi) {
        lines[refi][senti] = NISTTokenizer.tokenize(lines[refi][senti].trim());
      }
    }

    // Create a trie for each reference sentence:
    for (int senti = 0; senti < len; ++senti) {

      refTries[senti] = Trie.root("ref_" + senti);

      for (int refi = 0; refi < lines.length; ++refi) {

        Trie t = Trie.root("ref_" + senti + "_" + refi);
        String line = lines[refi][senti];
        // System.err.printf("ref line(%d,%d): %s\n", senti, refi, line);
        IString[] toks = IStrings.toIStringArray(line.split("\\s+"));
        refLengths[senti][refi] = toks.length;

        for (int i = 0; i < toks.length; ++i) { // Each ngram start position:
          Trie curT = t;
          Trie maxCurT = refTries[senti];
          for (int j = 0; j < ORDER; ++j) { // Each ngram end position:
            if (i + j >= toks.length)
              break;
            IString newTok = toks[i + j];
            int ngramSz = j + 1;
            // Check if path already present in trie:
            Trie nextT = curT.get(newTok);
            Trie maxNextT = maxCurT.get(newTok);
            int cnt;
            if (nextT != null) {
              assert (nextT.ngramSz == ngramSz);
              cnt = ++nextT.ngramCount;
            } else {
              nextT = new Trie(newTok, ngramSz, 1);
              curT.put(newTok, nextT);
              cnt = 1;
            }
            if (maxNextT != null) {
              assert (maxNextT.ngramSz == ngramSz);
              if (maxNextT.ngramCount < cnt)
                maxNextT.ngramCount = cnt;
            } else {
              maxNextT = new Trie(newTok, ngramSz, cnt);
              maxCurT.put(newTok, maxNextT);
            }
            curT = nextT;
            maxCurT = maxNextT;
          }
        }
      }
    }
  }

  private int getId(Featurizable<IString, String> f) {
    int sentId = f.sourceInputId;
    assert (sentId >= 0);
    assert (sentId < refTries.length);
    return sentId;
  }

  @Override
  public List<FeatureValue<String>> featurize(
      Featurizable<IString, String> f) {

    if (!rerankingStage && !featurizeDuringDecoding)
      return null;

    int sentId = getId(f);

    BLEUIncrementalScorer scorer = f.prior != null ? (BLEUIncrementalScorer) f.prior
        .getState(this) : null;
    double oldBLEU = (scorer != null) ? scorer.score : 0.0;

    // Find new ngram localCounts:
    for (int i = 0; i < f.targetPhrase.size(); ++i) {

      IString tok = f.targetPhrase.get(i);
      // System.err.println("new word: "+tok);

      BLEUIncrementalScorer newScorer = scorer != null ? new BLEUIncrementalScorer(
          scorer, i == 0) : new BLEUIncrementalScorer();

      // Update normalization counts:
      int pos = f.targetPosition + i;
      for (int j = 0; j <= Math.min(pos, ORDER - 1); ++j)
        ++newScorer.localPossibleMatchCounts[j];

      // Add unigram match, if any:
      Trie unigramT = refTries[sentId].get(tok);
      if (unigramT != null) {
        assert (unigramT.ngramSz == 1);
        newScorer.partialMatches.add(unigramT);
        double newVal = newScorer.fullMatches.incrementCount(unigramT);
        int newCount = (int) newVal;
        assert (newVal == newCount);
        if (unigramT.ngramCount >= newCount) {
          ++newScorer.localCounts[0];
          // System.err.printf("new unigram match (%d,%d): %s\n",unigramT.ngramCount,
          // newCount, f.translatedPhrase.get(i));
        }
      }

      // Add n+1-gram match, if n-gram match is present in scorer:
      if (scorer != null) {
        for (Trie ngramT : scorer.partialMatches) {
          Trie np1gramT = ngramT.get(tok);
          if (np1gramT != null) {
            newScorer.partialMatches.add(np1gramT);
            double newVal = newScorer.fullMatches.incrementCount(np1gramT);
            int newCount = (int) newVal;
            assert (newVal == newCount);
            if (np1gramT.ngramCount >= newCount) {
              ++newScorer.localCounts[np1gramT.ngramSz - 1];
              // System.err.printf("new %dgram match: %s\n", np1gramT.ngramSz,
              // f.translatedPhrase.get(i));
            }
          }
        }
      }

      scorer = newScorer;
    }

    f.setState(this, scorer);
    assert (scorer != null);

    // double percentDone =
    // (f.foreignSentence.size()-f.untranslatedTokens)*1.0/f.foreignSentence.size();

    int hypLength = f.targetPosition + f.targetPhrase.size();
    // scorer.updateScore(sentId, hypLength, percentDone, false);
    scorer.updateScore(sentId, hypLength, f.done || BP_BEFORE_FINAL, false);

    // System.err.printf("new=%f old=%f\n", scorer.score, oldBLEU);
    List<FeatureValue<String>> features = Generics.newLinkedList();
    features.add(new FeatureValue<String>(featureName, scorer.score - oldBLEU));
    return features;
  }

  @Override
  public void rerankingMode(boolean r) {
    rerankingStage = r;
  }

  public void dump(Featurizable<IString, String> f) {

    if (!rerankingStage && !featurizeDuringDecoding)
      return;

    BLEUIncrementalScorer scorer = (BLEUIncrementalScorer) f.getState(this);
    int sentId = getId(f);

    System.err.println("ref counts:");
    dumpRefCounts(System.err, sentId);

    for (int i = 0; i < lines.length; ++i)
      System.err.printf("ref%d: %s\n", i, lines[i][sentId]);
    System.err.printf("hyp: %s\n", f.targetPrefix);

    int hypLength = f.targetPosition + f.targetPhrase.size();
    // scorer.updateScore(sentId, hypLength, 1.0, true);
    scorer.updateScore(sentId, hypLength, true, false);
    System.err.println("unigram matches:");
    for (Map.Entry<Trie, Double> e : scorer.fullMatches.entrySet()) {
      if (e.getKey().ngramSz == 1) {
        System.err.printf("%s r=%d h=%d\n", e.getKey().tok,
            e.getKey().ngramCount, e.getValue().intValue());
      }
    }
  }

  public void initialize(
      int sourceInputId,
      List<ConcreteRule<IString,String>> concreteTranslationOptions, Sequence<IString> foreign) {
  }

  public void reset() {
  }

  public Object clone() throws CloneNotSupportedException {
    return super.clone();
  }

  private int bestMatchLength(int index, int candidateLength) {
    int best = refLengths[index][0];
    for (int i = 1; i < refLengths[index].length; i++) {
      if (Math.abs(candidateLength - best) > Math.abs(candidateLength
          - refLengths[index][i])) {
        best = refLengths[index][i];
      }
    }
    return best;
  }

  class BLEUIncrementalScorer {

    final int[] localCounts;
    final int[] localPossibleMatchCounts;
    final List<Trie> partialMatches;
    final Counter<Trie> fullMatches;
    // final BLEUIncrementalScorer backPtr;
    double score;

    public BLEUIncrementalScorer() {
      localCounts = new int[ORDER];
      localPossibleMatchCounts = new int[ORDER];
      partialMatches = new LinkedList<Trie>();
      fullMatches = new OpenAddressCounter<Trie>();
      // fullMatches = new ClassicCounter<Trie>();
      // backPtr = null;
      score = 0.0;
    }

    public BLEUIncrementalScorer(BLEUIncrementalScorer old, boolean deep) {
      if (deep) {
        localCounts = old.localCounts.clone();
        localPossibleMatchCounts = old.localPossibleMatchCounts.clone();
        fullMatches = new OpenAddressCounter<Trie>(old.fullMatches);
      } else {
        localCounts = old.localCounts;
        localPossibleMatchCounts = old.localPossibleMatchCounts;
        fullMatches = old.fullMatches;
      }
      partialMatches = new LinkedList<Trie>();
      // backPtr = old;
      score = old.score;
    }

    public void updateScore(int index, int localC, boolean bp, boolean verbose) {
      // public void updateScore(int index, int localC, double percentDone,
      // boolean verbose) {

      // int localR = bestMatchLength(index, (int)(localC*1.0/percentDone));
      int localR = bestMatchLength(index, localC);

      double localLogBP;
      if (localC < localR) {
        localLogBP = 1 - localR / (1.0 * localC);
      } else {
        localLogBP = 0.0;
      }

      double localPrecisions[] = new double[ORDER];
      for (int i = 0; i < ORDER; i++) {
        if (i == 0) {
          localPrecisions[i] = (1.0 * localCounts[i])
              / localPossibleMatchCounts[i];
        } else {
          localPrecisions[i] = (localCounts[i] + 1.0)
              / (localPossibleMatchCounts[i] + 1.0);
        }
        if (verbose)
          System.err.printf("prec-%d: %f (%d/%d)\n", i, localPrecisions[i],
              localCounts[i], localPossibleMatchCounts[i]);
      }
      double localNgramPrecisionScore = 0;
      for (int i = 0; i < ORDER; i++) {
        localNgramPrecisionScore += (1.0 / ORDER)
            * Math.log(localPrecisions[i]);
      }

      double bleuPrec = Math.exp(localNgramPrecisionScore);
      double bleu = Math.exp(localLogBP + localNgramPrecisionScore);
      if (verbose)
        System.err.printf("BLEU=%f BLEU-prec=%f BP=%f\n", bleu, bleuPrec,
            Math.exp(localLogBP));

      // score = bleu;
      score = bp ? bleu : bleuPrec;
    }
  }
}

class Trie {

  // final static int mapType = Integer.parseInt(System.getProperty("mapType",
  // "0"));

  private static final Map<String, Trie> roots = new Object2ObjectArrayMap<String, Trie>();
  // private static final Map<String,Trie> roots = new
  // Object2ObjectOpenHashMap<String,Trie>();
  // private static final Map<String,Trie> roots = new HashMap<String,Trie>();

  Map<IString, Trie> map;
  final IString tok;
  final int ngramSz;
  int ngramCount;

  static Trie root(String id) {
    Trie t = roots.get(id);
    if (t == null) {
      t = new Trie(null, 0, 0);
      roots.put(id, t);
    }
    return t;
  }

  Trie(IString tok, int ngramSz, int ngramCount) {
    map = null;
    this.tok = tok;
    this.ngramSz = ngramSz;
    this.ngramCount = ngramCount;
  }

  public Trie get(IString key) {
    return (map == null) ? null : map.get(key);
  }

  public Trie put(IString key, Trie trie) {
    if (map == null) {
      map = new Object2ObjectArrayMap<IString, Trie>();
      // map = new Object2ObjectOpenHashMap<IString,Trie>();
      // map = new HashMap<IString,Trie>();
    }
    return map.put(key, trie);
  }

  public void dump(PrintStream out) {
    dump(out, 0);
  }

  private void dump(PrintStream out, int depth) {
    if (map == null)
      return;
    for (Map.Entry<IString, Trie> e : map.entrySet()) {
      for (int i = 0; i < depth; ++i)
        out.print(" ");
      Trie t = e.getValue();
      assert (t.ngramSz == depth + 1);
      out.printf("%s=%d\n", e.getKey().word(), t.ngramCount);
      t.dump(out, depth + 1);
    }
  }
}
