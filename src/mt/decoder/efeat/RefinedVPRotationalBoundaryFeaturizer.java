package mt.decoder.efeat;

import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.Featurizables;
import mt.base.Sequence;
import mt.decoder.feat.IncrementalFeaturizer;
import mt.decoder.util.Hypothesis;

import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.IString;

import java.util.*;
import java.io.LineNumberReader;
import java.io.FileReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 
 * @author Pi-Chuan Chang
 *
 */
public class RefinedVPRotationalBoundaryFeaturizer implements IncrementalFeaturizer<IString, String>{
  static final String FEATURE_PREFIX = "RefinedVPRotB:";
	
  static final boolean DEBUG = false;
	

  List<Map<Integer,Pair<Integer,String>>> rotBs = null;

  @SuppressWarnings("unchecked")
  public RefinedVPRotationalBoundaryFeaturizer(String... args) {
    String filename = args[0];
    if (DEBUG) System.err.printf("Debug Mode\n");
    LineNumberReader reader = null;
    try {
      reader = new LineNumberReader(new FileReader(filename));
      rotBs = new ArrayList<Map<Integer,Pair<Integer,String>>>();
      String line = null;
      while((line = reader.readLine()) != null) {
        System.err.println("---------------------------");
        System.err.printf("calling parseLine for rotBs[%d]\n", rotBs.size());
        Map<Integer,Pair<Integer,String>> m = parseLine(line);
        rotBs.add(m);
      }
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("loading "+filename+" generated an exception");
    }
    
  }

  //static Pattern phrasePat = Pattern.compile("(\\d+),(\\d+),(\\d+),(\\d+),([^,]+),([^,]+),([^,]+)");
  static Pattern phrasePat = Pattern.compile("(\\d+),(\\d+),(\\d+),(\\d+),([^,]+),(VP),(VP)");
  Map<Integer,Pair<Integer,String>> parseLine(String line) {
    Map<Integer,Pair<Integer,String>> map = new HashMap<Integer,Pair<Integer,String>>();
    System.err.println("debug: "+line);
    String[] phrases = line.split(";");
    for (String phrase : phrases) {
      String[] toks = phrase.split(",");

      if (toks.length != 7) {
        System.err.println("dropping "+phrase);
        continue;
      }

      Matcher matcher = phrasePat.matcher(phrase);
      
      if (!matcher.find()) {
        System.err.println("dropping "+phrase);
        //throw new RuntimeException("pattern does not match: "+phrase);
        continue;
      } else {
        System.err.println("processing "+phrase);
        try {
          int p1idx1 = Integer.parseInt(matcher.group(1));
          int p1idx2 = Integer.parseInt(matcher.group(2));
          int p2idx1 = Integer.parseInt(matcher.group(3));
          String phCat = matcher.group(5);
          if (!"PP".equals(phCat) && !"LCP".equals(phCat)) {
            System.err.println("Only looking at PP and LCP now: dropping "+phrase);
            continue;
          }
          String p1  = matcher.group(5);
          String p2  = matcher.group(6);
          String p0  = matcher.group(7);
          if (p1idx2+1 != p2idx1) {
            throw new RuntimeException("phrase 1 and 2 should be adjacent");
          }
          StringBuilder sb = new StringBuilder();
          sb.append(p0).append("(").append(p1).append(":").append(p2).append(")");
          if (map.get(p1idx2)!=null) {
            throw new RuntimeException(p1idx2+" has more than one boundary");
          }
          Pair<Integer,String> p = new Pair<Integer,String>();
          p.setFirst(p1idx1);
          p.setSecond(sb.toString());
          map.put(p1idx2, p);
          //System.err.printf("pichuan: add to map: (%d,%d) - %s\n",p1idx1,p1idx2,sb.toString());
          System.err.printf("pichuan: add to map: (%d,%d) - %s\n",p.first(),p1idx2,p.second());
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
    return map;
  }
	
  static final IString START_SEQ = new IString("<s>");
	
  /**
   * 
   * T B   T A
   *    \ /
   *    / \
   * F A   F B
   */
  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) { 
    //System.err.printf("Getting sentId=%d from rotB\n", sentId);
    Map<Integer,Pair<Integer,String>> map = rotBs.get(sentId);

    int foreignSwapPos = Featurizables.locationOfSwappedPhrase(f);
    int firstUntranslatedFword = f.foreignPosition;

    String type = null;
    String label = null;
    int startp1 = -1;

    boolean toAdd = true;

    if (foreignSwapPos != -1) {  		
      // we have a swap
      Pair<Integer,String> p = map.get(foreignSwapPos-1);
      if (p!=null) {
        label = p.second();
        startp1 = p.first();
      }
      type = "fill:";
      if (label != null) { // the position does contain a VP rotation Boundary
        // check if things in P1 have been set already!
        boolean firstInP1 = (firstUntranslatedFword>=startp1);

        if (firstInP1) { // if this phrase starts at or after the boundary of p1
          // check if p1 has always been covered at all
          Hypothesis<IString,String> prevhyp = f.hyp.preceedingHyp;
          if(prevhyp != null) {
            for(int checkI = startp1; checkI <= foreignSwapPos-1; checkI++) {
              if (prevhyp.foreignCoverage.get(checkI)) {
                firstInP1 = false;
              }
            }
          }
        }
        if (!firstInP1) {toAdd = false;}
        if (DEBUG) {
          System.err.println("----------------------------------------------------");
          System.err.println("Type: 'fill'");
          System.err.println("Label: "+label);
          System.err.printf("foreignSwapPos: %d\n", foreignSwapPos);
          System.err.printf("(interpretation: there's a boundary between f position %d and %d\n"
                            , foreignSwapPos-1, foreignSwapPos);
          System.err.printf("                 first untranslated word is at:%d)\n"
                            , firstUntranslatedFword);
          System.err.printf("                %s - (%d-%d)\n", label, startp1,foreignSwapPos-1);
          System.err.println("         (fill)firstInP1 = "+firstInP1);
        }
        //  Foreign
        //  00000000............1111
        //  ^                   ^
        //  foreignPosition    foreignSwapPos
        //                \   /
        //                 \ /
        //                  x
        //                 / \
        //                /   \
        //       FprevTrans   translationPosition
        //  English
        if (DEBUG) {
          System.err.println("  Foreign");
          System.err.println("  00000000............1111");
          System.err.println("  ^                   ^");
          System.err.println("  foreignPosition    foreignSwapPos");
          System.err.println("                \\   /");
          System.err.println("                 \\ /");
          System.err.println("                  x");
          System.err.println("                 / \\");
          System.err.println("                /   \\");
          System.err.println("       FprevTrans   translationPosition");
          System.err.println("  English");
          //System.err.printf("foreignSentence: %s\n", f.foreignSentence);
          for(int i = 0 ; i < f.foreignSentence.size() ; i++) {
            IString w = f.foreignSentence.get(i);
            System.err.printf("%s (%d) ",w, i);
          }
          System.err.println();
          System.err.printf("(fill) foreignPhrase: %s\n", f.foreignPhrase);
          System.err.printf("(fill) translatedPhrase: %s\n", f.translatedPhrase);
          System.err.printf("(fill) partialTranslation: %s\n", f.partialTranslation);
          System.err.printf("(fill) c: %s\n", f.hyp.foreignCoverage);
          System.err.printf("(fill) foreignPosition: %d\n", f.foreignPosition);
          System.err.printf("(fill) foreignSwapPos: %d\n", foreignSwapPos);
          System.err.printf("(fill) translationPosition: %d\n", f.translationPosition);
          System.err.printf("type=%s; label: %s\n", type, label);
        }
      }
    } else {
      int foreignSentEnd = f.foreignPosition+f.foreignPhrase.size()-1;
      Pair<Integer,String> p = map.get(foreignSentEnd);
      if (p!=null) {
        label = p.second();
        startp1 = p.first();
      }
      type = "end:";
      if (label != null) { // the position does contain a VP rotation Boundary
        if (DEBUG) {
          System.err.println("----------------------------------------------------");
          System.err.println("Type: 'end'");
          System.err.println("Label: "+label);
          System.err.printf("foreignSentEnd: %d\n", foreignSentEnd);
          System.err.printf("(interpretation: i'm translating up to f position %d. There's a boundary between %d and %d\n", foreignSentEnd, foreignSentEnd, foreignSentEnd+1);
          System.err.printf("                 first untranslated word is at:%d, cur phrase len=%d\n)"
                            , firstUntranslatedFword, f.foreignPhrase.size());
          System.err.printf("                %s - (%d-%d)\n", label, startp1,foreignSentEnd);

        }
      //  Foreign
      //  000000000000000000000
      //  ^                   ^
      //  foreignPosition    foreignSentEnd
      //                      ^
      //                      label
        if (DEBUG) {
          System.err.println("  Foreign");
          System.err.println("  000000000000000000000");
          System.err.println("  ^                   ^");
          System.err.println("  foreignPosition    foreignSentEnd="+foreignSentEnd);
          System.err.println("                      ^");
          System.err.println("                      label");
          //System.err.printf("foreignSentence: %s\n", f.foreignSentence);
          for(int i = 0 ; i < f.foreignSentence.size() ; i++) {
            IString w = f.foreignSentence.get(i);
            System.err.printf("%s (%d) ",w, i);
          }
          System.err.println();
          System.err.printf("(end) foreignPhrase: %s\n", f.foreignPhrase);
          System.err.printf("(end) translatedPhrase: %s\n", f.translatedPhrase);
          System.err.printf("(end) partialTranslation: %s\n", f.partialTranslation);
          System.err.printf("(end) c: %s\n", f.hyp.foreignCoverage);
          System.err.printf("(end) foreignPosition: %d\n", f.foreignPosition);
          System.err.printf("(end) translationPosition: %d\n", f.translationPosition);
          System.err.printf("type=%s; label: %s\n", type, label);
        }
      }
    }

    if (type == null || label == null) return null;

    String featureString = FEATURE_PREFIX+type+label;

    if (!toAdd) {
      if (DEBUG) System.err.printf("Drop Feature string: %s\n", featureString);
      return null;
    }


    if (DEBUG) {
      System.err.printf("Feature string: %s\n", featureString);
    }

    return new FeatureValue<String>(featureString, 1.0);
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<IString>> options,
                         Sequence<IString> foreign) { }

	
	
  @Override
  public List<FeatureValue<String>> listFeaturize(
    Featurizable<IString, String> f) {

    return null; 
  }

  int sentId = -1;
  @Override
  public void reset() {	
    sentId++;
    System.err.println("sentId="+sentId);
  }

}
