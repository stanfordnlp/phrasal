package mt.hmmalign;

/**
 * Used as a wrapper for values in a HashMap which hold
 * double values and integer counts.
 * Should really use a nice utility function in javanlp.util.
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */

public class DoubleIntHolder {

  private double value;
  private int cnt;

  public DoubleIntHolder(double val, int cnt) {
    value = val;
    this.cnt = cnt;
  }

  public DoubleIntHolder() {
  };

  public void setVal(double val) {
    value = val;
  }

  public void incVal(double val) {
    value += val;
  }

  public void setCnt(int val) {
    cnt = val;
  }

  public void incCnt(int val) {
    cnt += val;
  }

  public double getValue() {
    return value;
  }

  public int getCount() {
    return cnt;
  }

} 
