package edu.stanford.nlp.mt.reranker;

/**
 * @author Pi-Chuan Chang
 */
public interface StatsFactory {
  public Stats newStats(String sent, String[] refs);
}
