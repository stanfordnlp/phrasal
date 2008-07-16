package mt.syntax.train;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.*;

import edu.stanford.nlp.util.FileLines;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.*;
import mt.base.DynamicIntegerArrayPrefixIndex;
import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;

/**
 * Interpolated backoff ML estimation of multinomials using Witten-Bell discounting.
 * Currently used to smooth lexical probabilities of sytnax-based models in
 * mt.syntax.train (similarly to Michael Collins's thesis, p. 65). 
 *
 * @author Michel Galley
 */
public class IntArrayBackoffEstimator {

  static final String DEBUG_PROPERTY = "DebugBackoffMLEstimator";
  static boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  final static IString separator = new IString("#####");

  final DynamicIntegerArrayPrefixIndex
       index = new DynamicIntegerArrayPrefixIndex(),
       nIndex = new DynamicIntegerArrayPrefixIndex();

  final List<Integer> counts = new IntArrayList();
  final List<Integer> cCounts = new IntArrayList();

  // Tuning set:
  final Map<IntPair,Integer> tune = new HashMap<IntPair,Integer>();

  // Same constant as in Collins parser (thesis, p. 185):
  static final double Cdefault = 5.0;

	// Backoff coefficient parameters, i.e., the constant C in:
	// lambda_i = count(prefix)/(count(prefix)+C_i)
  // Note that rather than having a single free parameter that then
	// gets multplied by the number of distinct successors seen in the
	// training data (common approach), we have here one free parameter 
	// for _each_ backoff 
	// order. C is directly optimized to maxize log-likelihood of a dev set.
  double[] C;

  public IntArrayBackoffEstimator(int backoffOrder) {
    this(createUniformC(backoffOrder,Cdefault));
  }

  public IntArrayBackoffEstimator(int backoffOrder, double c) {
    this(createUniformC(backoffOrder,c));
  }

  public IntArrayBackoffEstimator(double[] C) {
    this.C = C;
  }

  /**
   * Returns the interpolated (backoff) probability of idx given cIdx.
   * Note that backoff goes from right to left, i.e., 0-5, 0-4, 0-3, etc.
   */
  public double probabilityOf(int[] idx, int[] cIdx) {
    return Math.exp(logProbabilityOf(idx,cIdx));
  }

  /**
   * Returns the interpolated (backoff) log-probability of idx given cIdx.
   * Note that backoff goes from right to left, i.e., 0-5, 0-4, 0-3, etc.
   */
  public double logProbabilityOf(int[] idx, int[] cIdx) {
    int[] jIdx = mergeWithSeparator(idx,cIdx);
    double logp = Math.log(1.0/IString.index.size()); // 1/|V|
    for(int i=0; i<=cIdx.length; ++i) {
      int ji = index.indexOf(jIdx,idx.length+1+i,true);
      int ci = nIndex.indexOf(cIdx,i,true);
      double nCount = (ci < cCounts.size()) ? cCounts.get(ci) : 0.0;
      double lambda = nCount/(nCount+Math.exp(C[i]));
      double logpMLE = (nCount > 0.0 && ji < counts.size()) ?
        Math.log(counts.get(ji)/nCount) : Double.NEGATIVE_INFINITY;
      double old_logp = logp;
      logp += Math.log(1-lambda);
      double logLambda = lambda > 0 ? Math.log(lambda) : Double.NEGATIVE_INFINITY;
      logp = ArrayMath.logSum(new double[] {logpMLE+logLambda, logp});
      if(DEBUG) {
        System.err.printf("p%d = p_mle%d*lambda + p%d*(1-lambda) = %f*%f + %f*%f = %f\n",
          i+1, i, i, Math.exp(logpMLE), lambda, Math.exp(old_logp),1-lambda, Math.exp(logp));
      }
    }
    assert(logp == logp);
    return logp;
  }

  public double getLambda(int[] cIdx) {
    double c = C[cIdx.length];
    int ci = nIndex.indexOf(cIdx);
    if(ci < 0 || ci >= cCounts.size())
      return 0.0;
    double nCount = cCounts.get(ci);
    return nCount/(nCount+c);
  }

  /**
   * Provides sample for estimating p(idx | cIdx).
   * Increase counts of c(idx, cIdx) and c(cIdx)
   * and all relevant backoff counts.
   * Note that backoff goes from right to left, i.e., 0-5, 0-4, 0-3, etc.
   * @param idx event whose probability we need to estimate.
   * @param cIdx context
   */
  public void addTrainingSample(int[] idx, int[] cIdx) {
    int[] jIdx = mergeWithSeparator(idx,cIdx);
    for(int i=0; i<=cIdx.length; ++i) {
      int ji = index.indexOf(jIdx,idx.length+1+i,true);
      int ci = nIndex.indexOf(cIdx,i,true);
      addCountToArray(counts,ji,1);
      addCountToArray(cCounts,ci,1);
    }
  }

  /**
   * Provides sample for tuning backoff parameters.
   */
  public void addTuningSample(int[] idx, int[] cIdx) {
    IntPair p = new IntPair(index.indexOf(idx,true),nIndex.indexOf(cIdx,true));
    int count = (tune.containsKey(p)) ? tune.get(p) : 0;
    tune.put(p,count+1);
  }

  /**
   * Tune backoff parameters.
   */
  public void tune() {
    TuningSetLogp p = new TuningSetLogp();
    //Minimizer m = new RandomGreedyLocalSearch(0.5,5);
    //Minimizer<DiffFunction> m = new QNMinimizer();
    //Minimizer<DiffFunction> m = new CGMinimizer();
    Minimizer<DiffFunction> m = new GDMinimizer(p);
    //DiffFunctionTester.test(p); // derivative slightly off...
    System.err.println("Tuning backoff interpolation coefficients:");
    System.err.println("Initial parameters: "+Arrays.toString(C));
    C = m.minimize(p, 1e-5, C);
    System.err.println("Tuned parameters: "+Arrays.toString(C));
  }

  /**
   * Simple test case: train and test 3gram LM, and optionally tune backoff coefficients
	 * to maximize log-likelihood of a dev set.
   */
  public static void main(String[] args) {
    String trainStr=null, tuneStr=null, testStr=null, cStr=null;
    switch(args.length) {
      case 3:
        trainStr = args[0];
        testStr = args[1];
        cStr = args[2];
        break;
      case 4:
        trainStr = args[0];
        tuneStr = args[1];
        testStr = args[2];
        cStr = args[3];
        break;
      default:
        System.err.println("Usage: BackoffProbabilityDistribution <train_corpus> [<tune_corpus>] <test_corpus> <backoff_constant>");
        System.exit(1);
    }
    double cVal = Double.parseDouble(cStr);
    IntArrayBackoffEstimator mle = new IntArrayBackoffEstimator(3,cVal);
    // Train:
    for(String line : new FileLines(trainStr)) {
      int[] tokens = toReverseIntArray(line);
      for(int i=tokens.length-3; i>=0; --i)
        mle.addTrainingSample(
          new int[] {tokens[i]},
          new int[] {tokens[i+1], tokens[i+2]});
    }
    // Tuning:
    if(tuneStr != null) {
      DEBUG=false;
      for(String line : new FileLines(tuneStr)) {
        int[] tokens = toReverseIntArray(line);
        for(int i=tokens.length-3; i>=0; --i)
          mle.addTuningSample
					(new int[] {tokens[i]},
           new int[] {tokens[i+1], tokens[i+2]});
      }
      System.err.printf("Tuning with %d distinct n-grams\n",mle.tune.size());
      mle.tune();
    }
    DEBUG=true;
    // Test:
    int s=-1;
    for(String line : new FileLines(testStr)) {
      double totalLogp = 0.0;
      int[] tokens = toReverseIntArray(line);
      for(int i=tokens.length-3; i>=0; --i) {
        int[] context = new int[] {tokens[i+1], tokens[i+2]};
        double logp = mle.logProbabilityOf(new int[] {tokens[i]}, context);
        System.out.printf("p(%s | %s %s) = ",
           IString.getString(tokens[i]),
           IString.getString(tokens[i+2]),
           IString.getString(tokens[i+1]));
        System.out.print((float)Math.exp(logp));
        System.out.println("\tconfidence in bigram prefix: "+(float)mle.getLambda(context));
        totalLogp += logp;
      }
      System.err.printf("logp(sentence %d) = %.4g\n",++s,totalLogp);
    }
    System.err.println("index size: "+mle.index.size());
    System.err.println("cIndex size: "+mle.nIndex.size());
  }

  /**
   * Returns the partial dervatives of the interpolated (backoff) probability 
	 * of idx given cIdx.
   * 
   * Derivative:
   *
   * deriv(logp) = deriv(log(f(n))) = deriv(f(n))/f(n), where:
   *
   * f(3) = l_3 * mle3 + (1-l_3) * f(2)
   * f(2) = l_2 * mle2 + (1-l_2) * f(1)
   * f(1) = l_1 * mle1 + (1-l_1) * mleD
   *
   * df(3)/dl_3 = mle3
   * df(3)/dl_2 = mle2
   * df(3)/dl_1 = mle1 - mleD
   *
   * dl_i/dC = deriv(nCount/(nCount+C)) = -nCount/(nCount+C)^2
   */
  double[] diffLogProbabilityOf(int[] idx, int[] cIdx) {
    double denom = Math.exp(logProbabilityOf(idx,cIdx));
    double[] dlogp = new double[C.length];
    int[] jIdx = mergeWithSeparator(idx,cIdx);
		// check:
    //double mleD = 1.0/IString.index.size();
    //dlogp[0] = -mleD;
    for(int i=0; i<=cIdx.length; ++i) {
      int ji = index.indexOf(jIdx,idx.length+1+i,true);
      int ci = nIndex.indexOf(cIdx,i,true);
      double nCount = (ci >= 0 && ci < cCounts.size()) ? cCounts.get(ci) : 0.0;
      double mlei = (nCount > 0.0 && ji < counts.size()) ?
         counts.get(ji)/nCount : 0.0;
      dlogp[i] += mlei;
      double d = nCount+Math.exp(C[i]);
      dlogp[i] *= -nCount/(d*d);
    }
    for(int i=0; i<C.length; ++i) {
      dlogp[i] /= denom;
      assert(dlogp[i] == dlogp[i]);
    }
    return dlogp;
  }

  class TuningSetLogp implements DiffFunction {
    public int domainDimension() {
      return C.length;
    }
    public double valueAt(double[] x) {
      double logp = 0.0;
      int len=0;
      C = x;
      for(IntPair p : tune.keySet()) {
        int s = p.getSource();
        int t = p.getTarget();
        int[] idx = index.get(s);
        int[] cIdx = nIndex.get(t);
        int c = tune.get(p);
        logp += c*logProbabilityOf(idx,cIdx);
        len += c;
      }
      double ppl1 = Math.exp(-logp/len);
      System.err.printf("ppl1 of %f at %s (logp=%f, len=%d)\n",
         ppl1,Arrays.toString(x),logp,len);
      return -logp/len;
    }
    public double[] derivativeAt(double[] x) {
      double[] dlogp = new double[C.length];
      int len=0;
      C = x;
      for(IntPair p : tune.keySet()) {
        int s = p.getSource();
        int t = p.getTarget();
        int[] idx = index.get(s);
        int[] cIdx = nIndex.get(t);
        int c = tune.get(p);
        double[] d = diffLogProbabilityOf(idx,cIdx);
        for(int i=0; i<d.length; ++i)
          dlogp[i] += c*d[i];
        len += c;
      }
      for(int i=0; i<dlogp.length; ++i)
        dlogp[i] = dlogp[i]/len;
      if(DEBUG)
        System.err.printf("diff at %s: %s\n",
           Arrays.toString(x),Arrays.toString(dlogp));
      return dlogp;
    }
  }

  private static double[] createUniformC(int sz, double c) {
    double[] C = new double[sz];
    Arrays.fill(C,c);
    return C;
  }

  private static int[] mergeWithSeparator(int[] idx, int[] cIdx) {
    int[] jIdx = new int[idx.length+cIdx.length+1];
    System.arraycopy(idx,0,jIdx,0,idx.length);
    jIdx[idx.length] = separator.id;
    System.arraycopy(cIdx,0,jIdx,idx.length+1,cIdx.length);
    return jIdx;
  }

  private void addCountToArray(List<Integer> a, int idx, int c) {
    while(a.size() <= idx)
      a.add(0);
    a.set(idx,a.get(idx)+c);
  }

  private static int[] toReverseIntArray(String line) {
    String l = "<s> <s> "+line+" </s>";
    List<IString> a = Arrays.asList(IStrings.toIStringArray(l.split("\\s+")));
    Collections.reverse(a);
    return IStrings.toIntArray((IString[])a.toArray());
  }

  private static void printArray(String id, int[] a) {
    String[] strs = IStrings.toStringArray(a);
    System.err.printf("BackoffMLE array: %s = %s\n",id,Arrays.toString(strs));
  }

}
