package edu.stanford.nlp.mt.util;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper methods for timing things.
 * 
 * @author Spence Green
 *
 */
public final class TimingUtils {

  private TimingUtils() {}

  /**
   * Get a TimeKeeper object.
   * 
   * @return
   */
  public static TimeKeeper start() { return new TimeKeeper(); }
  
  /**
   * Get a start time.
   * 
   * @return
   */
  public static long startTime() { return System.nanoTime(); }
  
  /**
   * Get the elapsed time in seconds.
   * 
   * @param startTime
   * @return
   */
  public static double elapsedSeconds(long startTime) {
    return elapsedTime(startTime, System.nanoTime(), 1e9);
  }
  
  private static double elapsedTime(long startTime, long endTime, double units) {
    return (endTime - startTime) / units;
  }
  
  public static long elapsedMillis(long startTime) {
    return (long) elapsedTime(startTime, System.nanoTime(), 1e6);
  }
  
  public static class TimeKeeper {
    private final List<Long> marks;
    private final List<String> labels;
    private TimeKeeper() {
      this.marks = new ArrayList<>();
      this.labels = new ArrayList<>();
      marks.add(System.nanoTime());
      labels.add("Start");
    }
    public synchronized void mark(String label) {
      marks.add(System.nanoTime());
      labels.add(label);
    }
    public long elapsedNano() { return marks.get(marks.size()-1) - marks.get(0); }
    public long elapsedMillis() { return (long) (elapsedNano() / 1e6); }
    public double elapsedSecs() { return elapsedNano() / 1e9; }
    
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      NumberFormat nf = new DecimalFormat("0.000");
      for (int i = 1, sz = marks.size(); i < sz; ++i) {
        if (i > 1) sb.append(" || ");
        double elapsed = elapsedTime(marks.get(i-1), marks.get(i), 1e9);
        sb.append(labels.get(i)).append(" ").append(nf.format(elapsed));
      }
      sb.append(" || Total ").append(" ").append(nf.format(elapsedSecs()));
      return sb.toString();
    }
  }
}
