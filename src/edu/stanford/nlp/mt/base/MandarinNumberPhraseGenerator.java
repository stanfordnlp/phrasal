package edu.stanford.nlp.mt.base;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Scorer;

public class MandarinNumberPhraseGenerator extends AbstractPhraseGenerator<IString,String> {
   public static final String NUMBER_PHRASE_GENERATOR_NAME = "NumberPhraseGenerator";
   public static boolean DEBUG = true;
   
   public MandarinNumberPhraseGenerator(IsolatedPhraseFeaturizer<IString, String> phraseFeaturizer,
         Scorer<String> scorer) {
      super(phraseFeaturizer, scorer);
   }

   @Override
   public String getName() {
      return NUMBER_PHRASE_GENERATOR_NAME;     
   }

   static Map<String,Integer> basicMandarinNumberCharacters = new HashMap<String,Integer>();
   static {
      basicMandarinNumberCharacters.put("一", 1);  // yi
      basicMandarinNumberCharacters.put("二", 2);  // er      
      basicMandarinNumberCharacters.put("三", 3);  // sān
      basicMandarinNumberCharacters.put("四", 4);  // sì 
      basicMandarinNumberCharacters.put("五", 5);  // wǔ
      basicMandarinNumberCharacters.put("六", 6);  // liù
      basicMandarinNumberCharacters.put("七", 7);  // qī
      basicMandarinNumberCharacters.put("八", 8);  // bā
      basicMandarinNumberCharacters.put("九", 9);  // jiǔ
      basicMandarinNumberCharacters.put("十", 10); // shí      
   }
   
   @Override
   public List<TranslationOption<IString>> getTranslationOptions(Sequence<IString> chinesePhrase) {
      String firstWord = chinesePhrase.get(0).word();
      int sourcePhraseLength = chinesePhrase.size();
      List<TranslationOption<IString>> candidateTranslations = new LinkedList<TranslationOption<IString>>();
      
      if (sourcePhraseLength == 1) {
         StringBuffer englishNumber = new StringBuffer();
         // loop over characters in the first (and only) word
         for (int i = 0; i < firstWord.length(); i++) {
            String charAtI = firstWord.substring(i, i+1); 
            if (!basicMandarinNumberCharacters.containsKey(charAtI)) {
               // print debuging into like this
               if (DEBUG) {
                  System.err.printf("Not in basicMandarinNumberCharacters (%s)\n", charAtI);
               }
               englishNumber = null; // we can't handle characters not in basicMandarinNumberCharacters
               break;
            }            
            if (!charAtI.equals("十") || firstWord.length() == 1) {
               englishNumber.append(basicMandarinNumberCharacters.get(charAtI));
            } else if (i == 0 && firstWord.length() == 2) {
               englishNumber.append(1); // special case for 十一 to 十九
            } else {
               englishNumber = null; // we can't handle 十 being in the middle of a word
               break;
            }
         }
         
         
         if (englishNumber != null) {
            RawSequence<IString> englishNumberAsSequence =
               new RawSequence<IString>(new IString[] { new IString(englishNumber.toString()) });
            
            candidateTranslations.add(new TranslationOption<IString>(
                  new float[]{(float)1.0}, // whatever scores you want to assign to this translation
                  new String[]{"zhNumberScore1"}, // score names you wan to assign to this translation,
                  englishNumberAsSequence,
                  new RawSequence<IString>(chinesePhrase), null));     
         }
      }
       
      return candidateTranslations;
   }

   @Override
   public int longestForeignPhrase() {     
      return 2; // change to the longest Mandarin phrase this phrase generator can reasonably process 
   }

   @Override
   public void setCurrentSequence(Sequence<IString> foreign,
         List<Sequence<IString>> tranList) {
      // do nothing      
   }
   
   static public void main(String[] args) throws IOException {
      BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
      
      MandarinNumberPhraseGenerator mnpg = new MandarinNumberPhraseGenerator(null, null);
    
      System.out.println("MandarinNumberPhraseGenerator Interactive REPL");
      System.out.println("(ctrl-c to quit)");
      System.out.print("> ");
      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
         String[] tokens = line.split("\\s+");
         Sequence<IString> phrase = new RawSequence<IString>(IStrings.toIStringArray(tokens));
         List<TranslationOption<IString>> opts = mnpg.getTranslationOptions(phrase);
         if (opts.size() == 0) {
            System.out.println("No translation available");
         } else {
            System.out.println("Options");
            for (TranslationOption<IString> opt : opts) {
               System.out.printf("\t%s\n", opt);
            }
         }
         System.out.print("> ");
      }
   }
}
