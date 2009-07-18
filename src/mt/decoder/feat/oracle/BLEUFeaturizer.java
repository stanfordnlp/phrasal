package mt.decoder.feat.oracle;

import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.ClassicCounter;
import mt.decoder.feat.RichIncrementalFeaturizer;
import mt.decoder.feat.ClonedFeaturizer;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.ConcreteTranslationOption;
import mt.base.Sequence;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * BLEU score as a feature.
 *
 * @author Michel Galley
 */
public class BLEUFeaturizer implements RichIncrementalFeaturizer<IString,String> {

  private static final boolean DEBUG = false;

  private final String featureName;
	private final boolean featurizeDuringDecoding;

	// Stores the count for each ngram:
  private final Trie[] refTries;
  private final int[][] refLengths;

  private boolean rerankingStage = false;

  public static void main(String[] args) throws Exception {
    BLEUFeaturizer f = new BLEUFeaturizer("bleu", true, args);
    for(int i=0; i<f.refTries.length; ++i)
      f.dumpRefCounts(i);
  }

  public void dumpRefCounts(int senti) {
    Trie t = refTries[senti];
    t.dump();
  }

  @SuppressWarnings("unchecked")
  public BLEUFeaturizer(String featureName, boolean featurizeDuringDecoding, String... refs) throws Exception {
    
    this.featureName = featureName;
		this.featurizeDuringDecoding = featurizeDuringDecoding;

    // Read sentences:
    if(refs.length == 0)
      throw new UnsupportedOperationException("Need at least one reference file.");
    String[][] lines = new String[refs.length][];
    System.err.printf("Reading %s...\n", refs[0]);
    lines[0] = StringUtils.slurpFile(refs[0]).split("\\n");
    for (int i = 1; i < refs.length; i++) {
      System.err.printf("Reading %s...\n", refs[i]);
      lines[i] = StringUtils.slurpFile(refs[i]).split("\\n");
      if(lines[i].length != lines[0].length)
        throw new RuntimeException(String.format("References with mismatched number of lines: %d != %d.\n", lines[i].length, lines[0].length));
    }
    int len = lines[0].length;
    refTries = new Trie[len];
    refLengths = new int[len][refs.length];

    // Create a trie for each reference sentence:
    for(int senti=0; senti<len; ++senti) {

      refTries[senti] = Trie.root("ref_"+senti);

      for(int refi=0; refi<lines.length; ++refi) {

        Trie t = Trie.root("ref_"+senti+"_"+refi);
        String line = lines[refi][senti];
        //System.err.println("line: "+line);
        IString[] toks = IStrings.toIStringArray(line.trim().split("\\s+"));
        refLengths[senti][refi] = toks.length;

        for(int i=0; i<toks.length; ++i) { // Each ngram start position:
          Trie curT = t;
          Trie maxCurT = refTries[senti];
          for(int j=0; j<4; ++j) { // Each ngram end position:
            if(i+j >= toks.length)
              break;
            IString newTok = toks[i+j];
            int ngramSz = j+1;
            // Check if path already present in trie:
            Trie nextT = curT.get(newTok);
            Trie maxNextT = maxCurT.get(newTok);
            int cnt;
            if(nextT != null) {
              assert(nextT.ngramSz == ngramSz);
              cnt = ++nextT.ngramCount;
            } else {
              nextT = new Trie(ngramSz, 1);
              curT.put(newTok, nextT);
              cnt = 1;
            }
            if(maxNextT != null) {
              assert(maxNextT.ngramSz == ngramSz);
              maxNextT.ngramCount = cnt;
            } else {
              maxNextT = new Trie(ngramSz, cnt);
              maxCurT.put(newTok, maxNextT);
            }
            curT = nextT;
            maxCurT = maxNextT;
          }
        }
      }
    }
  }

  @Override
	public FeatureValue<String> featurize(Featurizable<IString,String> f) {

    int sentId = (int) f.hyp.id;

    if(!rerankingStage && !featurizeDuringDecoding)
			return null;

    BLEUIncrementalScorer scorer = (BLEUIncrementalScorer) f.prior.extra;
    double oldBLEU = (scorer != null) ? scorer.score : 0.0;

    // Find new ngram localCounts:
    for(int i=0; i<f.translatedPhrase.size(); ++i) {

      IString tok = f.translatedPhrase.get(i);

      BLEUIncrementalScorer newScorer = scorer != null ? new BLEUIncrementalScorer(scorer) : new BLEUIncrementalScorer();

      // Update normalization counts:
      int pos = f.translationPosition+i;
      for(int j=0; j<=pos; ++j)
        ++newScorer.localPossibleMatchCounts[j];

      // Add unigram match, if any:
      Trie unigramT = refTries[sentId].get(tok);
      if(unigramT != null) {
        assert(unigramT.ngramSz == 1);
        newScorer.partialMatches.add(unigramT);
        double newVal = newScorer.fullMatches.incrementCount(unigramT);
        int newCount = (int)newVal;
        assert(newVal == newCount);
        if(unigramT.ngramCount <= newCount)
          ++newScorer.localCounts[0];
      }

      // Add n+1-gram match, if n-gram match is present in scorer:
      if(scorer != null) {
        for(Trie ngramT : scorer.partialMatches) {
          Trie np1gramT = ngramT.get(tok);
          if(np1gramT != null) {
            newScorer.partialMatches.add(np1gramT);
            double newVal = newScorer.fullMatches.incrementCount(np1gramT);
            int newCount = (int)newVal;
            assert(newVal == newCount);
            if(np1gramT.ngramCount <= newCount)
              ++newScorer.localCounts[np1gramT.ngramSz-1];
          }
        }
      }

      // Debug:
			if(DEBUG) {
				List<Trie> m = newScorer.partialMatches;
				for(int k=0; k<m.size(); ++k) {
					assert(m.get(k).ngramSz > 0 && m.get(k).ngramSz <= 4);
					for(int l=k+1; l<m.size(); ++l) {
						assert(m.get(k).ngramSz != m.get(l).ngramSz);
					}
				}
			}

      scorer = newScorer;
    }
    
    f.extra = scorer;
    assert(scorer != null);
    
    int hypLength = f.translationPosition+f.translatedPhrase.size();
    scorer.updateScore(sentId, hypLength);

    return new FeatureValue<String>(featureName, scorer.score - oldBLEU);
  }

  public List<FeatureValue<String>> listFeaturize(Featurizable<IString, String> f) {
    return null;
  }

  @Override
  public void rerankingMode(boolean r) {
    rerankingStage = r;
  }

  public void debugBest(Featurizable<IString, String> f) { }

  public void initialize(List<ConcreteTranslationOption<IString>> concreteTranslationOptions, Sequence<IString> foreign) { }

  public void reset() { }

  @SuppressWarnings("unchecked")
  public ClonedFeaturizer<IString, String> clone() throws CloneNotSupportedException {
    try {
      return (ClonedFeaturizer<IString,String>) super.clone();
    } catch(CloneNotSupportedException e) {
      e.printStackTrace();
      return null;
    }
  }

  private int bestMatchLength(int index, int candidateLength) {
		int best = refLengths[index][0];
		for (int i = 1; i < refLengths[index].length; i++) {
			if (Math.abs(candidateLength - best) > Math.abs(candidateLength - refLengths[index][i])) {
				best = refLengths[index][i];
			}
		}
		return best;
	}

  class BLEUIncrementalScorer {

    private static final int order = 4;

    final int[] localCounts;
    final int[] localPossibleMatchCounts;
    final List<Trie> partialMatches;
    final Counter<Trie> fullMatches;
    final BLEUIncrementalScorer backPtr;
    double score;

    public BLEUIncrementalScorer() {
      localCounts = new int[order];
      localPossibleMatchCounts = new int[order];
      partialMatches = new LinkedList<Trie>();
      fullMatches = new ClassicCounter<Trie>();
      backPtr = null;
      score = 0.0;
    }

    public BLEUIncrementalScorer(BLEUIncrementalScorer old) {
      localCounts = old.localCounts.clone();
      localPossibleMatchCounts = old.localPossibleMatchCounts.clone();
      partialMatches = new LinkedList<Trie>();
      fullMatches = new ClassicCounter<Trie>(old.fullMatches);
      backPtr = old;
      score = old.score;
    }

    public void updateScore(int index, int localC) {

      int localR = bestMatchLength(index, localC);

      double localLogBP;
      if (localC < localR) {
        localLogBP = 1-localR/(1.0*localC);
      } else {
        localLogBP = 0.0;
      }

      double localPrecisions[] = new double[order];
      for (int i = 0; i < order; i++) {
          if (i == 0) {
            localPrecisions[i] = (1.0*localCounts[i])/localPossibleMatchCounts[i];
          } else {
            localPrecisions[i] = (localCounts[i]+1.0)/(localPossibleMatchCounts[i]+1.0);
          }
      }
      double localNgramPrecisionScore = 0;
      for (int i = 0; i < order; i++) {
        localNgramPrecisionScore += (1.0/order)*Math.log(localPrecisions[i]);
      }

      score = Math.exp(localLogBP + localNgramPrecisionScore);
    }
  }
}

class Trie {

  final static int mapType = Integer.parseInt(System.getProperty("mapType", "0"));

  private static final Map<String,Trie> roots = new HashMap<String,Trie>();

  Map<IString,Trie> map;
  final int ngramSz;
  int ngramCount;

  static Trie root(String id) {
    Trie t = roots.get(id);
    if(t == null) {
      t = new Trie(0, 0);
      roots.put(id,t);
    }
    return t;
  }

  Trie(int ngramSz, int ngramCount) {
    map = null;
    this.ngramSz = ngramSz;
    this.ngramCount = ngramCount;
  }

  public Trie get(IString key) {
    return (map == null) ? null : map.get(key);
  }

  public Trie put(IString key, Trie trie) {
    if(map == null) {
      map = new HashMap<IString,Trie>();
    }
    return map.put(key, trie);
  }

  public void dump() {
    dump(0);
  }

  private void dump(int depth) {
    if(map == null)
      return;
    for(Map.Entry<IString,Trie> e : map.entrySet()) {
      for(int i=0; i<depth; ++i)
        System.out.print(" ");
      Trie t = e.getValue();
      assert(t.ngramSz == depth+1);
      System.out.printf("%s=%d\n", e.getKey().word(), t.ngramCount);
      t.dump(depth+1);
    }
  }
}
