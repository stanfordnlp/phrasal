package edu.stanford.nlp.mt.base;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.io.FileInputStream;
import java.io.DataInputStream;
import java.io.FileWriter;
import java.io.PrintWriter;

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
   
   public String addCommas(long num) {
       Long inNum = new Long(num);
       String numStr = inNum.toString();
       int count = 0;
       for (int i = numStr.length(); i > 0; i--) {
           if (count == 3) {
               numStr = numStr.substring(0, i) + "," + numStr.substring(i);
               count = 0;
           }
           count++;
       }
       return numStr;
   }

   public boolean usesArabicNumbers(String inString) {
       String newString = ""; // new string without auxillary characters
       for (int i = 0; i < inString.length(); i++) { 
           String charAtI = inString.substring(i, i+1);
           if (!basicNumberChars.containsKey(charAtI) &&
               !specialNumberChars.containsKey(charAtI) &&
               !auxChars.contains(charAtI)) newString += charAtI;
       }
       try {
           double num = Double.parseDouble(newString);
           return true;
       } catch (NumberFormatException nfe) {
           return false;
       }               
   }
   
   public String getOrdinal(int num) {
       if (num == 1) {
           return "st";
       }
       if (num == 2) {
           return "nd";
       }
       if (num == 3) {
           return "rd";
       }
       if (num == 21) {
           return "st";
       }
       if (num == 22) {
           return "nd";
       }
       if (num == 23) {
           return "rd";
       }
       if (num == 31) {
           return "st";
       }
       else return "th";
   }
   
   public String numToWord(long num, boolean addSpace) {
       String word = "";
       if (num >= 1000000000000L) {
           word += numToWord(num / 1000000000000L, true) + "trillion";
           if (num % 1000000000000L != 0) word += " " + numToWord(num % 1000000000000L, false);
       } else if (num >= 1000000000) {
           word += numToWord(num / 1000000000, true) + "billion";
           if (num % 1000000000 != 0) word += " " + numToWord(num % 1000000000, false);
       } else if (num >= 1000000) {
           word += numToWord(num / 1000000, true) + "million";
           if (num % 1000000 != 0) word += " " + numToWord(num % 1000000, false);
       } else if (num >= 1000) {
           word += numToWord(num / 1000, true) + "thousand";
           if (num % 1000 != 0) word += " " + numToWord(num % 1000, false);
       } else if (num >= 100) {
           word += numToWord(num / 100, true) + "hundred";
           if (num % 100 != 0) word += " " + numToWord(num % 100, false);
       } else if (num > 20) {
           word += tensPlace.get((int)num / 10);
           if (num % 10 != 0) word += "-" + numToWord(num % 10, false);
       } else if (num <= 20 && num >= 0) word += smallNumberWords.get((int)num);
       if (addSpace) word += " ";
       return word;
   }
   
   public long getRawNumber(String chStr, boolean usesArabic) {
       // takes a string that is assumed to be a well-formed raw counting number in Chinese, using Chinese or Arabic numerals
       // returns its English equivalent as a long
       String enStr = "";
       long enNum = 0;
       for (int i = chStr.length() - 1; i >= 0; i--) {
           String charAtI = chStr.substring(i, i+1);
           int prev = 1; // represents the modifying value of characters to the left of a special number character (10, 100, 1000, 10000, 100 million)
           if (charAtI.equals("十")) {
               if (usesArabic && i > 0 && !specialNumberChars.containsKey(chStr.substring(i-1, i))) {
                   prev = Integer.parseInt(chStr.substring(i-1,i));
                   i--;
               } else if (i > 0 && basicNumberChars.containsKey(chStr.substring(i-1, i))) {
                   prev = basicNumberChars.get(chStr.substring(i-1, i));
                   i--;
               }
               enNum += (prev * 10);
           } else if (charAtI.equals("百")) {
               if (usesArabic && i > 0 && !specialNumberChars.containsKey(chStr.substring(i-1,i))) {
                   prev = Integer.parseInt(chStr.substring(i-1,i));
                   i--;
               } else if (i > 0 && basicNumberChars.containsKey(chStr.substring(i-1, i))) {
                   prev = basicNumberChars.get(chStr.substring(i-1, i));
                   i--;
               }
               enNum += (prev * 100);
           } else if (charAtI.equals("千")) {
               if (usesArabic && i > 0 && !specialNumberChars.containsKey(chStr.substring(i-1,i))) {
                   prev = Integer.parseInt(chStr.substring(i-1,i));
                   i--;
               } else if (i > 0 && basicNumberChars.containsKey(chStr.substring(i-1, i))) {
                   prev = basicNumberChars.get(chStr.substring(i-1, i));
                   i--;
               }
               enNum += (prev * 1000);
           } else if (charAtI.equals("万")) { // wan (10,000) is slightly complicated - take note here
               if (chStr.substring(0, chStr.indexOf("万")).contains("亿")) {// if there is an yi (100 million) anywhere earlier in the string
                   prev = (int)getRawNumber(chStr.substring(chStr.indexOf("亿") + 1, chStr.indexOf("万")), usesArabic);
                   i = chStr.indexOf("亿");
               } else if (chStr.indexOf("万") != 0) {
                   prev = (int)getRawNumber(chStr.substring(0, chStr.indexOf("万")), usesArabic);
                   i = 0;
               }
               enNum += (prev * 10000);
           } else if (charAtI.equals("亿")) {
               if (i > 0) {
                   prev = (int)getRawNumber(chStr.substring(0, i), usesArabic);
                   i = 0;
               }
               enNum += (prev * 100000000);
           } else {
               if (usesArabic) {
                   int j = i;
                   while (j > 0) {  // find the index of the next special number char if it exists
                       String charAtJ = chStr.substring(j, j+1);
                       if (specialNumberChars.containsKey(charAtJ)) break;
                       j--;
                   }
                   if (i == 0) {
                       enNum += Long.parseLong(chStr.substring(0,1));
                       break;
                   } else if (j == 0) enNum += Long.parseLong(chStr.substring(0, i+1)); // at the beginning of the number
                   else enNum += Long.parseLong(chStr.substring(++j, i+1)); // we're still somewhere in the number
                   i = j;
               } else {
                   enNum += basicNumberChars.get(chStr.substring(i, i+1));
               }
           }
       }
       return enNum;
   }

   static Map<String,Integer> basicNumberChars = new HashMap<String,Integer>();
   static {
      basicNumberChars.put("零", 0);  // ling
      basicNumberChars.put("〇", 0);
      basicNumberChars.put("一", 1);  // yi      
      basicNumberChars.put("二", 2);  // er
      basicNumberChars.put("两", 2);  // liang
      basicNumberChars.put("三", 3);  // sān
      basicNumberChars.put("四", 4);  // sì 
      basicNumberChars.put("五", 5);  // wǔ
      basicNumberChars.put("六", 6);  // liù
      basicNumberChars.put("七", 7);  // qī
      basicNumberChars.put("八", 8);  // bā
      basicNumberChars.put("九", 9);  // jiǔ
   }
   
   static Map<String, Integer> specialNumberChars = new HashMap<String,Integer>();
   static {
      specialNumberChars.put("十", 10); // shí
      specialNumberChars.put("百", 100); // bai
      specialNumberChars.put("千", 1000); // qian
      specialNumberChars.put("万", 10000); // wan
      specialNumberChars.put("亿", 100000000); // yi (100 million)
    }
   
    static Set<String> auxChars = new HashSet<String>();
    static {
        auxChars.add("日"); // ri, day
        auxChars.add("号"); // hao, day
        auxChars.add("月"); // yue, month
        auxChars.add("年"); // nian, year
        auxChars.add("代"); // dai, decade
        auxChars.add("分"); // fen, used for fractions/percentages
        auxChars.add("之"); // zhi, used for fractions/percentages
        auxChars.add("%");
        auxChars.add("％");
        auxChars.add(":");
        auxChars.add("/");
        auxChars.add("／");
        auxChars.add("第"); // di, used for ordinal numbers
        
    }
   
    static Map<Integer,String> monthNames = new HashMap<Integer, String>();
    static {
        monthNames.put(1, "January");
        monthNames.put(2, "February");
        monthNames.put(3, "March");
        monthNames.put(4, "April");
        monthNames.put(5, "May");
        monthNames.put(6, "June");
        monthNames.put(7, "July");
        monthNames.put(8, "August");
        monthNames.put(9, "September");
        monthNames.put(10, "October");
        monthNames.put(11, "November");
        monthNames.put(12, "December");
    }
    
    static Map<Integer,String> smallNumberWords = new HashMap<Integer, String>();
    static {
        smallNumberWords.put(0, "zero");
        smallNumberWords.put(1, "one");
        smallNumberWords.put(2, "two");
        smallNumberWords.put(3, "three");
        smallNumberWords.put(4, "four");
        smallNumberWords.put(5, "five");
        smallNumberWords.put(6, "six");
        smallNumberWords.put(7, "seven");
        smallNumberWords.put(8, "eight");
        smallNumberWords.put(9, "nine");
        smallNumberWords.put(10, "ten");
        smallNumberWords.put(11, "eleven");
        smallNumberWords.put(12, "twelve");
        smallNumberWords.put(13, "thirteen");
        smallNumberWords.put(14, "fourteen");
        smallNumberWords.put(15, "fifteen");
        smallNumberWords.put(16, "sixteen");
        smallNumberWords.put(17, "seventeen");
        smallNumberWords.put(18, "eighteen");
        smallNumberWords.put(19, "nineteen");
        smallNumberWords.put(20, "twenty");
    }
    
    static Map<Integer,String> tensPlace = new HashMap<Integer,String>();
    static {
        tensPlace.put(2, "twenty");
        tensPlace.put(3, "thirty");
        tensPlace.put(4, "forty");
        tensPlace.put(5, "fifty");
        tensPlace.put(6, "sixty");
        tensPlace.put(7, "seventy");
        tensPlace.put(8, "eighty");
        tensPlace.put(9, "ninety");
    }
   
   @Override
   public List<TranslationOption<IString>> getTranslationOptions(Sequence<IString> chinesePhrase) {
      String firstWord = chinesePhrase.get(0).word();
      int size = firstWord.length();
      List<TranslationOption<IString>> candidateTranslations = new LinkedList<TranslationOption<IString>>();
      StringBuffer englishNumber = new StringBuffer();
      StringBuffer altTrans = new StringBuffer();

      // minor preprocessing - check for certain specific auxillary characters and remove them
      if (firstWord.contains("多") || firstWord.contains("余")) { // this is crude at the moment
          String temp = "";
          int auxIndex = 0;
          if (firstWord.contains("多")) auxIndex = firstWord.indexOf("多");
          else auxIndex = firstWord.indexOf("余");
          temp += firstWord.substring(0, auxIndex);
          if (auxIndex+1 != firstWord.length()) temp += firstWord.substring(auxIndex+1);
          firstWord = temp;
          englishNumber.append("more than ");          
      }
      
      boolean usesArabic = usesArabicNumbers(firstWord);

      if (firstWord.contains("分")) {
          if (firstWord.contains("百")) { // currently will accidentally catch fractions that otherwise have hundreds in them - needs fixing, but quite uncommon
              // percentage
              if (firstWord.contains("之")) englishNumber.append(getRawNumber(firstWord.substring(3, size), usesArabic));
              else englishNumber.append(getRawNumber(firstWord.substring(2, size), usesArabic));
              englishNumber.append("%");              
          } else {
              // fraction - note: prints exactly as given, does not simplify fractions
              if (firstWord.contains("之")) englishNumber.append(getRawNumber(firstWord.substring(firstWord.indexOf("之") + 1, size), usesArabic));
              else englishNumber.append(getRawNumber(firstWord.substring(firstWord.indexOf("分") + 1, size), usesArabic));
              englishNumber.append("/");
              englishNumber.append(getRawNumber(firstWord.substring(0, firstWord.indexOf("分")), usesArabic)); 
          }
      } else if (firstWord.contains("％") || firstWord.contains("%")) {
          // percentages with percent signs - parse arabic numbers directly to avoid decimal problems
          if (usesArabic) {
              double num = Double.parseDouble(firstWord.substring(0, firstWord.length()-1));
              if (num % 1 == 0) englishNumber.append(Integer.parseInt(firstWord.substring(0, firstWord.length()-1))); // avoid dangling .0s if it's an integer
              else englishNumber.append(num);
          } else englishNumber.append(getRawNumber(firstWord.substring(0, firstWord.length()-1), usesArabic));
          englishNumber.append("%");    
      } else if (firstWord.contains("／") || firstWord.contains("/")) {
          // fractions with slashes
          int slashIndex = 0;
          if (firstWord.contains("／")) slashIndex = firstWord.indexOf("／"); // chinese slash
          else slashIndex = firstWord.indexOf("/"); // english slash
          englishNumber.append(getRawNumber(firstWord.substring(0, slashIndex), usesArabic));
          englishNumber.append("/");
          englishNumber.append(getRawNumber(firstWord.substring(slashIndex+1), usesArabic));
          
      } else if (firstWord.contains("年") || firstWord.contains("月") || firstWord.contains("日") || firstWord.contains("号")) {
          // dates
          if (firstWord.contains("代")) {
              // decade
              englishNumber.append("the ");
              englishNumber.append(getRawNumber(firstWord.substring(0, firstWord.indexOf("年")), usesArabic));
              englishNumber.append("s");
          } else {
              boolean hasDay = false;
              boolean hasMonth = false;
              if (firstWord.contains("月")) {
                  // month
                  hasMonth = true;
                  int startIndex = 0;
                  if (firstWord.contains("年")) startIndex = firstWord.indexOf("年") + 1;
                  int endIndex = firstWord.indexOf("月");
                  englishNumber.append(monthNames.get((int)getRawNumber(firstWord.substring(startIndex, endIndex), usesArabic)));
              }
              if (firstWord.contains("日") || firstWord.contains("号")) {
                  // day
                  hasDay = true;
                  if (hasMonth) englishNumber.append(" ");
                  int startIndex = 0;
                  if (firstWord.contains("月")) startIndex = firstWord.indexOf("月") + 1;
                  int endIndex = 0;
                  if (firstWord.contains("日")) endIndex = firstWord.indexOf("日");
                  else endIndex = firstWord.indexOf("号");
                  englishNumber.append(getRawNumber(firstWord.substring(startIndex, endIndex), usesArabic));
                  englishNumber.append(getOrdinal((int)getRawNumber(firstWord.substring(startIndex, endIndex), usesArabic)));
              }
              if (firstWord.contains("年")) {
                  // year
                  if (hasDay || hasMonth) englishNumber.append(", ");                      
                  if (usesArabic) englishNumber.append(firstWord.substring(0, firstWord.indexOf("年")));
                  else {
                      for (int i = 0; i < firstWord.indexOf("年"); i++) {
                          String charAtI = firstWord.substring(i, i+1);
                          englishNumber.append(basicNumberChars.get(charAtI));
                      }
                  }
              }
          }
      } else if (firstWord.contains("第")) {
          // ordinal
          int num = (int)getRawNumber(firstWord.substring(firstWord.indexOf("第")+1), usesArabic);
          englishNumber.append(num);
          englishNumber.append(getOrdinal(num));
      } else {
          // cardinal or money
          if (usesArabic && firstWord.contains(".")) { 
              // decimal handling
              if (specialNumberChars.containsKey(firstWord.substring(firstWord.length()-1))) {
                  long num = 0;
                  if (firstWord.endsWith("亿")) num = (long)(Double.parseDouble(firstWord.substring(0, firstWord.length()-2)) * 100000000L);
                  else if (firstWord.endsWith("万")) num = (long)(Double.parseDouble(firstWord.substring(0, firstWord.length()-2)) * 10000);
                  englishNumber.append(addCommas(num));
                  altTrans.append(numToWord(num, false));
              } else { // direct decimal with no chinese characters
                  englishNumber.append(firstWord);
              }
          } else { 
              long num = getRawNumber(firstWord, usesArabic);
              if (num > 999) englishNumber.append(addCommas(num));
              else englishNumber.append(num);
              altTrans.append(numToWord(num, false)); 
          }
      }
      
      if (!englishNumber.toString().equals("")) {
          RawSequence<IString> englishNumberAsSequence =
               new RawSequence<IString>(new IString[] { new IString(englishNumber.toString()) });
          candidateTranslations.add(new TranslationOption<IString>(
                  new float[]{(float)1.0}, // whatever scores you want to assign to this translation
                  new String[]{"zhNumberScore1"}, // score names you wan to assign to this translation,
                  englishNumberAsSequence,
                  new RawSequence<IString>(chinesePhrase), null));    
      }
      if (!altTrans.toString().equals("")) {
          RawSequence<IString> altTransAsSequence =
               new RawSequence<IString>(new IString[] { new IString(altTrans.toString()) });
          candidateTranslations.add(new TranslationOption<IString>(
                  new float[]{(float)1.0}, // whatever scores you want to assign to this translation
                  new String[]{"zhNumberScore1"}, // score names you wan to assign to this translation,
                  altTransAsSequence,
                  new RawSequence<IString>(chinesePhrase), null));    
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
      
      // file reader error checker
      /*
      System.out.println("MandarinNumberPhraseGenerator Error Checker");
      System.out.println("Input Test File:");
      System.out.print("> ");
      String file = reader.readLine();
      System.out.println("Output Filename:");
      System.out.print("> ");
      String outputFile = reader.readLine();

      
      FileWriter fstream = new FileWriter(outputFile);
      PrintWriter out = new PrintWriter(fstream);
      DataInputStream in = new DataInputStream(new FileInputStream(file));
      BufferedReader fileIn = new BufferedReader(new InputStreamReader(in));
      String line;
      
      while ((line = fileIn.readLine()) != null) {
          System.out.println(line);
          String[] tokens = line.split("\\s+");
          Sequence<IString> phrase = new RawSequence<IString>(IStrings.toIStringArray(tokens));
          try {
              List<TranslationOption<IString>> opts = mnpg.getTranslationOptions(phrase);
              out.write("(" + line + "||" + opts.get(0).translation.get(0).word().toString() + ")");
              out.println();
          } catch (Exception e) {
              out.write("Error getting translations for " + line);
              out.println();
          }
      }
      fileIn.close();
      in.close();
      
      out.close();
      fstream.close();
     
      // end of file reader error checker 
      */
      
      // Dan's original REPL
      
      System.out.println("MandarinNumberPhraseGenerator Interactive REPL");
      System.out.println("(ctrl-c to quit)");
      System.out.print("> ");
      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
         String[] tokens = line.split("\\s+");
         Sequence<IString> phrase = new RawSequence<IString>(IStrings.toIStringArray(tokens));
         
         try {
             List<TranslationOption<IString>> opts = mnpg.getTranslationOptions(phrase);
             if (opts.isEmpty()) {
                System.out.println("No translation available");
             } else {
                System.out.println("Options");
                for (TranslationOption<IString> opt : opts) {
                   System.out.printf("\t%s\n", opt);
                }
             }
         } catch (Exception e) {
             System.out.println("Error getting translation:");
             e.printStackTrace();
         }
         System.out.print("> ");
      }
   }
}