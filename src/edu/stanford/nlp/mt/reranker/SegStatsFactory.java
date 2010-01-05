package edu.stanford.nlp.mt.reranker;

/**
 * @author Pi-Chuan Chang
 */
public class SegStatsFactory implements StatsFactory {
  public Stats newStats(String sent, String[] refs) {
    String[] sents = sent.split("\\s+");
    String[][] refss = new String[refs.length][];
    for(int i = 0; i < refs.length; i++) {
      refss[i] = refs[i].split("\\s+");
    }
    return new SegStats(sents, refss);
  }
}
