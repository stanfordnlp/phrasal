package mt.metrics;

import java.util.*;
import java.io.*;

import mt.base.*;
import mt.reranker.ter.TERcost;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.optimization.Function;
import edu.stanford.nlp.optimization.Minimizer;
import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.math.ArrayMath;

import flanagan.math.Minimisation;
import flanagan.math.MinimisationFunction;

/**
 * Find a linear combination of automatic metrics that best correlates with human assessments. 
 * Metrics currently supported: BLEU, NIST, BLEU precision, NIST precision, BLEU 
 * ngram precision (1..4), BLEU brevity penalty (matched against closest reference), NIST 
 * brevity penalty (matched against average reference length), and TER. For TER, it is possible
 * to tune the cost of each edit type.
 * 
 * @author Michel Galley
 */
public class LinearCombinationMetricOptimizer implements Function {

  // TODO: This class is a big mess; rewrite it after MetricsMATR

  private static IString
   TER = new IString("ter"), BLEU = new IString("bleu"), LBLEU = new IString("lbleu"),
   BLEU_BP = new IString("bleu_bp"), LBLEU_BP = new IString("lbleu_bp"), 
   BLEU_P = new IString("bleu_prec"), LBLEU_P = new IString("lbleu_prec"),
   NIST = new IString("nist"), NIST_BP = new IString("nist_bp"),
   EXTERNAL = new IString("external"), NIST_P = new IString("nist_prec"),
   BLEU_1 = new IString("bleu_1gram"), BLEU_2 = new IString("bleu_2gram"),
   BLEU_3 = new IString("bleu_3gram"), BLEU_4 = new IString("bleu_4gram"),
   NIST_1 = new IString("nist_1gram"), NIST_2 = new IString("nist_2gram"),
   NIST_3 = new IString("nist_3gram"), NIST_4 = new IString("nist_4gram");

  static List<Integer> permutation;

  final IString[] metricScoresStr;
  String metrics;

  List<Sequence<IString>> hyps;
  List<List<Sequence<IString>>> refs;
  
  double[][] metricScores=null;
  double[] externalScores;
  double[] humanScores;
  double[] w;

  int numInstances = Integer.MAX_VALUE;
  int numDimensions=-1;
  int windowSize;
  
  boolean verbose, tuneCosts, tuneMetrics, printFuncValues;

  double simplexSize = 1.0; 

  // For RandomGreedyLocalSearch:
  public static final String LEARN_RATE_PROPERTY = "LearnRate";
  public static final double LEARN_RATE = Double.parseDouble(System.getProperty(LEARN_RATE_PROPERTY, "0.3"));
  public static final String PERSEVERENCE_PROPERTY = "Perseverence";
  public static final int PERSEVERENCE = Integer.parseInt(System.getProperty(PERSEVERENCE_PROPERTY, "5"));
  public static final String MAX_ITERATIONS_PROPERTY = "MaxIterations";
  public static final int MAX_ITERATIONS = Integer.parseInt(System.getProperty(MAX_ITERATIONS_PROPERTY, "1000000"));
  public static final String STARTING_POINTS_PROPERTY = "StartingPoints";
  public static final int STARTING_POINTS = Integer.parseInt(System.getProperty(STARTING_POINTS_PROPERTY, "10"));
  public static final String SIMPLEX_GROWTH_PROPERTY = "SimplexGrows";
  public static final int SIMPLEX_GROWTH = Integer.parseInt(System.getProperty(SIMPLEX_GROWTH_PROPERTY, "5"));

  static public void main(String[] args) throws Exception {
    Properties prop = StringUtils.argsToProperties(args);
    System.err.println("properties: "+prop.toString());
    LinearCombinationMetricOptimizer hcm = new LinearCombinationMetricOptimizer(prop);
    hcm.maximize();
  }
  
  public LinearCombinationMetricOptimizer(Properties prop) throws Exception {

    metrics = prop.getProperty("metrics");
    String refPrefix = prop.getProperty("refs");
		String hypFile = prop.getProperty("hyp");
    String humanScoreFile = prop.getProperty("human");
    String externalScoreFile = prop.getProperty("external");

    if(metrics == null || refPrefix == null || hypFile == null || humanScoreFile == null) {
			System.err.println
       ("Usage:\n\tjava mt.tools.MetricLinearCombination "+
        "[OPTIONS] -metrics <metric1:...:metricN> -refs <reference_trans> -hyp <system_trans> -human <human_judgments>\n"+
        "where OPTIONS are:\n"+
        "-tuneCosts: whether to tune TER costs (slow, default false)\n"+
        "-initCosts <sub:ins:del:shift>: initial TER costs (default: 1:1:1:1)\n"+
        "-initSimplexSize <f>: size of initial simplex (relative to initial values, default: .2)\n"+
        "-dataSize <n>: how many instances to read from files (default: maximum)\n"+
        "-initWeights <x1:...:xn>: initial metric weights (default: 1:...:1)\n"
       );
			System.exit(-1);
		}

    windowSize = Integer.parseInt(prop.getProperty("windowSize","10"));
    tuneCosts = Boolean.parseBoolean(prop.getProperty("tuneCosts","false"));
    tuneMetrics = Boolean.parseBoolean(prop.getProperty("tuneMetrics","true"));
    verbose = Boolean.parseBoolean(prop.getProperty("verbose","false"));
    String[] c = prop.getProperty("initCosts","1:1:1:1").split(":");
    String init_w = prop.getProperty("initWeights");
    TERcost.set_default_substitute_cost(Double.parseDouble(c[0]));
    TERcost.set_default_insert_cost(Double.parseDouble(c[1]));
    TERcost.set_default_delete_cost(Double.parseDouble(c[2]));
    TERcost.set_default_shift_cost(Double.parseDouble(c[3]));
    if(prop.getProperty("initSimplexSize") != null)
      simplexSize = Double.parseDouble(prop.getProperty("initSimplexSize"));
    if(prop.getProperty("dataSize") != null)
      numInstances = Integer.parseInt(prop.getProperty("dataSize"));

    // Metric identifiers:
    if(externalScoreFile != null && !metrics.contains("external"))
      metrics += ":external";
    metricScoresStr = IStrings.toIStringArray(metrics.split(":"));

    // Initial metric weights:
    if(init_w != null) {
      String[] ws = init_w.split(":");
      w = new double[ws.length];
      for(int i=0; i<ws.length; ++i)
        w[i] = Double.parseDouble(ws[i]);
    }

    // Load hypothesis translations:
    hyps = IOTools.slurpIStringSequences(hypFile);
    createRandomPermutation(hyps.size());
    hyps = permute(hyps);
    System.err.printf("Read %d hypotheses from %s\n",hyps.size(),hypFile);

    // Load references:
    refs = new ArrayList<List<Sequence<IString>>>();
    File f = new File(refPrefix);
		if (f.exists()) {
      List<Sequence<IString>> lines = IOTools.slurpIStringSequences(refPrefix);
      lines = permute(lines);
      for(Sequence<IString> ref : lines) {
        List<Sequence<IString>> sref = new ArrayList<Sequence<IString>>();
        sref.add(ref);
        refs.add(sref);
      }
    } else {
      for(int i = 0; ; i++) {
        f = new File(refPrefix+i);
        if(!f.exists()) break;
        List<Sequence<IString>> lines = IOTools.slurpIStringSequences(refPrefix);
        lines = permute(lines);
        for(int j=0; j<lines.size(); ++j) {
          if(i==0) {
            List<Sequence<IString>> fref = new ArrayList<Sequence<IString>>();
            refs.add(fref);
            fref.add(lines.get(j));
          } else {
            refs.get(j).add(lines.get(j));
          }
        }
      }
    }
    System.err.printf("Read %d references from %s\n",refs.size(),refPrefix);

    // Create scores:
    assert(refs.size() == hyps.size());
    if(numInstances > refs.size())
      numInstances = refs.size();
    if(externalScoreFile != null)
      externalScores = getExternalScores(externalScoreFile);
    humanScores = getExternalScores(humanScoreFile);
    createMetricScores();
  }

  @SuppressWarnings("unchecked")
  public void maximize() {
    DownhillSimplexMinimizer m = new DownhillSimplexMinimizer(simplexSize);
    System.err.println("minimizer: "+m.toString());
    // Set initial weights:
    if(w == null) {
      w = new double[numDimensions];
      Arrays.fill(w,1.0);
    }
    double[] bw = new double[0];
    double bf = Double.MAX_VALUE;
    if(w.length > 0 && tuneMetrics) {
      System.err.print("Optimizing...\n");
      for(int i=0; i<STARTING_POINTS; ++i) {
        for(int j=0; j<SIMPLEX_GROWTH; ++j) {
          m.setSimplexRelativeSize(simplexSize*Math.pow(2,j));
          System.err.printf("Step %d/%d...\nStart at %s\n",i+1,STARTING_POINTS,Arrays.toString(w));
          double[] tw = m.minimize(this, 1e-5, w);
          normalizeW(tw);
          System.err.printf("Pearson %.6f at %s\n",-valueAt(tw),Arrays.toString(tw));
          double f = valueAt(tw);
          if(f < bf) { 
            if(bf == Double.MAX_VALUE) {
              System.err.printf("Pearson: -Inf -> %.6f\n",-f);
            } else {
              System.err.printf("Pearson: %.6f -> %.6f\n",-bf, -f);
            }
            bf = f; bw = tw; 
          }
          w = randomDoubleArray(w.length);
        }
      }
      for(int i=0; i<bw.length; ++i) {
        if(i>0)
          System.out.print(" + ");
        System.out.printf("%.3f*%s",bw[i],metricScoresStr[i]);
      }
      System.out.println();
      if(tuneCosts) {
        int i=bw.length-1;
        System.out.printf
          ("TER costs: sub=%.3f ins=%.3f del=%.3f shift=%.3f\n",
          tcost(bw[i-3]),tcost(bw[i-2]),tcost(bw[i-1]),tcost(bw[i]));
      }
    }
    printFuncValues = true;
    valueAt(bw);
  }

  private void normalizeW(double[] w) {
    double m1 = Math.abs(ArrayMath.max(w)), m2 = Math.abs(ArrayMath.min(w));
    ArrayMath.multiplyInPlace(w,1.0/Math.max(m1,m2));
  }

  public int domainDimension() {
    return numDimensions;
  }

  public double valueAt(double[] x) {
    if(tuneCosts) {
      int i=x.length-1;
      TERcost.set_default_substitute_cost(tcost(x[i-3]));
      TERcost.set_default_insert_cost(tcost(x[i-2]));
      TERcost.set_default_delete_cost(tcost(x[i-1]));
      TERcost.set_default_shift_cost(tcost(x[i]));
      createMetricScores();
    }
    double[] combinedScores = new double[metricScores[0].length];
    for(int i=0; i<metricScores.length; ++i)
        for(int j=0; j<metricScores[0].length; ++j)
          combinedScores[j] += x[i]*metricScores[i][j];
    double pearson = getPearsonCorrelation(humanScores,combinedScores);
    if(printFuncValues) {
      //printWorseCorrelationCases(combinedScores);
      System.err.printf("Pearson=%.3f for metric %s at [",pearson, metrics);
      for(int i=0; i<x.length; ++i) {
        if(i>0)
          System.err.print(",");
        System.err.print((float)x[i]);
      }
      System.err.printf("].\n");
    }
    return -pearson;
  }

  void dump(String name, List<?> l) {
    System.err.printf("Dumping list %s\n",name);
    for(int i=0; i<l.size(); ++i)
      System.err.printf("%d: %s\n",i,l.get(i).toString());
  }

  public double[] getExternalScores(String filename) throws IOException {
    int sz = windowSize;
    List<Double> l = new ArrayList<Double>();
    List<String> lines = Arrays.asList(StringUtils.slurpFile(filename).split("[\r\n]+"));
    lines = permute(lines);
    for (String line : lines)
      l.add(Double.parseDouble(line));
    double[] externalScores = new double[numInstances/sz];
    int i=0, ii=0;
    while(i+sz<=numInstances) {
      double hs = 0.0;
      double totAvgLen = 0.0;
      for(int j=0; j<sz; ++j) {
        double avgLen = 0.0;
        for(int k=0; k<refs.get(i+j).size(); ++k) {
          avgLen += refs.get(i+j).get(k).size();
        }
        avgLen /= refs.get(i+j).size();
        totAvgLen += avgLen;
        hs += l.get(i+j)*avgLen;
      }
      externalScores[ii] = hs/totAvgLen;
      ++ii;
      i+=sz;
    }
    System.err.printf("Read %d scores from %s\n",externalScores.length,filename);
    return externalScores;
  }

  @SuppressWarnings("unchecked")
  public void createMetricScores() {
    int sz = windowSize;
    metricScores = new double[metricScoresStr.length][];
    for(int j=0; j<metricScores.length; ++j)
      metricScores[j] = new double[numInstances/sz];
    int i=0, ii=0;
    while(i+sz<=numInstances) {
      List<List<Sequence<IString>>> refs1 = new ArrayList<List<Sequence<IString>>>();
      // Create references:
      for(int j=0; j<sz; ++j)
        refs1.add(refs.get(i+j));
      // Create hypotheses:
      BLEUMetric.BLEUIncrementalMetric bleu = new BLEUMetric(refs1,true).getIncrementalMetric();
      NISTMetric.NISTIncrementalMetric nist = new NISTMetric(refs1).getIncrementalMetric();
      TERMetric.TERIncrementalMetric ter = new TERMetric(refs1).getIncrementalMetric();
      for(int j=0; j<sz; ++j) {
        bleu.add(new ScoredFeaturizedTranslation<IString, String>(hyps.get(i+j), null, 0));
        nist.add(new ScoredFeaturizedTranslation<IString, String>(hyps.get(i+j), null, 0));
        ter.add(new ScoredFeaturizedTranslation<IString, String>(hyps.get(i+j), null, 0));
      }
      setMetricScores(bleu, nist, ter, ii);
      if(numDimensions < 0)
        numDimensions = metricScores.length + (tuneCosts?4:0);
      if(verbose) {
        System.err.printf("index %d (line %d) human=%.3f",ii,i,humanScores[ii]);
        for(int k=0; k<metricScores.length; ++k) {
          IString n = metricScoresStr[k];
          System.err.printf(" %s=%.3f", n, metricScores[k][ii]);
        }
        System.err.println();
      }
      ++ii;
      i+=sz;
    }
  }

  public void setMetricScores(BLEUMetric<?,?>.BLEUIncrementalMetric bleu,
                              NISTMetric<?,?>.NISTIncrementalMetric nist,
                              TERMetric<?,?>.TERIncrementalMetric ter, int line) {
    for(int i=0; i<metricScoresStr.length; ++i) {
      IString s = metricScoresStr[i];
      double v;
      if(s.equals(NIST)) {
        v = nist.score();
      } else if(s.equals(NIST_BP)) {
        v = nist.brevityPenalty();
      } else if(s.equals(NIST_P)) {
        v = nist.score()/bleu.brevityPenalty();
      } else if(s.equals(BLEU)) {
        v = bleu.score();
      } else if(s.equals(LBLEU)) {
        v = Math.log(bleu.score());
        if(v == -Double.NEGATIVE_INFINITY)
          v = -100;
      } else if(s.equals(BLEU_BP)) {
        v = bleu.brevityPenalty();
      } else if(s.equals(LBLEU_BP)) {
        v = Math.log(bleu.brevityPenalty());
      } else if(s.equals(BLEU_P)) {
        v = bleu.score()/bleu.brevityPenalty();
      } else if(s.equals(LBLEU_P)) {
        v = Math.log(bleu.score())-Math.log(bleu.brevityPenalty());
      } else if(s.equals(TER)) {
        v = -ter.score();
      } else if(s.equals(BLEU_1)) {
        v = bleu.ngramPrecisions()[0];
        v = (v == v) ? v : 0.0; 
      } else if(s.equals(BLEU_2)) {
        v = bleu.ngramPrecisions()[1];
        v = (v == v) ? v : 0.0; 
      } else if(s.equals(BLEU_3)) {
        v = bleu.ngramPrecisions()[2];
        v = (v == v) ? v : 0.0; 
      } else if(s.equals(BLEU_4)) {
        v = bleu.ngramPrecisions()[3];
        v = (v == v) ? v : 0.0; 
      } else if(s.equals(NIST_1)) {
        v = nist.ngramPrecisions()[0];
        v = (v == v) ? v : 0.0; 
      } else if(s.equals(NIST_2)) {
        v = nist.ngramPrecisions()[1];
        v = (v == v) ? v : 0.0; 
      } else if(s.equals(NIST_3)) {
        v = nist.ngramPrecisions()[2];
        v = (v == v) ? v : 0.0; 
      } else if(s.equals(NIST_4)) {
        v = nist.ngramPrecisions()[3];
        v = (v == v) ? v : 0.0; 
      } else if(s.equals(EXTERNAL)) {
        v = externalScores[line];
      } else {
        throw new RuntimeException("Unknown metric: "+s.toString());
      }
      metricScores[i][line] = v;
    }
  }

  /**
   * Computes Pearson correlation coefficient between two _standardized_ vectors.
   */
  public static double getPearsonCorrelation(double[] scores1, double[] scores2) {
    return ArrayMath.pearsonCorrelation(scores1,scores2);
    //return ArrayMath.innerProduct(scores1,scores2)/(scores1.length-1);
  }

  void printWorseCorrelationCases(double[] combinedScores) {
    double[] combinedScoresStd = combinedScores.clone(); 
    double[] humanScoresStd = humanScores.clone();
    ArrayMath.standardize(combinedScoresStd);
    ArrayMath.standardize(humanScoresStd);
    List<Pair<Double,Integer>> l = new ArrayList<Pair<Double,Integer>>();
    for(int i=0; i<combinedScores.length; ++i)
      l.add(new Pair<Double,Integer>(humanScoresStd[i]-combinedScoresStd[i],i));
    Collections.sort(l, new Comparator<Pair<Double,Integer>>() {
      public int compare(Pair<Double,Integer> p1, Pair<Double,Integer> p2) {
        return p1.first().compareTo(p2.first());
      }
    });
    for(int i=0; i<l.size();++i) {
      int ii = l.get(i).second();
      System.err.printf("===============================================\n");
      System.err.printf("index=%d human=%.3f metric=%.3f (standardized scores: human=%.3f metric=%.3f diff=%.3f):\n",
        ii,humanScores[ii],combinedScores[ii],humanScoresStd[ii],combinedScoresStd[ii],humanScoresStd[ii]-combinedScoresStd[ii]);
      for(int j=0;j<windowSize; ++j) {
        int line = ii*windowSize+j;
        System.err.printf("  ref(%d)=%s\n",line,refs.get(line).get(0).toString());
        System.err.printf("  hyp(%d)=%s\n",line,hyps.get(line).toString());
      }
    }
  }

  /**
   * Transform input cost (any real value) into value that works
   * reasonably well with TER (between .05 and 1).
   */
  private double tcost(double v) {
    return (SloppyMath.sigmoid(v) * .95) + .05;
  }

  private static double[] randomDoubleArray(int l) {
    double[] a = new double[l];
    Random generator = new Random();
    for(int i = 0; i < a.length; i++)
      a[i] = generator.nextDouble()*2-1;
    return a;
  }

  private static void createRandomPermutation(int sz) {
    permutation = new ArrayList<Integer>();
    for(int i=0; i<sz; ++i)
      permutation.add(i);
    Collections.shuffle(permutation);
  }

  private <T> List<T> permute(List<T> list) {
    List<T> newList = new ArrayList<T>();
    for(int i=0; i<list.size(); ++i)
      newList.add(list.get(permutation.get(i)));
    return newList;
  }
}

/**
 * Downhill simplex minimization algorithm (Nelder and Mead, 1965).
 * It requires only function evaluations, and it is a method of choice
 * when the derivative is unavailable (e.g., while minimizing 0/1 loss).
 * This class mainly serves as an interface to Michael Thomas Flanagan's
 * implementation.
 *
 * @author Michel Galley
 */
class DownhillSimplexMinimizer implements Minimizer<Function> {

	// Could move this class to edu.stanford.nlp.optimization; 
	// the downside is that doing "import edu.stanford.nlp.optimization.*"
	// would require an extra jar file.

  double[] step;
  double simplexRelativeSize = .5;

  public DownhillSimplexMinimizer(double[] step) {
    this.step = step;
  }

  public DownhillSimplexMinimizer(double s) {
    simplexRelativeSize = s;
  }

  public DownhillSimplexMinimizer() {}

  public void setSimplexRelativeSize(double r) {
    simplexRelativeSize = r;
  }

  public double[] minimize
       (Function function, double ftol, double[] initial, int maxIterations) {
    System.err.printf("Nelder-Mead optimization with simplex of relative size %.3f\n", simplexRelativeSize);
    if(step == null) {
      step = initial.clone();
      for(int i=0; i<step.length; ++i)
        step[i] *= simplexRelativeSize;
    }
    Minimisation min = new Minimisation();
    min.nelderMead(new WrapperFunction(function), initial, step, ftol);
    double minimum = min.getMinimum();
    double[] param = min.getParamValues();
    System.err.printf("Nelder-Mead converged at: %s value: %.3f\n", Arrays.toString(param),minimum);
    return param;
  }

  public double[] minimize
    (Function function, double functionTolerance, double[] initial) {
    return minimize(function, functionTolerance,initial,Integer.MAX_VALUE);
  }

  class WrapperFunction implements MinimisationFunction {
    Function f;
    WrapperFunction(Function f) { this.f = f; }
    public double function(double[] x) { return f.valueAt(x); }
  }
}
