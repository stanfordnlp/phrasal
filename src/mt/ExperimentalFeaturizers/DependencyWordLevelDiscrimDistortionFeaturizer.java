package mt.ExperimentalFeaturizers;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
import java.io.LineNumberReader;
import java.io.FileReader;

/*
import mt.IString;
*/
import mt.Sequence;
import mt.ConcreteTranslationOption;
import mt.Featurizable;
import mt.FeatureValue;
import mt.IncrementalFeaturizer;
import mt.IBMModel1;
import mt.IString;

import edu.stanford.nlp.ling.WordTag;

/**
 * 
 * @author Pi-Chuan Chang
 *
 * @param <TK>
 */
public class DependencyWordLevelDiscrimDistortionFeaturizer<TK> implements IncrementalFeaturizer<TK, String> {

  private static boolean PAIR_OF_SOURCE_WORDS = true; // always true
  private static boolean FIRST_OF_SOURCE_WORDS = true;
  private static boolean SECOND_OF_SOURCE_WORDS = true;
  private static boolean USE_COARSE_DISTORATION_TYPES = true;
  private static boolean TESTING = false;

  private int lineCount = 0;

  private IBMModel1 model1 = null;

  private static final boolean DETAILED_DEBUG = false;

  private String parseFilename = null;
  private LineNumberReader parseReader = null;
  private Integer[] currentDep2Head = null;
  private String[]  currentDepName = null;
  private Map<Integer,List<Integer>> currentHead2Deps = null;

  public DependencyWordLevelDiscrimDistortionFeaturizer(String... args) {
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

      if(arg.startsWith("parse=")) {
        String[] toks = arg.split("=");
        parseFilename = toks[1];
        initParseFile(parseFilename);
      }
    }
  }

  void initParseFile(String parseFilename) {
    try {
      parseReader = new LineNumberReader(new FileReader(parseFilename));
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("loading "+parseFilename+" generated an exception");
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

    /* if currentDep2Head exists,
       it means it's read in "reset" from the "parseFilename" file.
       Let's check if the number of words is the same as in the foreignSentence */
    if(currentDep2Head != null) {
      if (currentDep2Head.length != f.foreignSentence.size()) {
        System.err.println("f.foreignSentence="+f.foreignSentence);
        throw new RuntimeException("line "+lineCount+" of "+parseFilename
                                   +". has different #words(="+currentDep2Head.length
                                   +" from what's in the f.foreignSentence(size="
                                   +f.foreignSentence.size()+")");
      }
    }
    
    
    int fStartIdx = f.foreignPosition;
    int fEndIdx   = f.foreignPosition + f.foreignPhrase.size() - 1;
    
    if (DETAILED_DEBUG) {
      System.err.println("in DependencyWordLevelDiscrimDistortionFeaturizer.");
      System.err.printf ("  foreignPhrase = %s [%d-%d]\n",f.foreignPhrase, fStartIdx, fEndIdx);
      System.err.println("  partialTranslation="+f.partialTranslation);
      System.err.println("  foreignSentence="+f.foreignSentence);
    }
    
    for(int fidx = fStartIdx; fidx <= fEndIdx; fidx++) {

      // do features that has a dependent in the range of this currently translated phrase
      int dep = fidx;
      int head;
      if (currentDep2Head[dep]==null) head = -1; else head = currentDep2Head[dep];
      
      // if the head is already translated, let's add the feature!!
      if (!WordLevelDiscrimDistortionFeaturizer.wordTranslated(f, dep)) {
        throw new RuntimeException("how can word "+dep+" hasn't been translated yet?");
      }
    
      if (DETAILED_DEBUG) System.err.println("DEPS: dep in the range!");
      features.addAll(WordLevelDiscrimDistortionFeaturizer.extractDistortionFeatureForPairOfSourceWords(head, dep, f, null, model1, "D|"));
      features.addAll(extractDistortionFeatureForDependencyType(head,dep,f,model1));

      // then, 
      // do features that has a head in the range of this currently translated phrase
      head = fidx;
      if (DETAILED_DEBUG) System.err.println("DEPS: head in the range!");
      List<Integer> deps = currentHead2Deps.get(head);
      if (deps != null) {
        for (int oneDep : deps) {
          if (oneDep >= fStartIdx && oneDep <= fEndIdx) {
            // if both side of this link are within this phrase, the feature of this dependency
            // link are actually already added! Don't duplicate!!!
            continue;
          } else {
            features.addAll(WordLevelDiscrimDistortionFeaturizer.extractDistortionFeatureForPairOfSourceWords(head, oneDep, f, null, model1, "D|"));
            features.addAll(extractDistortionFeatureForDependencyType(head,oneDep,f,model1));
          }
        }
      }
    }
    
    return features;
  }
  

  /**
   * there's lots of duplication of this method and
   * "extractDistortionFeatureForPairOfSourceWords" in 
   * WordLevelDiscrimDistortionFeaturizer.java
   **/
  private List<FeatureValue<String>> extractDistortionFeatureForDependencyType
  (int cidx1, int cidx2, Featurizable<TK,String> f, IBMModel1 model1) {
    List<FeatureValue<String>> features = new ArrayList<FeatureValue<String>>();

    if (!WordLevelDiscrimDistortionFeaturizer.wordTranslated(f, cidx1) 
        || !WordLevelDiscrimDistortionFeaturizer.wordTranslated(f, cidx2)) return features; // empty features


    int[] ephrase1_range = f.f2tAlignmentIndex[cidx1];
    int[] ephrase2_range = f.f2tAlignmentIndex[cidx2];

    // sanity check
    if (ephrase1_range.length!=2 || ephrase2_range.length!=2) {
      throw new RuntimeException("fword "+cidx1+" and "+cidx2+" should both already been translated, why's the range not int[2]?");
    }

    int relative_cidx1_AlignedEnglishIndex
      = WordLevelDiscrimDistortionFeaturizer.getAlignedEnglishWordIndex(
        f.foreignSentence.get(cidx1),
        f.partialTranslation.subsequence(ephrase1_range[0], ephrase1_range[1]), model1);

    int relative_cidx2_AlignedEnglishIndex
      = WordLevelDiscrimDistortionFeaturizer.getAlignedEnglishWordIndex(
        f.foreignSentence.get(cidx2),
        f.partialTranslation.subsequence(ephrase2_range[0], ephrase2_range[1]), model1);

    int cidx1_AlignedEnglishIndex = ephrase1_range[0] + relative_cidx1_AlignedEnglishIndex;
    int cidx2_AlignedEnglishIndex = ephrase2_range[0] + relative_cidx2_AlignedEnglishIndex;

    int distance = cidx2_AlignedEnglishIndex-cidx1_AlignedEnglishIndex;

    List<String> distortionTypes = WordLevelDiscrimDistortionFeaturizer.getDistortionTypes(distance);

    for(String distortionType : distortionTypes) {
      StringBuilder sb = new StringBuilder();
      sb.append("DT|") // Dependency Type
        .append(currentDepName[cidx2])
        .append("-")
        .append(distortionType);
      if (DETAILED_DEBUG) System.err.println("adding feature: "+sb.toString());
      features.add(new FeatureValue<String>(sb.toString(), 1.0));
    }
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
      line = parseReader.readLine();
      if (line == null) {
        System.err.printf("DependencyWordLevelDiscrimDistortionFeaturizer: reset lineCount from %d to 1\n",
                          lineCount);
        System.err.printf("DependencyWordLevelDiscrimDistortionFeaturizer: reread from wordtag file: %s\n", 
                          parseFilename);
        System.err.printf("DependencyWordLevelDiscrimDistortionFeaturizer: set firstIter to false\n");
        parseReader.close();
        try {
          parseReader = new LineNumberReader(new FileReader(parseFilename));
        } catch (Exception e) {
          e.printStackTrace();
          throw new RuntimeException("loading "+parseFilename+" generated an exception");
        }
        lineCount = 1;
        line = parseReader.readLine();
        firstIter = false;
      }
      
      int readerLineNum = parseReader.getLineNumber();
      if (readerLineNum != lineCount) {
        throw new RuntimeException("DependencyWordLevelDiscrimDistortionFeaturizer: the internal lineCount("
                                   +lineCount
                                   +") should be the same as parseReader.getLineNumber() ("
                                   +readerLineNum+")");
      }
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("loading "+parseFilename+" generated an exception");
    }
    
    System.err.printf("DependencyWordLevelDiscrimDistortionFeaturizer: line %d\n", lineCount);
    if (line!=null) {
      // TODO: check if the largest idx in parse is the same as the length!
      currentDep2Head = parseLineIntoDep2Head(line);
      currentHead2Deps = parseLineIntoHead2Deps(line);
      currentDepName = parseLineIntoDepName(line);
      if (DETAILED_DEBUG) {
        System.err.printf("DependencyWordLevelDiscrimDistortionFeaturizer: %s\n", line);
        System.err.printf("DependencyWordLevelDiscrimDistortionFeaturizer: ");
        for(Integer d : currentDep2Head) {
          System.err.print(d+" ");
        }
        for(Map.Entry<Integer,List<Integer>> e : currentHead2Deps.entrySet()) {
          System.err.printf("head %d:",e.getKey());
          for(int d : e.getValue()) {
            System.err.printf("%d ",d);
          }
          System.err.println();
        }
      }
    }
    if (firstIter) doReset = !doReset;
  }


  private Map<Integer,List<Integer>> parseLineIntoHead2Deps(String line) {
    String[] toks = line.split("\t");

    Map<Integer,List<Integer>> head2deps = new HashMap<Integer,List<Integer>>();
    for(String tok : toks) {
      String[] depline = tok.split(" ");
      if (depline.length != 5) {
        throw new RuntimeException("format error! line=["+line+"] when loading file "+parseFilename);
      }
      try {
        int govIdx = Integer.parseInt(depline[2])-1;
        int depIdx = Integer.parseInt(depline[4])-1;
        if (depIdx > toks.length+1 || govIdx > toks.length+1) {
          throw new RuntimeException("toks.length="+toks.length+",depIdx="+depIdx+",govIdx="+govIdx);
        }
        List<Integer> hdeps = head2deps.get(govIdx);
        if (hdeps == null) { hdeps = new ArrayList<Integer>(); }
        hdeps.add(depIdx);
        head2deps.put(govIdx, hdeps);
      } catch (Exception e) {
        e.printStackTrace();
        throw new RuntimeException("should be integers");
      }
    }
    
    return head2deps;
  }
  
  private Integer[] parseLineIntoDep2Head(String line) {
    String[] toks = line.split("\t");
    Integer[] deps = new Integer[toks.length+1];

    for(String tok : toks) {
      String[] depline = tok.split(" ");
      if (depline.length != 5) {
        throw new RuntimeException("format error! line=["+line+"] when loading file "+parseFilename);
      }
      try {
        int govIdx = Integer.parseInt(depline[2])-1;
        int depIdx = Integer.parseInt(depline[4])-1;
        if (depIdx > toks.length+1 || govIdx > toks.length+1) {
          throw new RuntimeException("toks.length="+toks.length+",depIdx="+depIdx+",govIdx="+govIdx);
        }
        if (deps[depIdx] != null) {
          throw new RuntimeException("dep "+depIdx+" has 2 heads?");
        }
        deps[depIdx] = new Integer(govIdx);
      } catch (Exception e) {
        e.printStackTrace();
        throw new RuntimeException("should be integers");
      }
    }

    return deps;
  }

  private String[] parseLineIntoDepName(String line) {
    String[] toks = line.split("\t");
    String[] depname = new String[toks.length+1];

    for(String tok : toks) {
      String[] depline = tok.split(" ");
      if (depline.length != 5) {
        throw new RuntimeException("format error! line=["+line+"] when loading file "+parseFilename);
      }
      try {
        int govIdx = Integer.parseInt(depline[2])-1;
        int depIdx = Integer.parseInt(depline[4])-1;
        if (depIdx > toks.length+1 || govIdx > toks.length+1) {
          throw new RuntimeException("toks.length="+toks.length+",depIdx="+depIdx+",govIdx="+govIdx);
        }
        if (depname[depIdx] != null) {
          throw new RuntimeException("dep "+depIdx+" has 2 heads?");
        }
        depname[depIdx] = new String(depline[0]);
      } catch (Exception e) {
        e.printStackTrace();
        throw new RuntimeException("should be integers");
      }
    }

    return depname;
  }
}
