package mt.decoder.efeat;

import java.util.List;
import java.util.ArrayList;
import java.io.LineNumberReader;
import java.io.FileReader;

/*
import mt.IString;
*/
import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.IBMModel1;
import mt.base.IString;
import mt.base.Sequence;
import mt.decoder.feat.IncrementalFeaturizer;

import edu.stanford.nlp.ling.WordTag;

/**
 * 
 * implementing word-level discriminative features.
 * ideas similar to "Distortion models for statistical machine translation" 
 *  (ACL06 Al-Onaizan and Papineni)
 * Difference: used as discriminative features.
 * 
 * @author Pi-Chuan Chang
 *
 * @param <TK>
 */
public class WordLevelDiscrimDistortionFeaturizer<TK> implements IncrementalFeaturizer<TK, String> {
  
  public static final String DEBUG_PROPERTY = "DebugWordLevelDiscrimDistortionFeaturizer";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
  public static final String FEATURE_NAME = "WordLevelDiscrimDistortion";

  private static final boolean DETAILED_DEBUG = false;

  private static boolean PAIR_OF_SOURCE_WORDS = true; // always true
  private static boolean FIRST_OF_SOURCE_WORDS = false;
  private static boolean SECOND_OF_SOURCE_WORDS = false;
  private static boolean USE_POS_TAGS = false;
  private static boolean USE_COARSE_DISTORATION_TYPES = false;
  private static boolean TESTING = false;

  private int lineCount = 0;

  private IBMModel1 model1 = null;
  
  private String wordTagFilename = null;
  private LineNumberReader wordTagReader = null;
  private List<WordTag> currentWordTag = null;
  

  public WordLevelDiscrimDistortionFeaturizer(String... args) {
    for (String arg : args) {
      if ("PAIR_OF_SOURCE_WORDS".equals(arg)) {
        System.err.println("PAIR_OF_SOURCE_WORDS=true");
        PAIR_OF_SOURCE_WORDS=true;
      }
      if ("FIRST_OF_SOURCE_WORDS".equals(arg)) {
        System.err.println("FIRST_OF_SOURCE_WORDS=true");
        FIRST_OF_SOURCE_WORDS=true;
      }
      if ("SECOND_OF_SOURCE_WORDS".equals(arg)) {
        System.err.println("SECOND_OF_SOURCE_WORDS=true");
        SECOND_OF_SOURCE_WORDS=true;
      }
      if ("USE_COARSE_DISTORATION_TYPES".equals(arg)) {
        System.err.println("USE_COARSE_DISTORATION_TYPES=true");
        USE_COARSE_DISTORATION_TYPES=true;
      }
      if ("TESTING".equals(arg)) {
        System.err.println("TESTING=true");
        TESTING=true;
      }

      if(arg.startsWith("wordtag=")) {
        String[] toks = arg.split("=");
        wordTagFilename = toks[1];
        initWordTagFile(wordTagFilename);
        USE_POS_TAGS = true;
        System.err.printf("USE_POS_TAGS=true (%s)\n", wordTagFilename);

      }
    }
  }

  void initWordTagFile(String wordTagFilename) {
    try {
      wordTagReader = new LineNumberReader(new FileReader(wordTagFilename));
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("loading "+wordTagFilename+" generated an exception");
    }
  }


  
  @Override
  public FeatureValue<String> featurize(Featurizable<TK,String> f) {
    //return new FeatureValue<String>(FEATURE_NAME, -1.0*f.linearDistortion);
    return null;
  }
  
  @Override
  public List<FeatureValue<String>> listFeaturize(Featurizable<TK,String> f) {
    List<FeatureValue<String>> features = new ArrayList<FeatureValue<String>>();

    /* if currentWordTag exists, 
       it means it's read in "reset" from the "wordTagFilename" file.
       Let's check if the number of words is the same as in the foreignSentence */
    if(currentWordTag != null) {
      if (currentWordTag.size() != f.foreignSentence.size()) {
        System.err.println("f.foreignSentence="+f.foreignSentence);
        throw new RuntimeException("line "+lineCount+" of "+wordTagFilename+
                                   " has different #words(="+currentWordTag.size()
                                   +" from what's in the f.foreignSentence(size="
                                   +f.foreignSentence.size()+")");
      }
    }
    
    if (DETAILED_DEBUG) {
      System.err.println("in WordLevelDiscrimDistortionFeaturizer.");
      System.err.println("  foreignPhrase="+f.foreignPhrase);
      System.err.println("  foreignPhrase.size="+f.foreignPhrase.size());
      int foreignAdjancentNextPhrasePosition=foreignAdjancentNextPhrasePosition(f);
      System.err.println("  foreignAdjancentNextPhrasePosition="
                         +foreignAdjancentNextPhrasePosition);
      System.err.println("  foreignPosition="+f.foreignPosition);
      int prevForeignWordPosition = f.foreignPosition-1;
      System.err.println("  lastPositionOfAdjacentPreviousPhrase="+prevForeignWordPosition);
      System.err.println("  priorPhraseTranslated="+priorPhraseTranslated(f));
      System.err.println("  nextPhraseTranslated="+nextPhraseTranslated(f));
      System.err.println("  translatedPhrase="+f.translatedPhrase);
      System.err.println("  partialTranslation="+f.partialTranslation);
      System.err.println("  foreignSentence="+f.foreignSentence);
      System.err.println("  hyp="+f.hyp);
      System.err.println("  hyp.parent()="+f.hyp.parent());
    }

    features.addAll(extractInternalFeatures(f));
    
    if (priorPhraseTranslated(f)) features.addAll(extractConnectionFeaturesWithPreviousPhrase(f));
    if (nextPhraseTranslated(f))  features.addAll(extractConnectionFeaturesWithNextPhrase(f));
    
    return features;
  }
  
  @Override
  public void initialize(List<ConcreteTranslationOption<TK>> options,
                         Sequence<TK> foreign) {		
    try {
      //System.err.println("Starting to load IBM Model1");
      //model1 = IBMModel1.load("/u/nlp/data/gale/scr/DancMTexperiments/feature_experiments/model1/zh_en.model.actual.t1");
      model1 = IBMModel1.load("/scr/nlp/data/gale2/acl08dd/resources/model1/zh_en.model.actual.t1");
      //System.err.println("finish loading IBM Model1");
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("IBMModel1 generated an exception");
    }
  }

  private List<FeatureValue<String>> extractInternalFeatures(Featurizable<TK,String> f) {
    List<FeatureValue<String>> features = new ArrayList<FeatureValue<String>>();


    // instead, take the startPos and EndPos of current foreign translation?
    int start = f.foreignPosition;
    int end = f.foreignPosition+f.foreignPhrase.size()-1;

    for (int curr = start; curr < end; curr++) {
      int next = curr+1;
      if (DETAILED_DEBUG) System.err.printf("word pair %d %d\n", curr, next);

      features.addAll(extractDistortionFeatureForPairOfSourceWords(curr, next, f, currentWordTag, model1));
    }

    return features;
  }

  /**
   * Return the index of the <code>englishPhrase</code> which has the highest Model 1 score with <code>chWord</code>
   * @return the index of the word in <code>englishPhrase</code> which has the highest Model1 score with <code>chWord</code>
   */
  protected static <TK> int getAlignedEnglishWordIndex(TK chWord, Sequence<TK> englishPhrase, IBMModel1 model1) {
    double max = Double.MIN_VALUE;
    int max_i = -1;
    if (DETAILED_DEBUG) System.err.println("chWord="+chWord);
    for (int i = 0; i < englishPhrase.size(); i++) {
      TK eWord = englishPhrase.get(i);
      double score = model1.score((IString)chWord, (IString)eWord);
      if (max < score) {
        max_i = i;
        if (DETAILED_DEBUG) System.err.println("max_i="+max_i);
        max = score;
        if (DETAILED_DEBUG) System.err.println("max="+max);
      }
    }
    if (DETAILED_DEBUG) System.err.println("enWord="+englishPhrase.get(max_i));
    return max_i;
  }


  private static <TK> List<FeatureValue<String>> extractDistortionFeatureForPairOfSourceWords
  (int cidx1, int cidx2, Featurizable<TK,String> f, List<WordTag> currentWordTag, IBMModel1 model1)
  {
    return extractDistortionFeatureForPairOfSourceWords(cidx1,cidx2,f,currentWordTag,model1,"");
  }
  

  /**
   * extract distortion features for a pair of source words.
   * depends on some global boolean variables like PAIR_OF_SOURCE_WORDS, 
   * FIRST_OF_SOURCE_WORDS or SECOND_OF_SOURCE_WORDS, 
   * features of different granualarity are included
   **/
  protected static <TK> List<FeatureValue<String>> extractDistortionFeatureForPairOfSourceWords
  (int cidx1, int cidx2, Featurizable<TK,String> f, 
   List<WordTag> currentWordTag, IBMModel1 model1, String featPrefix) {

    List<FeatureValue<String>> features = new ArrayList<FeatureValue<String>>();

    if (!wordTranslated(f, cidx1) || !wordTranslated(f, cidx2)) return features; // empty features


    int[] ephrase1_range = f.f2tAlignmentIndex[cidx1];
    int[] ephrase2_range = f.f2tAlignmentIndex[cidx2];

    // sanity check
    if (ephrase1_range.length!=2 || ephrase2_range.length!=2) {
      throw new RuntimeException("fword "+cidx1+" and "+cidx2+" should both already been translated, why's the range not int[2]?");
    }
    
    int relative_cidx1_AlignedEnglishIndex
      = getAlignedEnglishWordIndex(
        f.foreignSentence.get(cidx1),
        f.partialTranslation.subsequence(ephrase1_range[0], ephrase1_range[1]), model1);

    int relative_cidx2_AlignedEnglishIndex
      = getAlignedEnglishWordIndex(
        f.foreignSentence.get(cidx2),
        f.partialTranslation.subsequence(ephrase2_range[0], ephrase2_range[1]), model1);
    
    int cidx1_AlignedEnglishIndex = ephrase1_range[0] + relative_cidx1_AlignedEnglishIndex;
    int cidx2_AlignedEnglishIndex = ephrase2_range[0] + relative_cidx2_AlignedEnglishIndex;
    
    if (DETAILED_DEBUG) 
      System.err.printf("word pair %s %s (%d %d) --> %s %s (%d %d)\n",
                        f.foreignSentence.get(cidx1),
                        f.foreignSentence.get(cidx2),
                        cidx1,
                        cidx2,
                        f.partialTranslation.get(cidx1_AlignedEnglishIndex),
                        f.partialTranslation.get(cidx2_AlignedEnglishIndex),
                        cidx1_AlignedEnglishIndex,
                        cidx2_AlignedEnglishIndex);

    int distance = cidx2_AlignedEnglishIndex-cidx1_AlignedEnglishIndex;

    List<String> distortionTypes = getDistortionTypes(distance);

    for(String distortionType : distortionTypes) {
      if (PAIR_OF_SOURCE_WORDS) {
        StringBuilder sb = new StringBuilder();
        sb.append(featPrefix)
          .append(f.foreignSentence.get(cidx1))
          .append("-")
          .append(f.foreignSentence.get(cidx2)).append("-").append(distortionType);
        if (DETAILED_DEBUG) System.err.println("adding feature: "+sb.toString());
        features.add(new FeatureValue<String>(sb.toString(), 1.0));

        if (USE_POS_TAGS) {
          sb = new StringBuilder();
          sb.append(featPrefix)
            .append("POS|")
            .append(currentWordTag.get(cidx1).tag())
            .append("-")
            .append(currentWordTag.get(cidx2).tag()).append("-").append(distortionType);
          if (DETAILED_DEBUG) System.err.println("adding feature: "+sb.toString());
          features.add(new FeatureValue<String>(sb.toString(), 1.0));
        }
      }
      
      if (FIRST_OF_SOURCE_WORDS) {
        StringBuilder sb = new StringBuilder();
        sb.append(featPrefix)
          .append("SW1:")
          .append(f.foreignSentence.get(cidx1))
          .append("-").append(distortionType);
        if (DETAILED_DEBUG) System.err.println("adding feature: "+sb.toString());
        features.add(new FeatureValue<String>(sb.toString(), 1.0));

        if (USE_POS_TAGS) {
          sb = new StringBuilder();
          sb.append(featPrefix)
            .append("POS|")
            .append("SW1:")
            .append(currentWordTag.get(cidx1).tag())
            .append("-").append(distortionType);
          if (DETAILED_DEBUG) System.err.println("adding feature: "+sb.toString());
          features.add(new FeatureValue<String>(sb.toString(), 1.0));
        }
      }
      
      if (SECOND_OF_SOURCE_WORDS) {
        StringBuilder sb = new StringBuilder();
        sb.append(featPrefix)
          .append("SW2:")
          .append(f.foreignSentence.get(cidx2))
          .append("-").append(distortionType);
        if (DETAILED_DEBUG) System.err.println("adding feature: "+sb.toString());
        features.add(new FeatureValue<String>(sb.toString(), 1.0));

        if (USE_POS_TAGS) {
          sb = new StringBuilder();
          sb.append(featPrefix)
            .append("POS|")
            .append("SW2:")
            .append(currentWordTag.get(cidx2).tag())
            .append("-").append(distortionType);
          if (DETAILED_DEBUG) System.err.println("adding feature: "+sb.toString());
          features.add(new FeatureValue<String>(sb.toString(), 1.0));
        }
      }
    }

    return features;
  }

  public static List<String> getDistortionTypes(int distance) {
    List<String> types = new ArrayList<String>();
    types.add(getDistortionType(distance));
    if (USE_COARSE_DISTORATION_TYPES) {
      types.add(getDistortionType_2(distance));
    }
    return types;
  }

  public static String getDistortionType_2(int distance) {
    String distortionType = null;

    if (distance >= 0) {
      distortionType="NOTSWAP";
    } else {
      distortionType="SWAP";
    }
    return distortionType;
  }


  public static String getDistortionType(int distance) {
    String distortionType = null;
    if (distance > 1) {
      // sameOrientation:discontinuous
      // abbr: >1
      distortionType=">1";
    } else if (distance==1) {
      // sameOrientation:monotone
      // abbr: +1
      distortionType="=1";
    } else if (distance==0) {
      distortionType="=0";
    } else if (distance==-1) {
      distortionType="=-1";
    } else if (distance<-1) {
      distortionType="<-1";
    } else {
      throw new RuntimeException("some uncaught case in defining distortionType");
    }
    return distortionType;
  }


  public static String getDistortionType_9(int distance) {
    String distortionType = null;
    switch (distance) {
    case 3:
      distortionType="=3";
      break;
    case 2:
      distortionType="=2";
      break;
    case 1:
      distortionType="=1";
      break;
    case 0:
      distortionType="=0";
      break;
    case -1:
      distortionType="=-1";
      break;
    case -2:
      distortionType="=-2";
      break;
    case -3:
      distortionType="=-3";
      break;
    default:
      if (distance < -3) distortionType="<-3";
      else if (distance > 3) distortionType=">3";
      else 
        throw new RuntimeException("some uncaught case in defining distortionType");
    }

    return distortionType;
  }

  public static String getDistortionTypeAll(int distance) {
    StringBuilder sb = new StringBuilder("=");
    sb.append(distance);
    return sb.toString();
  }

  private List<FeatureValue<String>> extractConnectionFeaturesWithPreviousPhrase(Featurizable<TK,String> f) {
    // guranteed to be not negative, because we checked "priorPhraseTranslated"
    int lastWordPositionOfPreviousPhrase = f.foreignPosition-1;
    int curr = f.foreignPosition;

    return extractDistortionFeatureForPairOfSourceWords(lastWordPositionOfPreviousPhrase, curr, f, currentWordTag, model1);
  }

  private List<FeatureValue<String>> extractConnectionFeaturesWithNextPhrase(
    Featurizable<TK,String> f) {
    int curr = f.foreignPosition+f.foreignPhrase.size()-1;
    int firstWordPositionOfNextPhrase = f.foreignPosition+f.foreignPhrase.size();

    return extractDistortionFeatureForPairOfSourceWords(curr, firstWordPositionOfNextPhrase, f, currentWordTag, model1);
  }


  /**
   * @return the next word right after the currently translated foreign phrase. 
   * It is the start position of the linearly adjancent next phrase
   **/
  private int foreignAdjancentNextPhrasePosition(Featurizable<TK,String> f) {
    return f.foreignPosition+f.foreignPhrase.size();
  }


  /**
   * @param wordPostiion the position of the source word we want to check if it has been translated or not
   **/
  protected static <TK> boolean wordTranslated(
    Featurizable<TK,String> f, int wordPosition) {
    if (wordPosition < 0) return false;

    return f.hyp.foreignCoverage.get(wordPosition);
  }

  /**
   * @return wheather the linearly adjacent previous foreign phrase has been translated or not
   **/
  private boolean priorPhraseTranslated(Featurizable<TK,String> f) {
    int lastWordPositionOfPreviousPhrase = f.foreignPosition-1;
    return wordTranslated(f, lastWordPositionOfPreviousPhrase);
  }

  /**
   * @return wheather the linearly adjacent next foreign phrase has been translated or not
   **/
  private boolean nextPhraseTranslated(Featurizable<TK,String> f) {
    int firstWordPositionOfNextPhrase = foreignAdjancentNextPhrasePosition(f);
    return wordTranslated(f, firstWordPositionOfNextPhrase);
  }

  boolean doReset = true;
  boolean firstIter = true;
  
  @Override
  public void reset() {
    if (firstIter)
      System.err.println("Reset called. Do reset="+doReset);
    else {
      System.err.println("Reset called. Do reset=true");
    }

    // We skip reset only if it's in the first iteration of training (!TESTING)
    if (firstIter && !doReset && !TESTING) { 
      doReset = !doReset;
      return;
    }
    lineCount++;

    String line = null;
    try {
      if (USE_POS_TAGS == true) { // wordTagFilename != null) {
        line = wordTagReader.readLine();
        if (line == null) {
          System.err.printf("WordLevelDiscrimDistortionFeaturizer: reset lineCount from %d to 1\n",
                            lineCount);
          System.err.printf("WordLevelDiscrimDistortionFeaturizer: reread from wordtag file: %s\n", 
                            wordTagFilename);
          System.err.printf("WordLevelDiscrimDistortionFeaturizer: set firstIter to false\n");
          wordTagReader.close();
          try {
            wordTagReader = new LineNumberReader(new FileReader(wordTagFilename));
          } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("loading "+wordTagFilename+" generated an exception");
          }
          lineCount = 1;
          line = wordTagReader.readLine();
          firstIter = false;
        }
        
        int readerLineNum = wordTagReader.getLineNumber();
        if (readerLineNum != lineCount) {
          throw new RuntimeException("WordLevelDiscrimDistortionFeaturizer: the internal lineCount("
                                     +lineCount
                                     +") should be the same as wordTagReader.getLineNumber() ("
                                     +readerLineNum+")");
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("loading "+wordTagFilename+" generated an exception");
    }

    System.err.printf("WordLevelDiscrimDistortionFeaturizer: line %d\n", lineCount);
    if (line!=null) {
      currentWordTag = parseLineIntoWordTags(line);
      if (DETAILED_DEBUG) {
        System.err.printf("WordLevelDiscrimDistortionFeaturizer: %s\n", line);
        System.err.printf("WordLevelDiscrimDistortionFeaturizer: ");
        for(WordTag wt : currentWordTag) {
          System.err.print(wt+" ");
        }
        System.err.println();
      }
    }
    if (firstIter) doReset = !doReset;
  }

  private List<WordTag> parseLineIntoWordTags(String line) {
    String[] toks = line.split("\t");
    List<WordTag> wordTags = new ArrayList<WordTag>();

    for(String tok : toks) {
      String[] wordtag = tok.split(" ");
      if (wordtag.length != 2) {
        throw new RuntimeException("format error! line=["+line+"] when loading file "+wordTagFilename);
      }
      WordTag wordTag = new WordTag(wordtag[0],wordtag[1]);
      wordTags.add(wordTag);
    }
    return wordTags;
  }
}
