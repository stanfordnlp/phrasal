package edu.stanford.nlp.mt.util;

import java.util.List;
import edu.stanford.nlp.util.StringUtils;

public class BasicNBestEntry {
  private String line;
  private int sentenceNumber;
  private Sequence<IString> tokens;
  private float score;

  public BasicNBestEntry(String input) {
    line = input;
    List<List<String>> fields = StringUtils.splitFieldsFast(input.trim(), "|||");
    sentenceNumber = Integer.valueOf(fields.get(0).get(0));
    tokens = IStrings.toIStringSequence(fields.get(1));
    score = Float.parseFloat(fields.get(3).get(0));
  } 

  public String getLine() { return line; }
  public int getNumber() { return sentenceNumber; }
  public Sequence<IString> getTokens() { return tokens; }
  public float getScore() { return score; }
}
