package edu.stanford.nlp.mt.base;

import edu.stanford.nlp.mt.train.DTUPhraseExtractor;

import java.util.*;


/**
 * 
 * @author Michel Galley
 *
 * @param <T>
 */
public class DTUOption<T> extends TranslationOption<T> {

  public final RawSequence<T>[] dtus;

  @SuppressWarnings("unchecked")
  private final static RawSequence emptySeq = new RawSequence(new Object[0]);

  @SuppressWarnings("unchecked")
  public DTUOption(int id, float[] scores, String[] phraseScoreNames, RawSequence<T>[] dtus, RawSequence<T> foreign, PhraseAlignment alignment) {
		super(id, scores, phraseScoreNames, emptySeq, foreign, alignment);
    this.dtus = dtus;
    //System.err.println("DTUOption: "+dtus.length);
  }
	
	public DTUOption(int id, float[] scores, String[] phraseScoreNames, RawSequence<T>[] dtus, RawSequence<T> foreign, PhraseAlignment alignment, boolean forceAdd) {
		super(id, scores, phraseScoreNames, null, foreign, alignment, forceAdd);
    this.dtus = dtus;
	}
	
	@Override
	public String toString() {
		StringBuffer sbuf = new StringBuffer("TranslationOption: \"");
    for(int i=0; i<dtus.length; ++i) {
      if(i>0)
        sbuf.append(" ").append(DTUPhraseExtractor.GAP_STR.word()).append(" ");
      sbuf.append(dtus[i].toString());
    }
    sbuf.append(String.format("\" scores: %s\n", Arrays.toString(scores)));
		return sbuf.toString();
	}
}
