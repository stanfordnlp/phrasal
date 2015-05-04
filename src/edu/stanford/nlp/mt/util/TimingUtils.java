package edu.stanford.nlp.mt.util;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.LinkedList;
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
    return elapsedSeconds(startTime, System.nanoTime());
  }
  
  public static double elapsedSeconds(long startTime, long endTime) {
    return (endTime - startTime) / 1e9;
  }
  
  public static class TimeKeeper {
    private final List<Long> marks;
    private final List<String> labels;
    private TimeKeeper() {
      this.marks = new LinkedList<>();
      this.labels = new LinkedList<>();
      marks.add(System.nanoTime());
      labels.add("Start");
    }
    public synchronized void mark(String label) {
      marks.add(System.nanoTime());
      labels.add(label);
    }
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      NumberFormat nf = new DecimalFormat("0.000");
      for (int i = 1, sz = marks.size(); i < sz; ++i) {
        if (i > 1) sb.append(" || ");
        double elapsed = elapsedSeconds(marks.get(i-1), marks.get(i));
        sb.append(labels.get(i)).append(" ").append(nf.format(elapsed));
      }
      return sb.toString();
    }
  }
}
