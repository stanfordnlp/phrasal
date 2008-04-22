package mt.reranker;

import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.FileReader;

import edu.stanford.nlp.util.Interner;

/**
 * Creates IBM Model 1 alignments between source and target language
 * sentences based on a GIZA++ translation probability table.
 *
 * Each target language word is aligned to 0 or more source language
 * words. This means each source language word is aligned to 0 or 1
 * target language words. In model 1, an alignment probability is
 * directly proportional to the product of the word translation
 * probabilities, so we can do a greedy search from the source side
 * for each word and that's it.
 *
 * Model 1 typically sets the lexical probability of any word
 * translating to NULL to a fixed value shared across all words, but
 * since GIZA++ gives us lexicalized NULL probabilities we use those
 * instead.
 *
 **/
public class Model1Aligner {
  public static final String DEFAULT_GIZA_TTABLE_FN = "/u/nlp/scr/data/gale/n-best-reranking/model1/model/model1.actual.ti.final";
  public static final String NULL_WORD = "NULL".intern(); // what GIZA++ outputs for null
  public static final double UNSEEN_PROB = 0.000001; // probability of a word pair we've never seen yet

  private IdentityHashMap<String, IdentityHashMap<String, Double>> lexProbs;

  private Interner<String> intr = new Interner<String>();

  public Model1Aligner() throws Exception {
    this(DEFAULT_GIZA_TTABLE_FN);
  }

  public Model1Aligner(String fn) throws Exception { 
    lexProbs = new IdentityHashMap<String, IdentityHashMap<String, Double>>();

    System.err.println("Loading translation model probabilities from " + fn + ". Begin surfing the internet now.");
    
    BufferedReader in = new BufferedReader(new FileReader(fn));
    String line;
    int i = 0;
    while ((line = in.readLine()) != null) {
      String[] vals = line.split(" ");
      String f = intr.intern(new String(vals[0])); // interning this results in a significant slowdown
      String e = intr.intern(new String(vals[1])); // interning this results in a significant savings in memory
      double v = Double.parseDouble(vals[2]);

      // if (!lexProbs.containsKey(f)) lexProbs.put(f, new IdentityHashMap<String, Double>());
      // lexProbs.get(f).put(e, v);
      IdentityHashMap<String, Double> second = lexProbs.get(f);
      if (second == null) {
        second = new IdentityHashMap<String, Double>();
        lexProbs.put(f, second);
      }
      second.put(e, v);
      
      i++;
      if ((i % 1000000) == 0) System.err.println("loaded " + i + " probs");
    }

    System.err.println("DONE! loaded " + i + " probabilities total.");
    in.close();
  }

  public double getLexProbFromTheHashTableIMadeByReadingTheOutputFromGIZAPlusPlus(String source, String target) {
    String iSource = intr.intern(source);
    String iTarget = intr.intern(target);
    if (!lexProbs.containsKey(iSource)) return UNSEEN_PROB;
    IdentityHashMap<String, Double> asdf = lexProbs.get(iSource);

    if (!asdf.containsKey(iTarget)) return UNSEEN_PROB;
    return asdf.get(iTarget);
  }

  public double displacementProb(String source, String target, int sourcePos, int sourceLen, int targetPos, int targetLen) {
    return (double)1 / ((double)targetLen + 1); // null being the extra one
  }

  public double lexProb(String source, String target) {
    return getLexProbFromTheHashTableIMadeByReadingTheOutputFromGIZAPlusPlus(source, target);
  }

  public LegacyAlignment align(String[] source, String[] target) {
    LegacyAlignment ret = new LegacyAlignment();
    ret.score = 0.0;

    for(int i = 0; i < source.length; i++) {
      int best = -1;
      double bestProb = Math.log(lexProb(source[i], NULL_WORD)) + Math.log(displacementProb(source[i], NULL_WORD, i, source.length, -1, target.length));
      //System.out.println("  " + source[i] + " -> NULL has prob " + lexProb(source[i], NULL_WORD) + " * " + displacementProb(source[i], NULL_WORD, i, source.length, -1, target.length) + " = " + bestProb);
      for(int j = 0; j < target.length; j++) {

	double p = Math.log(lexProb(source[i], target[j])) + Math.log(displacementProb(source[i], target[j], i, source.length, j, target.length));
	//System.out.println("  " + source[i] + " -> " + target[j] + " has prob " + lexProb(source[i], target[j]) + " * " + displacementProb(source[i], target[j], i, source.length, j, target.length) + " = " + p);

	if(p > bestProb) {
	  best = j;
	  bestProb = p;
	}
      }

      //System.out.println("aligning " + source[i] + " to " + (best == -1 ? "NULL" : target[best]));
      ArrayList l = new ArrayList();
      l.add(best);
      ret.add(l);
      ret.score += bestProb;
    }

    return ret;
  }


  public static void main(String[] argv) throws Exception {
    Model1Aligner m = new Model1Aligner();

    BufferedReader sIn = new BufferedReader(new FileReader(argv[0]));
    BufferedReader tIn = new BufferedReader(new FileReader(argv[1]));

    String sSent, tSent;
    while((sSent = sIn.readLine()) != null) {
      tSent = tIn.readLine();
      
      String[] s = sSent.split(" ");
      String[] t = tSent.split(" ");

      LegacyAlignment a = m.align(s, t);
      System.out.println("# score: " + a.score);
      System.out.println(a.toString(s, t));
    }
  }
}

