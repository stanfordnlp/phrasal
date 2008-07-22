package mt.reranker;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * Creates IBM Model 2 alignments between source and target language
 * sentences based on a GIZA++ translation probability table.
 *
 * Displacement formula is based on William's 224n project, and the
 * default parameters are tuned so as to minimize AER on the
 * French-English corpus. You may wish to tune those yourself if you
 * want better alignments.
 *
 * Although we retain the lexicalized lexical NULL probabilities from
 * the Model 1 class, we do have a fixed dispalcement NULL probability.
 **/
public class Model2Aligner extends Model1Aligner {
  /* these two are fixed arbitrarily */
  public static final int DEFAULT_NUM_BUCKETS = 10;
  public static final double DEFAULT_LAST_BUCKET_VAL = 0.01;
  public static final double DEFAULT_NULL_DISPLACEMENT_PROB = 0.05;

  /* these two have been tuned to minimize AER on some French-English
   * corpus as part of William's 224n project*/
  public static final double DEFAULT_LAST_BUCKET_DISP = 4.0;
  public static final double DEFAULT_BUCKET_SLOPE = 5; //8.2;

  public int numBuckets = DEFAULT_NUM_BUCKETS;
  public double lastBucketDisp = DEFAULT_LAST_BUCKET_DISP;
  public double lastBucketVal = DEFAULT_LAST_BUCKET_VAL;
  public double bucketSlope = DEFAULT_BUCKET_SLOPE;
  public double nullDisplacementProb = DEFAULT_NULL_DISPLACEMENT_PROB;

  public Model2Aligner() throws Exception { super(); } /* java sucks */
  
  private double bucketScore(double posE, double posF, double lenE, double lenF) {
    double disp = Math.abs(posE - posF * (lenE / lenF));
    double bucketSize = lastBucketDisp / numBuckets;
    int bucketNumber = (int)(disp / bucketSize);
    double score = 0.0;
    if (bucketNumber > numBuckets - 1) score = lastBucketVal;
    else score = (numBuckets - bucketNumber) * bucketSlope;

    return score;
  }

  @Override
	public double displacementProb(String source, String target, int sourcePos, int sourceLen, int targetPos, int targetLen) {
    if(targetPos == -1) return nullDisplacementProb; /* aligning to null */

    double bs = bucketScore(targetPos, sourcePos, targetLen, sourceLen);

    /* probably should do this analytically. */
    double norm = 0.0;
    for(int i = 0; i < targetLen; i++) norm += bucketScore(i, sourcePos, targetLen, sourceLen);

    return bs / (norm * (1.0 - nullDisplacementProb));
  }

  public static void main(String[] argv) throws Exception {
    Model2Aligner m = new Model2Aligner();

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
