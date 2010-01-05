package edu.stanford.nlp.mt.reranker;

/**
 * @author Pi-Chuan Chang
 */
public class EditStatsFactory implements StatsFactory {
  public Stats newStats(String sentence, String[] refs) {
    return new EditStats(sentence, refs);
  }
}
