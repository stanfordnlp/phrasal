package mt.visualize.phrase;

import java.util.Random;

import edu.stanford.nlp.stats.*;

public class ScoreDistribution {

  public final int MAX_DEVIATION;

  private Counter<Double> counts;
  private double mean = 0.0;
  private double std_dev = 0.0;
  private boolean computed = false;
  private int numSamples = 0;

  public ScoreDistribution(int maxDeviation) {
    MAX_DEVIATION = maxDeviation;
    counts = new ClassicCounter<Double>();
  }

  public int getStdDev(double score) {
    if(!computed) return -1;

    double sign = 1.0;
    if(score < mean) {
      score = 1.0 - score;
      sign = -1.0;
    }

    double maxDev = (double) MAX_DEVIATION;
    for(double i = 0; i < maxDev; i++)
      if(isInRange(score,mean + (i * std_dev), mean + ((i + 1.0) * std_dev)))
        return (int) (i * sign);

    return (int) (maxDev * sign);
  }

  public void add(double d) {
    if(computed) return;
    counts.incrementCount(round(d));
  }

  public void computeDistribution() {
    if(computed) return;

    //Compute the sample mean
    double mean_num = 0.0;
    for(double d : counts.keySet())
      mean_num += d * counts.getCount(d);
    mean = mean_num / counts.totalCount();

    //Compute the sample variance
    double varNum = 0.0;
    for(double d : counts.keySet())
      varNum += ( (d - mean) * (d - mean) );
    double variance = varNum / (counts.totalCount() - 1.0);
    std_dev = Math.sqrt(variance);

    computed = true;
    numSamples = (int) counts.totalCount();
    counts = null; //Mark for GC
  }

  private boolean isInRange(double val, double lower, double upper) {
    return (lower <= val) && (val <= upper);
  }

  private double round(double d) {
    int dPrecision = (int) (d * 10.0);
    return (double) dPrecision / 10.0;
  }

  @Override
  public String toString() {
    return String.format("samples:\t%d\nmean:\t%f\nstddev:\t%f", numSamples,mean,std_dev);
  }



  /**
   * @param args
   */
  public static void main(String[] args) {
    ScoreDistribution dist = new ScoreDistribution(300);
    Random random = new Random();

    for(int i = 0; i < 100000; i++)
      dist.add(random.nextDouble());
    dist.computeDistribution();

    System.out.println(dist);

    System.out.printf("%f: %d\n",0.2,dist.getStdDev(0.2));
    System.out.printf("%f: %d\n",0.4,dist.getStdDev(0.4));
    System.out.printf("%f: %d\n",0.6,dist.getStdDev(0.6));
    System.out.printf("%f: %d\n",0.8,dist.getStdDev(0.8));
  }

}
