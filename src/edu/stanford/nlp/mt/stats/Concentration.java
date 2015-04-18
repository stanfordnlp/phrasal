package edu.stanford.nlp.mt.stats;

/**
 * Re-sampling of a CRP concentration parameter using the method described in:
 * 
 *   M. West. 1995. "Hyperparameter estimation in Dirichlet process mixture models."
 *    Technical Report, Duke University.
 *   
 * <p>
 * The same method is described in Escobar and West (1995).
 * 
 * @author Spence Green
 *
 */
public class Concentration {
  private RandomDistribution gammaPrior; // Specifies $p(\alpha)$
  private RandomDistribution betaDist;
  private double alpha; // Actual variable we want to sample
  private double x; // Auxiliary variable to faciliate sampling $\alpha$
  private int n; // Number of data points
  private int K; // Number of clusters
  private double a;
  private double b;
  
  // Number of mini-steps to do per sample of $\alpha$:
  // only one is necessary for ergodicity
  private int numMiniSteps = 1;

  public Concentration(double a, double b, double alpha, int n) {
    this.gammaPrior = new Distributions.Gamma();
    this.betaDist = new Distributions.Beta();

    this.alpha = alpha; // Initial value
    this.a = a;
    this.b = b;
    this.n = n; // Fixed
    this.x = 0.5;
    this.K = -1;
  }


  public double sampleAlpha(int K) {
    if(K > n)
      throw new IllegalArgumentException("Cannot re-sample for k > n " + K);
    
    if (n == 0 || K == 0.0 ) {//Sample from the prior again
      double[] params = {a,b};
      double[] draw = gammaPrior.draw(params, null);
      this.alpha = draw[0];
    
    } else {
      this.K = K;
      // Sample from $p(\alpha, x | K)$
      for(int i = 0; i < numMiniSteps; i++) {
        doSampleX();
        doSampleAlpha();
        //logs("alpha=%f (avg %f), x=%f, K=%d", alpha, sumAlpha/(i+1), x, K);
      }
    }
    
    return alpha;
  }

  private void doSampleX() {
    double[] params = {alpha+1.0,n};
    double[] draw = betaDist.draw(params,null);
    this.x = draw[0];
    //logs("Beta(%f, %d) = %f", alpha+1, n, x);
  }

  private void doSampleAlpha() {
    double piConst = a + K - 1.0;
    piConst /= (n * (b - Math.log(x)));
    
    double pi = piConst / (1.0 + piConst);
    
//    System.err.printf("a=%.6f b=%.6f n=%d K=%d pi=%.6f alpha=%.6f%n",a,b,n,K,pi,alpha);
    
    double[] gamma1Params = {a + K, b - Math.log(x)};
    double[] draw1 = gammaPrior.draw(gamma1Params, null);
    
    double[] gamma2Params = {a + K - 1.0, b - Math.log(x)};
    double[] draw2 = gammaPrior.draw(gamma2Params, null);
    
    alpha = (pi * draw1[0]) + ((1.0 - pi)*draw2[0]);
    
    if(alpha < 0.0)
      throw new RuntimeException();
  }
  
  /**
   * For debugging.
   * 
   * @param args
   */
  public static void main(String[] args) {
    Concentration conc = new Concentration(1.0,1.0,1.0,500);
    
    System.out.println(conc.sampleAlpha(50));
    
  }
}
