package edu.stanford.nlp.mt.decoder.efeat;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Featurizables;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;
import edu.stanford.nlp.util.Index;

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
public class VPRotationalBoundaryFeaturizer implements
    IncrementalFeaturizer<IString, String> {
  static final String FEATURE_PREFIX = "VPRotB:";

  static final boolean DEBUG = false;

  List<Map<Integer, String>> rotBs = null;

  public VPRotationalBoundaryFeaturizer(String... args) {
    String filename = args[0];
    if (DEBUG)
      System.err.printf("Debug Mode\n");
    LineNumberReader reader = null;
    try {
      reader = new LineNumberReader(new FileReader(filename));
      rotBs = new ArrayList<Map<Integer, String>>();
      String line = null;
      while ((line = reader.readLine()) != null) {
        System.err.println("---------------------------");
        System.err.printf("calling parseLine for rotBs[%d]\n", rotBs.size());
        Map<Integer, String> m = parseLine(line);
        rotBs.add(m);
      }
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("loading " + filename
          + " generated an exception");
    }

  }

  // static Pattern phrasePat =
  // Pattern.compile("(\\d+),(\\d+),(\\d+),(\\d+),([^,]+),([^,]+),([^,]+)");
  static Pattern phrasePat = Pattern
      .compile("(\\d+),(\\d+),(\\d+),(\\d+),([^,]+),(VP),(VP)");

  Map<Integer, String> parseLine(String line) {
    Map<Integer, String> map = new HashMap<Integer, String>();
    System.err.println("debug: " + line);
    String[] phrases = line.split(";");
    for (String phrase : phrases) {
      String[] toks = phrase.split(",");

      if (toks.length != 7) {
        System.err.println("dropping " + phrase);
        continue;
      }

      Matcher matcher = phrasePat.matcher(phrase);

      if (!matcher.find()) {
        System.err.println("dropping " + phrase);
        // throw new RuntimeException("pattern does not match: "+phrase);
        continue;
      } else {
        System.err.println("processing " + phrase);
        try {
          int p1idx2 = Integer.parseInt(matcher.group(2));
          int p2idx1 = Integer.parseInt(matcher.group(3));
          String phCat = matcher.group(5);
          if (!"PP".equals(phCat) && !"LCP".equals(phCat)) {
            System.err.println("Only looking at PP and LCP now: dropping "
                + phrase);
            continue;
          }
          String p1 = matcher.group(5);
          String p2 = matcher.group(6);
          String p0 = matcher.group(7);
          if (p1idx2 + 1 != p2idx1) {
            throw new RuntimeException("phrase 1 and 2 should be adjacent");
          }
          StringBuilder sb = new StringBuilder();
          sb.append(p0).append("(").append(p1).append(":").append(p2)
              .append(")");
          if (map.get(p1idx2) != null) {
            throw new RuntimeException(p1idx2 + " has more than one boundary");
          }
          map.put(p1idx2, sb.toString());
          System.err.printf("pichuan: add to map: %d - %s\n", p1idx2,
              sb.toString());
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
   * T B T A \ / / \ F A F B
   */
  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    // System.err.printf("Getting sentId=%d from rotB\n", sentId);
    Map<Integer, String> map = rotBs.get(sentId);

    int foreignSwapPos = Featurizables.locationOfSwappedPhrase(f);

    String type = null;
    String label = null;

    if (foreignSwapPos != -1) {
      // we have a swap
      label = map.get(foreignSwapPos - 1);
      type = "fill:";
      if (label != null) { // the position does contain a VP rotation Boundary
        if (DEBUG) {
          System.err
              .println("----------------------------------------------------");
          System.err.println("Type: 'fill'");
          System.err.println("Label: " + label);
          System.err.printf("foreignSwapPos: %d\n", foreignSwapPos);
          System.err
              .printf(
                  "(interpretation: there's a boundary between f position %d and %d\n",
                  foreignSwapPos - 1, foreignSwapPos);
        }
        // Foreign
        // 00000000............1111
        // ^ ^
        // foreignPosition foreignSwapPos
        // \ /
        // \ /
        // x
        // / \
        // / \
        // FprevTrans translationPosition
        // English
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
          // System.err.printf("foreignSentence: %s\n", f.foreignSentence);
          for (int i = 0; i < f.foreignSentence.size(); i++) {
            IString w = f.foreignSentence.get(i);
            System.err.printf("%s (%d) ", w, i);
          }
          System.err.println();
          System.err.printf("foreignPhrase: %s\n", f.foreignPhrase);
          System.err.printf("translatedPhrase: %s\n", f.translatedPhrase);
          System.err.printf("partialTranslation: %s\n", f.partialTranslation);
          System.err.printf("c: %s\n", f.hyp.foreignCoverage);
          System.err.printf("foreignPosition: %d\n", f.foreignPosition);
          System.err.printf("foreignSwapPos: %d\n", foreignSwapPos);
          System.err.printf("translationPosition: %d\n", f.translationPosition);
          System.err.printf("type=%s; label: %s\n", type, label);
        }
      }
    } else {
      int foreignSentEnd = f.foreignPosition + f.foreignPhrase.size() - 1;
      label = map.get(foreignSentEnd);
      type = "end:";
      if (label != null) { // the position does contain a VP rotation Boundary
        if (DEBUG) {
          System.err
              .println("----------------------------------------------------");
          System.err.println("Type: 'end'");
          System.err.println("Label: " + label);
          System.err.printf("foreignSentEnd: %d\n", foreignSentEnd);
          System.err
              .printf(
                  "(interpretation: i'm translating up to f position %d. There's a boundary between %d and %d\n",
                  foreignSentEnd, foreignSentEnd, foreignSentEnd + 1);
        }
        // Foreign
        // 000000000000000000000
        // ^ ^
        // foreignPosition foreignSentEnd
        // ^
        // label
        if (DEBUG) {
          System.err.println("  Foreign");
          System.err.println("  000000000000000000000");
          System.err.println("  ^                   ^");
          System.err.println("  foreignPosition    foreignSentEnd="
              + foreignSentEnd);
          System.err.println("                      ^");
          System.err.println("                      label");
          // System.err.printf("foreignSentence: %s\n", f.foreignSentence);
          for (int i = 0; i < f.foreignSentence.size(); i++) {
            IString w = f.foreignSentence.get(i);
            System.err.printf("%s (%d) ", w, i);
          }
          System.err.println();
          System.err.printf("foreignPhrase: %s\n", f.foreignPhrase);
          System.err.printf("translatedPhrase: %s\n", f.translatedPhrase);
          System.err.printf("partialTranslation: %s\n", f.partialTranslation);
          System.err.printf("c: %s\n", f.hyp.foreignCoverage);
          System.err.printf("foreignPosition: %d\n", f.foreignPosition);
          System.err.printf("translationPosition: %d\n", f.translationPosition);
          System.err.printf("type=%s; label: %s\n", type, label);
        }
      }
    }

    if (label == null)
      return null;

    String featureString = FEATURE_PREFIX + type + label;

    if (DEBUG) {
      System.err.printf("Feature string: %s\n", featureString);
    }

    return new FeatureValue<String>(featureString, 1.0);
  }

  @Override
  public void initialize(List<ConcreteTranslationOption<IString,String>> options,
      Sequence<IString> foreign, Index<String> featureIndex) {
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(
      Featurizable<IString, String> f) {

    return null;
  }

  int sentId = -1;

  @Override
  public void reset() {
    sentId++;
    System.err.println("sentId=" + sentId);
  }

}
