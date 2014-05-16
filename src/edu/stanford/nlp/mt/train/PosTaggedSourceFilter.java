package edu.stanford.nlp.mt.train;

import java.io.*;
import java.util.regex.*;

import edu.stanford.nlp.mt.util.DynamicIntegerArrayIndex;
import edu.stanford.nlp.mt.util.IOTools;
import edu.stanford.nlp.mt.util.IString;
import edu.stanford.nlp.mt.util.IStrings;
import edu.stanford.nlp.mt.util.IntegerArrayIndex;
import edu.stanford.nlp.mt.util.Sequence;
import edu.stanford.nlp.mt.util.Sequences;
import edu.stanford.nlp.mt.util.SimpleSequence;

public class PosTaggedSourceFilter extends AbstractSourceFilter {

  protected IntegerArrayIndex phraseWithoutContentPosTable;
  protected Pattern contentPOSPattern = Pattern.compile("VA|VE|VV|NR|NT|NN|CD|OD|JJ");
  protected Pattern enPunctPattern = Pattern.compile("\\p{Punct}+");
  protected Pattern zhPunctPattern = Pattern.compile("[、。，；：？！“”‘’╚╗└┐（）…——《》〈〉·.]+");
  protected Pattern targetPattern = Pattern.compile("\\p{Punct}+");

  public PosTaggedSourceFilter(int maxPhraseLenF) {
    super(maxPhraseLenF, new DynamicIntegerArrayIndex());
    phraseWithoutContentPosTable = new DynamicIntegerArrayIndex();
  }

  private boolean isContentPOS(String pos, String word) {
    return contentPOSPattern.matcher(pos).matches() && 
      !enPunctPattern.matcher(word).matches() && !zhPunctPattern.matcher(word).matches();
  }

  /**
   * @return true if the target string should be applied the content POS filtering rule
   */
  private boolean targetMatch(String targetStr) {
    return targetPattern.matcher(targetStr).matches();
  }

  @Override
  public void filterAgainstSentence(String fLine) {
    fLine = fLine.trim();
    String[] wordAndPOSs = fLine.trim().split("\\s+");
    int wordArrLen = wordAndPOSs.length;
    String[] wordArr = new String[wordArrLen];
    String[] posArr = new String[wordArrLen];
    for (int i = 0; i < wordArrLen; i++) {
      String[] parts = wordAndPOSs[i].split("#");
      assert(parts.length == 2);
      wordArr[i] = parts[0];
      posArr[i] = parts[1];
    }

    Sequence<IString> f = new SimpleSequence<IString>(true,
        IStrings.toIStringArray(wordArr));
    for (int i = 0; i < f.size(); ++i) {
      boolean contentPOSFound = isContentPOS(posArr[i], wordArr[i]);
      for (int j = i; j < f.size() && j - i < maxPhraseLenF; ++j) {
        Sequence<IString> fPhrase = f.subsequence(i, j + 1);
        if (SHOW_PHRASE_RESTRICTION)
          System.err.printf("Restrict to phrase (i=%d,j=%d,M=%d): %s\n", i, j,
              maxPhraseLenF, fPhrase.toString());
        sourcePhraseTable.indexOf(Sequences.toIntArray(fPhrase), true);
        //detect content POS in the phrase
        contentPOSFound = contentPOSFound || isContentPOS(posArr[j], wordArr[j]); 
        // since there are fewer phrases that DON'T contain content POS than the ones that DO
        // we only store the ones that DON'T
        if (!contentPOSFound)
          phraseWithoutContentPosTable.indexOf(Sequences.toIntArray(fPhrase), true);
      }
    }
  }

  @Override
  public boolean allows(AlignmentTemplate alTemp) {
    if (super.allows(alTemp)) {
      String targetStr = alTemp.e().toString();
      if (targetMatch(targetStr)) {
        // System.err.println("POS filter found target: "+targetStr);
        int[] fIntArray = Sequences.toIntArray(alTemp.f());
        int fKey = phraseWithoutContentPosTable.indexOf(fIntArray, false);
        boolean fNotContainContentPOS = (fKey >= 0);
        //if (!fNotContainContentPOS)
        //  System.err.println("POS filter forbids target: "+alTemp.e().toString() + " source: " + alTemp.f().toString());
        //else
        //  System.err.println("POS filter no content POS found : "+alTemp.e().toString() + " source: " + alTemp.f().toString());
        return fNotContainContentPOS;
      } else {
        //System.err.println("POS filter target mismatch: "+alTemp.e().toString() + " source: " + alTemp.f().toString());
        return true;
      }
    } else {
      //System.err.println("POS filter parent disallow: "+alTemp.e().toString() + " source: " + alTemp.f().toString());
      return false;
    }
  }
}
