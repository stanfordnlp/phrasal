package mt.train;

import java.util.*;
import java.io.*;

import mt.base.IOTools;
import mt.base.Sequence;
import mt.base.SimpleSequence;

import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;

/**
 * Toolkit for extracting source-language n-grams that must be considered
 * for feature extraction.
 *
 * @author Michel Galley
 */
 
public class SourceFilteringToolkit {

  public static final String SHOW_PHRASE_RESTRICTION_PROPERTY = "ShowPhraseRestriction";
  public static final boolean SHOW_PHRASE_RESTRICTION = 
    Boolean.parseBoolean(System.getProperty(SHOW_PHRASE_RESTRICTION_PROPERTY, "false"));

  /**
   * Restrict feature extraction to source-language phrases that appear in 
   * a given test/dev corpus.
   *
   * @param fFilterCorpus
   */
  @SuppressWarnings("unchecked")
  public static Sequence<IString>[] getPhrasesFromFilterCorpus(String fFilterCorpus, int maxPhraseLenF, boolean addBoundaryMarkers) {
    AlignmentTemplates tmpSet;
    tmpSet = new AlignmentTemplates();
    System.err.println("Filtering against corpus: "+fFilterCorpus);
    //filterFromDev = true;  
    try {
      LineNumberReader fReader = IOTools.getReaderFromFile(fFilterCorpus);
      for (String fLine; (fLine = fReader.readLine()) != null; )
        extractPhrasesFromLine(tmpSet, fLine, maxPhraseLenF, addBoundaryMarkers);
      fReader.close();
    } catch(IOException e) {
      e.printStackTrace();
    }
    Sequence<IString>[] phrases = new Sequence[tmpSet.sizeF()];
    for(int i=0; i<phrases.length; ++i) {
      int[] fArray = tmpSet.getF(i);
      phrases[i] = new SimpleSequence<IString>(true, IStrings.toIStringArray(fArray));
    }
    Collections.shuffle(Arrays.asList(phrases));
    return phrases;
  }

  private static void extractPhrasesFromLine(AlignmentTemplates set, String fLine, int maxPhraseLenF, boolean addBoundaryMarkers) {
    if(addBoundaryMarkers)
      fLine = new StringBuffer("<s> ").append(fLine).append(" </s>").toString();
    Sequence<IString> f = new SimpleSequence<IString>(true, IStrings.toIStringArray(fLine.split("\\s+")));
    for(int i=0; i<f.size(); ++i) {
      for(int j=i; j<f.size() && j-i<maxPhraseLenF; ++j) {
        Sequence<IString> fPhrase = f.subsequence(i,j+1);
        if(SHOW_PHRASE_RESTRICTION)
          System.err.printf("restrict to phrase (i=%d,j=%d,M=%d): %s\n",i,j,maxPhraseLenF,fPhrase.toString());
        set.addForeignPhraseToIndex(fPhrase);
      }
    }
  }

  /**
   * Restrict feature extraction to a pre-defined list of source-language phrases.
   */
  @SuppressWarnings("unchecked")
  public static Sequence<IString>[] getPhrasesFromList(String fileName) {
    ArrayList<Sequence<IString>> list = new ArrayList<Sequence<IString>>();
    System.err.println("Filtering against list: "+fileName);
    //filterFromDev = true;
    try {
      LineNumberReader fReader = IOTools.getReaderFromFile(fileName);
      for (String fLine; (fLine = fReader.readLine()) != null; ) {
        Sequence<IString> f = new SimpleSequence<IString>(true, IStrings.toIStringArray(fLine.split("\\s+")));
        if(SHOW_PHRASE_RESTRICTION)
          System.err.printf("restrict to phrase: %s\n",f.toString());
        list.add(f);
      }
      fReader.close();
    } catch(IOException e) {
      e.printStackTrace();
    }
    return list.toArray(new Sequence[list.size()]);
  }
}
