package edu.stanford.nlp.mt.tools;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.IStrings;
import edu.stanford.nlp.mt.base.FlatPhraseTable;
import edu.stanford.nlp.mt.base.PhraseTable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;
import edu.stanford.nlp.mt.base.TranslationOption;

import java.io.*;
import java.util.*;

import static java.lang.System.*;

/**
 * 
 * @author danielcer
 * 
 */
public class DumpPossibleWordCoOccurrences {
  static public void main(String[] args) throws IOException {
    if (args.length != 2) {
      err.printf("Usage:\n\tjava DumpPossibleWordCoOcurrences (phrase table) (source text)\n");
      exit(-1);
    }

    String phraseTableFilename = args[0];
    String sourceTextFilename = args[1];
    PhraseTable<IString> ppt = new FlatPhraseTable<String>(null,
        phraseTableFilename);

    LineNumberReader reader = new LineNumberReader(new FileReader(
        sourceTextFilename));

    Map<IString, List<Integer>> wordSents = new HashMap<IString, List<Integer>>();

    for (String line = reader.readLine(); line != null; line = reader
        .readLine()) {
      if (reader.getLineNumber() % 10 == 0)
        err.print(".");
      Sequence<IString> tokens = new SimpleSequence<IString>(
          IStrings.toIStringArray(line.split("\\s+")));
      int tokensSz = tokens.size();
      Set<IString> possibleTranslationWords = new HashSet<IString>();
      int longestForeign = ppt.longestForeignPhrase();
      if (longestForeign < 0)
        longestForeign = -longestForeign;
      for (int i = 0; i < tokensSz; i++) {
        int jMax = Math.min(tokensSz, i + longestForeign);
        for (int j = i + 1; j <= jMax; j++) {
          Sequence<IString> phrase = tokens.subsequence(i, j);
          List<TranslationOption<IString>> opts = ppt
              .getTranslationOptions(phrase);
          if (opts == null)
            continue;
          for (TranslationOption<IString> opt : opts) {
            for (IString word : opt.translation) {
              possibleTranslationWords.add(word);
            }
          }
        }
      }

      Integer sentId = reader.getLineNumber() - 1; // use a shared 'Integer'
                                                   // object for all words
      for (IString word : possibleTranslationWords) {
        List<Integer> sents = wordSents.get(word);
        if (sents == null) {
          sents = new LinkedList<Integer>();
          wordSents.put(word, sents);
        }
        sents.add(sentId);
      }
    }

    List<String> sortedWords = new ArrayList<String>(wordSents.size());
    for (IString word : wordSents.keySet()) {
      sortedWords.add(word.toString());
    }

    Collections.sort(sortedWords);

    for (String word : sortedWords) {
      out.printf("%s %s\n", word, wordSents.get(new IString(word)).toString()
          .replaceAll("[^0-9 ]", ""));
    }
  }
}
