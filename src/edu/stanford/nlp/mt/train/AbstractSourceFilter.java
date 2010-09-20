package edu.stanford.nlp.mt.train;

import edu.stanford.nlp.mt.base.IOTools;
import edu.stanford.nlp.mt.base.IntegerArrayIndex;
import edu.stanford.nlp.mt.base.Sequences;

import java.io.IOException;
import java.io.LineNumberReader;

/**
 * @author Michel Galley
 */
public abstract class AbstractSourceFilter implements SourceFilter {

  public static final String SHOW_PHRASE_RESTRICTION_PROPERTY = "ShowPhraseRestriction";
  public static final boolean SHOW_PHRASE_RESTRICTION =
    Boolean.parseBoolean(System.getProperty(SHOW_PHRASE_RESTRICTION_PROPERTY, "false"));

  protected final IntegerArrayIndex sourcePhraseTable;
  protected final int maxPhraseLenF;

  protected boolean isEnabled = false;
  protected int startId, endId;

  public AbstractSourceFilter(int maxPhraseLenF, IntegerArrayIndex sourcePhraseTable) {
    this.maxPhraseLenF = maxPhraseLenF;
    this.sourcePhraseTable = sourcePhraseTable;
  }

  public abstract void filterAgainstSentence(String sourceSentence);

  /**
   * Restrict feature extraction to source-language phrases that appear in
   * a given test/dev corpus.
   */
  @Override
  public void filterAgainstCorpus(String sourceLanguageCorpus) {
    System.err.println("Enumerating phrases in: "+sourceLanguageCorpus);
    System.err.print("Line");
    try {
      LineNumberReader fReader = IOTools.getReaderFromFile(sourceLanguageCorpus);
      int lineNb = 0;
      for (String fLine; (fLine = fReader.readLine()) != null; ) {
        if (lineNb % 100 == 0)
          System.err.printf(" %d...", lineNb);
        filterAgainstSentence(fLine);
        ++lineNb;
      }
      fReader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    System.err.printf("\nPhrases in %s: %d\n", sourceLanguageCorpus, size());
		isEnabled = true;
  }

  @Override
  public boolean allows(AlignmentTemplate alTemp) {
    int fKey = sourcePhraseTable.indexOf(Sequences.toIntArray(alTemp.f()), false);
    return fKey >= 0 && fKey >= startId && fKey < endId;
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void lock() {
    this.sourcePhraseTable.lock();
  }

  @Override
  public void setRange(int startId, int endId) {
    this.startId = startId;
    this.endId = endId;
  }

  @Override
  public int size() {
    return sourcePhraseTable.size();
  }
  
  public IntegerArrayIndex getSourceIndex() {
    return sourcePhraseTable;
  }
}
