package mt.tools;

import java.util.*;
import java.io.File;

import mt.base.*;
import mt.metrics.*;
import mt.reranker.ter.TERcost;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.optimization.Function;
import edu.stanford.nlp.optimization.Minimizer;
import edu.stanford.nlp.math.SloppyMath;

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
public class HumanAssessmentCorrelationMaximizer implements Function {

  final String[] metricScoresStr;
  double[][] metricScores=null;
  double[] humanScores;
  double[] linearScores;
  int sz_x=-1;
  boolean tune_ter_costs = true;

  List<Sequence<IString>> hyps;
  List<List<Sequence<IString>>> refs;

  static final boolean DEBUG=false;

  static public void main(String[] args) throws Exception {
    HumanAssessmentCorrelationMaximizer hcm = new HumanAssessmentCorrelationMaximizer(args);
    hcm.maximize();
  }
  
  public HumanAssessmentCorrelationMaximizer(String[] args) throws Exception {
		if (args.length != 4) {
			System.err.printf
       ("Usage:\n\tjava mt.tools.HumanAssessmentCorrelationMaximizer (metric1:...:metricN) (reference.trans) (system.trans) (human.judgments)\n");
			System.exit(-1);
		}
    String metrics = args[0];
    String refPrefix = args[1];
		String hypFile = args[2];
    String humanFile = args[3];

    metricScoresStr = metrics.split(":");

    hyps = IOTools.slurpIStringSequences(hypFile);
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
    List<Double> humanScoreList = new ArrayList<Double>();
    String[] lines = StringUtils.slurpFile(humanFile).split("[\r\n]+");
    for (String line : lines) humanScoreList.add(Double.parseDouble(line));
    humanScores(refs,humanScoreList,5);
    metricScores(refs,hyps,5);
  }

  public void maximize() {
    DownhillSimplexMinimizer m = new DownhillSimplexMinimizer();
    double[] init_x = new double[sz_x];
    // Correlation along each axis:
    for(int i=0; i<init_x.length; ++i){
      if(i>0) init_x[i-1] = 0;
      init_x[i] = 1;
      valueAt(init_x);
    }
    // Set initial conditions:
    Arrays.fill(init_x,1.0);
    System.err.print("Optimizing...\n");
    double[] tx = m.minimize(this, 1e-5, init_x);
    System.out.print("optimal metric: ");
    for(int i=0; i<metricScoresStr.length; ++i) {
      if(i>0)
        System.out.print(" + ");
      System.out.printf("%.3f*%s",tx[i],metricScoresStr[i]);
    }
    System.out.println();
    if(tune_ter_costs) {
      int i=tx.length-1;
      System.out.printf
        ("cost(sub)=%.3f cost(ins)=%.3f cost(del)=%.3f cost(shift)=%.3f\n",
        pSigmoid(tx[i-3]),pSigmoid(tx[i-2]),pSigmoid(tx[i-1]),pSigmoid(tx[i]));
    }
  }

  public int domainDimension() {
    return metricScores[0].length + (tune_ter_costs?4:0);
  }

  public double valueAt(double[] x) {
    if(tune_ter_costs) {
      int i=x.length-1;
      TERcost.set_default_substitute_cost(pSigmoid(x[i-3]));
      TERcost.set_default_insert_cost(pSigmoid(x[i-2]));
      TERcost.set_default_delete_cost(pSigmoid(x[i-1]));
      TERcost.set_default_shift_cost(pSigmoid(x[i]));
      metricScores(refs,hyps,5);
    }
    for(int i=0; i<metricScores.length; ++i)
        for(int j=0; j<metricScores[0].length; ++j)
          linearScores[i] += x[j]*metricScores[i][j];
    double pearson = getPearsonCorrelation(humanScores,linearScores);
    System.err.printf("pearson=%.3f at [",pearson);
    for(int i=0; i<x.length; ++i) {
      if(i>0)
        System.err.print(",");
      System.err.print((float)x[i]);
    }
    System.err.printf("]\n");
    return -pearson;
  }

  public void humanScores(List<List<Sequence<IString>>> refs, List<Double> human, int sz) {
    assert(human.size() == refs.size());
    humanScores = new double[refs.size()/sz];
    int i=0, ii=0;
    while(i+sz<=refs.size()) {
      // Merge human machineScores:
      double hs = 0.0;
      double totAvgLen = 0.0;
      for(int j=0; j<sz; ++j) {
        double avgLen = 0.0;
        for(int k=0; k<refs.get(i+j).size(); ++k) {
          avgLen += refs.get(i+j).get(k).size();
        }
        avgLen /= refs.get(i+j).size();
        totAvgLen += avgLen;
        hs += human.get(i+j)*avgLen;
      }
      humanScores[ii] = hs/totAvgLen;
      if(DEBUG)
        System.err.printf("line %d human=%.3f\n",i,humanScores[ii]);
      ++ii;
      i+=sz;
    }
  }

  @SuppressWarnings("unchecked")
  public void metricScores(List<List<Sequence<IString>>> refs, List<Sequence<IString>> hyps, int sz) {
    assert(hyps.size() == refs.size());
    metricScores = new double[refs.size()/sz][];
    linearScores = new double[metricScores.length];
    int i=0, ii=0;
    while(i+sz<=refs.size()) {
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
      metricScores[ii] = getMetricScores(bleu, nist, ter);
      if(sz_x < 0)
        sz_x = metricScores[0].length + (tune_ter_costs?4:0);
      if(DEBUG) {
        System.err.printf("line %d",i);
        for(int k=0; k<metricScoresStr.length; ++k)
          System.err.printf(" %s=%.3f", metricScoresStr[k], metricScores[ii][k]);
        System.err.println();
      }
      ++ii;
      i+=sz;
    }
  }

  public double[] getMetricScores(BLEUMetric.BLEUIncrementalMetric bleu,
                                  NISTMetric.NISTIncrementalMetric nist,
                                  TERMetric.TERIncrementalMetric ter) {
    double[] scores = new double[metricScoresStr.length];
    for(int i=0; i<metricScoresStr.length; ++i) {
      String s = metricScoresStr[i];
      if(s.equals("nist")) {
        scores[i] = -nist.score();
      } else if(s.equals("nist_bp")) {
        scores[i] = -nist.brevityPenalty();
      } else if(s.equals("nist_prec")) {
        scores[i] = -nist.score()/bleu.brevityPenalty();
      } else if(s.equals("bleu")) {
        scores[i] = -bleu.score();
      } else if(s.equals("bleu_bp")) {
        scores[i] = -bleu.brevityPenalty();
      } else if(s.equals("bleu_prec")) {
        scores[i] = -bleu.score()/bleu.brevityPenalty();
      } else if(s.equals("ter")) {
        scores[i] = -ter.score();
      } else if(s.equals("bleu_prec")) {
        scores[i] = -bleu.score()/bleu.brevityPenalty();
      } else if(s.equals("bleu_1gram")) {
        scores[i] = -bleu.ngramPrecisions()[0];
      } else if(s.equals("bleu_2gram")) {
        scores[i] = -bleu.ngramPrecisions()[1];
      } else if(s.equals("bleu_3gram")) {
        scores[i] = -bleu.ngramPrecisions()[2];
      } else if(s.equals("bleu_4gram")) {
        scores[i] = -bleu.ngramPrecisions()[3];
      } else {
        throw new UnsupportedOperationException();
      }
    }
    return scores;
  }

	public static double getPearsonCorrelation(double[] scores1, double[] scores2){
		double result;
		double sum_sq_x = 0, sum_sq_y = 0;
		double sum_coproduct = 0;
		double mean_x = scores1[0], mean_y = scores2[0];
		for(int i=2;i<scores1.length+1;i+=1){
			double w = ((double) i - 1) /i;
			double delta_x = scores1[i-1]-mean_x;
			double delta_y = scores2[i-1]-mean_y;
			sum_sq_x += delta_x*delta_x*w;
			sum_sq_y += delta_y*delta_y*w;
			sum_coproduct += delta_x*delta_y*w;
			mean_x += delta_x / i;
			mean_y += delta_y / i;
		}
		double pop_sd_x = Math.sqrt(sum_sq_x/scores1.length);
		double pop_sd_y = Math.sqrt(sum_sq_y/scores1.length);
		double cov_x_y = sum_coproduct / scores1.length;
    double denom = pop_sd_x*pop_sd_y;
    if(denom == 0.0)
      return 0.0;
    result = cov_x_y/denom;
		return result;
	}

  private double pSigmoid(double v) {
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
  static final double stepRatio = 5.0;

  public DownhillSimplexMinimizer(double[] step) {
    this.step = step;
  }

  public DownhillSimplexMinimizer() {}

  public double[] minimize
       (Function function, double ftol, double[] initial, int maxIterations) {
    if(step == null) {
      step = initial.clone();
      for(int i=0; i<step.length; ++i)
        step[i] /= stepRatio;    
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
