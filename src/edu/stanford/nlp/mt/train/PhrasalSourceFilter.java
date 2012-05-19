package edu.stanford.nlp.mt.train;

import java.io.*;

import edu.stanford.nlp.mt.base.DynamicIntegerArrayIndex;
import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.Sequences;

public class PhrasalSourceFilter extends AbstractSourceFilter {

  private final boolean addBoundaryMarkers;

  public PhrasalSourceFilter(int maxPhraseLenF, boolean addBoundaryMarkers) {
    super(maxPhraseLenF, new DynamicIntegerArrayIndex());
    System.err.println("creating PhrasalSourceFilter with maxPhraseLenF=" + maxPhraseLenF);
    this.addBoundaryMarkers = addBoundaryMarkers;
  }

  @Override
  public void filterAgainstSentence(String fLine) {

    fLine = fLine.trim();

    if (addBoundaryMarkers)
      fLine = new StringBuffer("<s> ").append(fLine).append(" </s>").toString();

    Sequence<IString> f = new SimpleSequence<IString>(true,
        IStrings.toIStringArray(fLine.split("\\s+")));
    for (int i = 0; i < f.size(); ++i) {
      for (int j = i; j < f.size() && j - i < maxPhraseLenF; ++j) {
        Sequence<IString> fPhrase = f.subsequence(i, j + 1);
        if (SHOW_PHRASE_RESTRICTION)
          System.err.printf("Restrict to phrase (i=%d,j=%d,M=%d): %s\n", i, j,
              maxPhraseLenF, fPhrase.toString());
        sourcePhraseTable.indexOf(Sequences.toIntArray(fPhrase), true);
      }
    }
  }

  /**
   * Restrict feature extraction to a pre-defined list of source-language
   * phrases.
   */
  public void filterAgainstList(String fileName) {
    System.err.println("Filtering against list: " + fileName);
    // filterFromDev = true;
    try {
      LineNumberReader fReader = IOTools.getReaderFromFile(fileName);
      for (String fLine; (fLine = fReader.readLine()) != null;) {
        int[] f = IStrings.toIntArray(IStrings.toIStringArray(fLine
            .split("\\s+")));
        if (SHOW_PHRASE_RESTRICTION)
          System.err.printf("Restrict to phrase: %s\n", f.toString());
        sourcePhraseTable.indexOf(f, true);
      }
      fReader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    isEnabled = true;
  }
}
