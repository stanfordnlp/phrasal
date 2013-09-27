package edu.stanford.nlp.mt.tune.optimizers;

import java.util.Arrays;

import edu.stanford.nlp.optimization.Function;
import edu.stanford.nlp.optimization.Minimizer;

/**
 * Downhill Simplex (Nelder-Mead)
 * 
 * @author daniel cer
 */

public class DownhillSimplexMinimizer implements Minimizer<Function>{
  private final boolean DEBUG = false;
  
  private final double alpha;
  private final double gamma;
  private final double rho;
  private final double sigma;
  private final double lambda;
  
  public final static double DEFAULT_ALPHA = 1;
  public final static double DEFAULT_GAMMA = 2;
  public final static double DEFAULT_RHO   = 0.5;
  public final static double DEFAULT_SIGMA = 0.5;
  public final static double DEFAULT_LAMBDA = 1.0;
  
  public DownhillSimplexMinimizer() {
    this(DEFAULT_ALPHA, DEFAULT_GAMMA, DEFAULT_RHO, DEFAULT_SIGMA, DEFAULT_LAMBDA);
  }
  
  
  public DownhillSimplexMinimizer(double alpha, double gamma, double rho, double sigma, double lambda) {
    this.alpha = alpha;
    this.gamma = gamma;
    this.rho   = rho;
    this.sigma = sigma;
    this.lambda = lambda;
  }
  
  public double[] minimize(Function function, double functionTolerance,
      double[] initial) {
    return minimize(function, functionTolerance, initial, 0);
  }

  /**
   * Create initial n+1 simplex points using given initial point
   * for point[0] and setting all other points to 
   * point[0] + lambda+unit_vector_(i-1]
   *   
   * @param initial
   */
  private double[][] createInitialSimplex(double[] initial) {
    double[][] points = new double[initial.length+1][];
    for (int i = 0; i < points.length; i++) {
      points[i] = new double[initial.length];
    }
    System.arraycopy(initial, 0, points[0], 0, initial.length);
    for (int i = 1; i < points.length; i++) {
      System.arraycopy(initial, 0, points[i], 0, initial.length);
      points[i][i-1] += lambda;
    }
    return points;
  }
  
  /**
   * Calculate the values of function at points
   * 
   * @param function
   * @param points
   */
  private double[] calculateValues(Function function, double[][] points) {
    double values[] = new double[points.length];
    for (int i = 0; i < points.length; i++) {
      values[i] = function.valueAt(points[i]);
    }
    return values;
  }
  
  
  /**
   * Find the minimum and maximum positions in values
   * 
   * @param values
   */
  private int[] findMinAndMax(double[] values) {
    int minIdx = 0, maxIdx = 0;
    for (int i = 1; i < values.length; i++) {
      if (values[i] < values[minIdx]) {
        minIdx = i;
      } else if (values[i] > values[maxIdx]) {
        maxIdx = i;
      }
    }
    return new int[]{minIdx, maxIdx};
  }
  
  /**
   * Average points, excluding point at worstIdx
   * 
   * @param points
   * @param worstIdx
   */
  private double[] calcCenter(double[][] points, int worstIdx) {
    double[] center = new double[points[0].length];
    
    for (int i = 0; i < points.length; i++) {
      if (i == worstIdx) continue;
      for (int j = 0; j < center.length; j++) {
          center[j] += points[i][j];    
      }
    }
    
    for (int j = 0; j < center.length; j++) {
      center[j] /= points.length;  
    }
    
    return center;
  }
  
  /**
   * calculate b + scale *(m1 - m2)
   * @param b
   * @param m1
   * @param m2
   */
  private double[] scaledMixture(double[] b, double scale, double[] m1, double[] m2) {
    double[] y = new double[b.length];
    for (int i = 0; i < y.length; i++) {
      y[i] = b[i] + scale*(m1[i] - m2[i]);
    }
    return y;
  }
  
  public double[] minimize(Function function, double functionTolerance,
      double[] initial, int maxIterations) {
    double points[][] = createInitialSimplex(initial);
    
    double[] values = calculateValues(function, points);
    
    if (DEBUG) {
      System.err.println("Downhill simplex minimization");
      System.err.printf("  Max iterations: %d\n", maxIterations);
      System.err.printf("  Initial point: %s\n", Arrays.toString(initial));      
    }    
    
    for (int iter = 0; iter < maxIterations || maxIterations == 0; iter++) {
        if (DEBUG) {
          System.err.printf("Iter: %d\n", iter);
        }
        
        // find min max values            
        int[] minMax = findMinAndMax(values);
        int bestIdx = minMax[0], worstIdx = minMax[1];
        if (DEBUG) {
          System.err.printf("  Best point value (%d): %e\n", bestIdx, values[bestIdx]);
          System.err.printf("  Worst point value(%d): %e\n", worstIdx, values[worstIdx]);         
        }
        
        
        // Reflection        
        double[] center = calcCenter(points, worstIdx);
        double[] xReflection = scaledMixture(center, alpha, center, points[worstIdx]);
        double rValue = function.valueAt(xReflection);
        
        double deltaBestWorst = Math.abs(values[bestIdx] - values[worstIdx]);
        if (deltaBestWorst < functionTolerance) {
           if (DEBUG) {
             System.err.printf("deltaBestWorst %e < tol: %e\n", deltaBestWorst, functionTolerance);
           }
           break;  
        }
        
        if (DEBUG) {
          System.err.printf("Reflection val: %e vec: %s\n", rValue, Arrays.toString(xReflection));          
        }
                
        if (rValue < values[worstIdx] && rValue > values[bestIdx]) {
          System.arraycopy(xReflection, 0, points[worstIdx], 0, xReflection.length);
          values[worstIdx] = rValue;
          continue;
        }
        
        // Expansion
        if (rValue < values[bestIdx]) {
          double[] xExpand = scaledMixture(center, gamma, center, points[worstIdx]);          
          double eValue = function.valueAt(xExpand);
          if (DEBUG) {
            System.err.printf("Expansion val: %e vec: %s\n", eValue, Arrays.toString(xExpand));          
          }
          if (eValue < rValue) {
            System.arraycopy(xExpand, 0, points[worstIdx], 0, xExpand.length);
            values[worstIdx] = eValue;
          } else {
            System.arraycopy(xReflection, 0, points[worstIdx], 0, xReflection.length);
            values[worstIdx] = rValue;
          }
          continue;
        }
        
        // contraction
        double[] xContraction = scaledMixture(points[worstIdx], rho, center, points[worstIdx]);
        double cValue  = function.valueAt(xContraction);
        if (cValue < values[worstIdx]) {
          if (DEBUG) {
            System.err.printf("Contraction val: %e vec: %s\n", cValue, Arrays.toString(xContraction));          
          }
          System.arraycopy(xContraction, 0, points[worstIdx], 0, xContraction.length);
          values[worstIdx] = cValue;
          continue;
        }
        
        // Reduction        
        for (int i = 0; i < points.length; i++) {
          if (i == bestIdx) continue;                  
          points[i] = scaledMixture(points[bestIdx], sigma, points[i], points[bestIdx]);
        }
        values = calculateValues(function, points);
        if (DEBUG) {
          System.err.printf("Reduction vals: %s\n", Arrays.toString(values));          
        }
    }
    
    int[] minMax = findMinAndMax(values);
    if (DEBUG) {
      System.err.printf("Final best val: %e point: %s\n", values[minMax[0]], Arrays.toString(points[minMax[0]]));
    }
    return points[minMax[0]];
  }
}
