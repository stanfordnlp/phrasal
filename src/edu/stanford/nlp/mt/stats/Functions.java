package edu.stanford.nlp.mt.stats;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Useful mathematical functions.
 * 
 * @author Spence Green
 *
 */
public final class Functions {

//  public static double gamma(double x) {
//    return Stat.gammaFunction(x);
//  }
//  
//  public static double logGamma(double x) {
//    return Stat.logGammaFunction(x);
//  }
//  public static double pochhammer(double x, int n) {
//    return Math.exp(logPochhammer(x,n));
//  }
//  
//  public static double logPochhammer(double x, int n) {
//    if(x < 0.0 || n < 0)
//      throw new IllegalArgumentException(String.format("%s: x = %.2f n = %d",Functions.class.getName(),x,n));
//    
//    //TODO Is this correct?
//    if(x == 0.0)
//      return 0.0;
//    
//    double num = Stat.logGammaFunction(x + (double) n);
//    double denom = Stat.logGammaFunction(x);
//    
//    //WSGDEBUG
//    if(Double.isNaN(num) || Double.isNaN(denom))
//      System.err.println();
//    
//    return num - denom;
//  }

  /**
   * ln(n!).
   * 
   * Implemented as Stirling's approximation.
   * 
   * @param n
   * @return
   */
  public static double logFactorial(int n) {
    if (n < 0) throw new IllegalArgumentException();
    else if (n <= 1) return 0; // 0! := 1
    else return (n * Math.log(n)) - n + (0.5 * Math.log(2 * Math.PI * n));
  }
  
  /**
   * Returns a random permutation of n choose k using a KFY shuffle.
   * 
   * @param n -- Size of the vector
   * @param k -- Number of 1s
   */
  public static boolean[] randomBitVector(int n, int k) {
    if(n <= 0 || k > n)
      throw new IllegalArgumentException(String.format("Can't generate bit vector [ n = %d , k = %d ]",n,k));
    
    boolean[] vect = new boolean[n];//default is false
    
    if(k == 0) { 
      //Do nothing and return
    
    } else if(k == n) {
      Arrays.fill(vect, true);
    
    } else {
      //Inside-out KFY shuffle with dynamic initialization
      //See http://en.wikipedia.org/wiki/Knuth_shuffle#The_modern_algorithm
      vect[0] = true;
      for(int i = n-1; i > 0; i--) {
        int j = ThreadLocalRandom.current().nextInt(i+1);
        vect[i] = vect[j];
        vect[j] = (i < k);
      }
    }
    
    return vect;
  }
  
  
  /**
   * Implements n choose k.
   * 
   * @param n
   * @param k
   */
  public static long binCoeff(int n, int k) {
    return Math.round(Math.exp(logBinCoeff(n,k)));
  }
  
  /**
   * Returns the log of n choose k. Linear time computation.
   * 
   * @param n
   * @param k
   */
  public static double logBinCoeff(int n, int k) {
    if(k > n)
      throw new IllegalArgumentException("Cannot compute (" + n + " choose " + k + " )");
    else if(k == 0 || k == n)
      return Math.log(1);
    
    double r = 0.0;
    for(int d = 1; d <= k; d++)
      r += Math.log(n - (k-d)) - Math.log(d);
 
    return r;
  }
  
  public static double logMult(double a, double b) {
    if(a == 0.0 || b == 0.0)
      throw new IllegalArgumentException("Cannot take the logMult of " + a + " " + b);
    return Math.exp(Math.log(a) + Math.log(b));
  }
  
  
  /**
   * @param args
   */
  public static void main(String[] args) {
    System.out.println("Binomial coefficients");
    for(int i = 0; i <= 4; i++) {
      if(i == 0) {
        assert binCoeff(4,i) == 1;
        
      } else if(i == 1) {
        assert binCoeff(4,i) == 4;
        
      } else if(i == 2) {
        assert binCoeff(4,i) == 6;
        
      } else if(i == 3) {
        assert binCoeff(4,i) == 4;
        
      } else if(i == 4) {
        assert binCoeff(4,i) == 1;
      }
    }
    System.out.println("Binomial tests...okay!");

    //Bit vector tests
    System.out.println("Random vector: 4 choose 0: " + Arrays.toString(randomBitVector(4,0)));
    System.out.println("Random vector: 4 choose 2: " + Arrays.toString(randomBitVector(4,2)));
    System.out.println("Random vector: 4 choose 4: " + Arrays.toString(randomBitVector(4,4)));
  }
}
