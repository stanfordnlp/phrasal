package mt.tools;

import java.util.*;
import java.io.File;
import java.io.IOException;

import mt.base.*;
import mt.metrics.*;
import mt.reranker.ter.TERcost;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;
import edu.stanford.nlp.optimization.Function;
import edu.stanford.nlp.optimization.Minimizer;
import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.*;

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
public class HumanAssessmentCorrelationMaximizer implements DiffFunction {

  // TODO: print bad cases
  // TODO: derivative
  // TODO: more than 1 external score

  private static IString
   TER = new IString("ter"), BLEU = new IString("bleu"),
   BLEU_BP = new IString("bleu_bp"), BLEU_P = new IString("bleu_prec"),
   NIST = new IString("nist"), NIST_BP = new IString("nist_bp"),
   EXTERNAL = new IString("external"), NIST_P = new IString("nist_prec"),
   BLEU_1 = new IString("bleu_1gram"), BLEU_2 = new IString("bleu_2gram"),
   BLEU_3 = new IString("bleu_3gram"), BLEU_4 = new IString("bleu_4gram"),
   NIST_1 = new IString("nist_1gram"), NIST_2 = new IString("nist_2gram"),
   NIST_3 = new IString("nist_3gram"), NIST_4 = new IString("nist_4gram");

  final IString[] metricScoresStr;

  List<Sequence<IString>> hyps;
  List<List<Sequence<IString>>> refs;
  
  double[][] metricScores=null;
  double[] externalScores;
  double[] humanScores;
  double[] w;

  int numInstances = Integer.MAX_VALUE;
  int numDimensions=-1;
  int windowSize;
  
  boolean tuneCosts;
  boolean tuneMetrics;
  boolean verbose;
  boolean printValue;

  static final Random shuffler = new Random(3982733423L);

  double simplexSize = 0; // if 0, disables optimization with downhill simplex

  // For RandomGreedyLocalSearch:
  public static final String LEARN_RATE_PROPERTY = "LearnRate";
  public static final double LEARN_RATE = Double.parseDouble(System.getProperty(LEARN_RATE_PROPERTY, "0.3"));
  public static final String PERSEVERENCE_PROPERTY = "Perseverence";
  public static final int PERSEVERENCE = Integer.parseInt(System.getProperty(PERSEVERENCE_PROPERTY, "10"));
  public static final String MAX_ITERATIONS_PROPERTY = "MaxIterations";
  public static final int MAX_ITERATIONS = Integer.parseInt(System.getProperty(MAX_ITERATIONS_PROPERTY, "1000000"));

  static public void main(String[] args) throws Exception {
    Properties prop = StringUtils.argsToProperties(args);
    System.err.println("properties: "+prop.toString());
    HumanAssessmentCorrelationMaximizer hcm = new HumanAssessmentCorrelationMaximizer(prop);
    hcm.maximize();
  }
  
  public HumanAssessmentCorrelationMaximizer(Properties prop) throws Exception {

    String metrics = prop.getProperty("metrics");
    String refPrefix = prop.getProperty("refs");
		String hypFile = prop.getProperty("hyp");
    String humanScoreFile = prop.getProperty("human");
    String externalScoreFile = prop.getProperty("external");

    if(metrics == null || refPrefix == null || hypFile == null || humanScoreFile == null) {
			System.err.println
       ("Usage:\n\tjava mt.tools.HumanAssessmentCorrelationMaximizer "+
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
    Collections.shuffle(hyps,shuffler);
    System.err.printf("Read %d hypotheses from %s\n",hyps.size(),hypFile);

    // Load references:
    refs = new ArrayList<List<Sequence<IString>>>();
    File f = new File(refPrefix);
		if (f.exists()) {
      for(Sequence<IString> ref : IOTools.slurpIStringSequences(refPrefix)) {
        List<Sequence<IString>> sref = new ArrayList<Sequence<IString>>();
        sref.add(ref);
        refs.add(sref);
      }
    } else {
      for(int i = 0; ; i++) {
        f = new File(refPrefix+i);
        if(!f.exists()) break;
        List<Sequence<IString>> lines = IOTools.slurpIStringSequences(refPrefix);
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
    Collections.shuffle(refs,shuffler);

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
    Minimizer m = (simplexSize > 0.0) ? 
    new DownhillSimplexMinimizer(simplexSize) :
    new RandomGreedyLocalSearch(LEARN_RATE,PERSEVERENCE,MAX_ITERATIONS);
    System.err.println("minimizer: "+m.toString());
    // Set initial weights:
    if(w == null) {
      w = new double[numDimensions];
      Arrays.fill(w,1.0);
    }
    double[] tx = new double[0];
    if(w.length > 0 && tuneMetrics) {
      System.err.print("Optimizing...\n");
      tx = m.minimize(this, 1e-5, w);
      System.out.print("optimal metric: "+metricScoresStr[0]);
      for(int i=0; i<tx.length; ++i) {
          System.out.print(" + ");
        System.out.printf("%.3f*%s",tx[i],metricScoresStr[i+1]);
      }
      System.out.println();
      if(tuneCosts) {
        int i=tx.length-1;
        System.out.printf
          ("TER costs: sub=%.3f ins=%.3f del=%.3f shift=%.3f\n",
          tcost(tx[i-3]),tcost(tx[i-2]),tcost(tx[i-1]),tcost(tx[i]));
      }
    }
    printValue = true;
    valueAt(tx);
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
    double[] combinedScores = new double[metricScores.length];
    for(int i=0; i<metricScores.length; ++i)
        for(int j=0; j<metricScores[0].length; ++j)
          combinedScores[i] += (j>0 ? x[j-1] : 1.0)*metricScores[i][j];
    double pearson = getPearsonCorrelationStd(humanScores,combinedScores);
    if(printValue) {
      System.err.printf("pearson=%.3f at [",pearson);
      for(int i=0; i<x.length; ++i) {
        if(i>0)
          System.err.print(",");
        System.err.print((float)x[i]);
      }
      System.err.printf("]\n");
    }
    return -pearson;
  }

  public double[] derivativeAt(double[] x) {
    if(tuneCosts)
      throw new RuntimeException("Derivative can't be computed if TER costs are not constant (piecewise constant function).");
    double[] deriv = new double[x.length];
    deriv[0] = 0.0;
    for(int j=1; j<metricScores[0].length; ++j) {
      double[] combinedScores = new double[metricScores.length];
      for(int i=0; i<metricScores.length; ++i)
        combinedScores[i] += metricScores[i][j];
      deriv[j] = -getPearsonCorrelationStd(humanScores,combinedScores);
    }
    return deriv;
  }

  public double[] getExternalScores(String filename) throws IOException {
    int sz = windowSize;
    List<Double> l = new ArrayList<Double>();
    String[] lines = StringUtils.slurpFile(filename).split("[\r\n]+");
    Collections.shuffle(Arrays.asList(lines),shuffler);
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
    ArrayMath.standardize(externalScores);
    System.err.printf("Read %d scores from %s\n",externalScores.length,filename);
    return externalScores;
  }

  @SuppressWarnings("unchecked")
  public void createMetricScores() {
    int sz = windowSize;
    metricScores = new double[numInstances/sz][];
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
      metricScores[ii] = getMetricScores(bleu, nist, ter, ii);
      ArrayMath.standardize(metricScores[ii]);
      if(numDimensions < 0)
        numDimensions = metricScores[0].length + (tuneCosts?4:0)-1;
      if(verbose) {
        System.err.printf("line %d human=%.3f",i,humanScores[ii]);
        for(int k=0; k<metricScores[ii].length; ++k) {
          IString n = metricScoresStr[k];
          System.err.printf(" %s=%.3f", n, metricScores[ii][k]);
        }
        System.err.println();
      }
      ++ii;
      i+=sz;
    }
  }

  public double[] getMetricScores(BLEUMetric.BLEUIncrementalMetric bleu,
                                  NISTMetric.NISTIncrementalMetric nist,
                                  TERMetric.TERIncrementalMetric ter, int line) {
    int sz = metricScoresStr.length;
    double[] scores = new double[sz];
    for(int i=0; i<metricScoresStr.length; ++i) {
      IString s = metricScoresStr[i];
      double v;
      if(s.equals(NIST)) {
        v = -nist.score();
      } else if(s.equals(NIST_BP)) {
        v = -nist.brevityPenalty();
      } else if(s.equals(NIST_P)) {
        v = -nist.score()/bleu.brevityPenalty();
      } else if(s.equals(BLEU)) {
        v = -bleu.score();
      } else if(s.equals(BLEU_BP)) {
        v = -bleu.brevityPenalty();
      } else if(s.equals(BLEU_P)) {
        v = -bleu.score()/bleu.brevityPenalty();
      } else if(s.equals(TER)) {
        v = -ter.score();
      } else if(s.equals(BLEU_1)) {
        v = -bleu.ngramPrecisions()[0];
      } else if(s.equals(BLEU_2)) {
        v = -bleu.ngramPrecisions()[1];
      } else if(s.equals(BLEU_3)) {
        v = -bleu.ngramPrecisions()[2];
      } else if(s.equals(BLEU_4)) {
        v = -bleu.ngramPrecisions()[3];
      } else if(s.equals(NIST_1)) {
        v = -nist.ngramPrecisions()[0];
      } else if(s.equals(NIST_2)) {
        v = -nist.ngramPrecisions()[1];
      } else if(s.equals(NIST_3)) {
        v = -nist.ngramPrecisions()[2];
      } else if(s.equals(NIST_4)) {
        v = -nist.ngramPrecisions()[3];
      } else if(s.equals(EXTERNAL)) {
        v = externalScores[line];
      } else {
        throw new RuntimeException("Unknown metric: "+s.toString());
      }
      scores[i] = (v == v) ? v : 0.0; 
    }
    return scores;
  }

  /**
   * Computes Pearson correlation coefficient between two _standardized_ vectors.
   */
  public static double getPearsonCorrelationStd(double[] scores1, double[] scores2) {
    return ArrayMath.innerProduct(scores1,scores2)/(scores1.length-1);
  }

  /**
   * Transform input cost (any real value) into value that works
   * reasonably well with TER (between .05 and 1).
   */
  private double tcost(double v) {
    return (SloppyMath.sigmoid(v) * .95) + .05;
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

  public double[] minimize
       (Function function, double ftol, double[] initial, int maxIterations) {
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
