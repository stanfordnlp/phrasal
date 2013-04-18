package edu.stanford.nlp.mt.decoder.efeat;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.mt.base.ConcreteTranslationOption;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;
import edu.stanford.nlp.util.Index;

/**
 * Feature template
 * 
 * @author huihsin
 * @author danielcer
 * 
 */
public class VerbLinkageFeaturizerTwo implements
    IncrementalFeaturizer<IString, String> {
  public static final String PREFIX = "VRBLNKTWO:";
  public Sequence<IString> previousForeign; // previous foreign sentence
  public Sequence<IString> currentForeign = new SimpleSequence<IString>(
      new IString("<BEGIN-CORPUS>"));
  public Set<String> previousForeignWords = new HashSet<String>(); // set of
                                                                   // foreign
                                                                   // words
  public static final Set<String> fExistNouns = new HashSet<String>();
  public static final Set<String> fDTs = new HashSet<String>();
  public static final Set<String> fPNs = new HashSet<String>();
  public static final Set<String> fPUs = new HashSet<String>();
  public static final Set<String> fNouns = new HashSet<String>();
  public static final Set<String> fSingles = new HashSet<String>();
  public static final Set<String> tNNs = new HashSet<String>();
  public static final Set<String> tNNSs = new HashSet<String>();

  static String PATH_TO_LIST_OF_fWords = "/juice/u3/htseng/mt/ctb.txt";
  static String PATH_TO_LIST_OF_tWords = "/juice/u3/htseng/mt/wsj.txt";

  static {

    try {
      BufferedReader freader = new BufferedReader(new FileReader(
          PATH_TO_LIST_OF_fWords));
      for (String line; (line = freader.readLine()) != null;) {
        if (line.equals(""))
          continue;
        String[] fields = line.split("	");
        if (fields.length == 2) {
          String tag = fields[1];

          if (tag.contains("NN")) {
            fNouns.add(fields[0]);
          }
          if (tag.contains("DT")) {
            fDTs.add(fields[0]);
          }
          if (tag.contains("PN")) {
            fPNs.add(fields[0]);
          }
          if (tag.contains("PUA") || tag.contains("CC") || tag.contains("CS")
              || tag.contains("PUB")) {
            fPUs.add(fields[0]);
          }
          if (fields[0].equals("一") || fields[0].equals("单")
              || fields[0].equals("仅") || fields[0].equals("只")) {
            fSingles.add(fields[0]);
          }
        }
      }
      freader.close();

      BufferedReader treader = new BufferedReader(new FileReader(
          PATH_TO_LIST_OF_tWords));
      for (String line; (line = treader.readLine()) != null;) {
        if (line.equals(""))
          continue;
        String[] fields = line.split("	");
        if (fields.length == 2) {
          String tag = fields[1];

          if (tag.contains("NN")) {
            tNNs.add(fields[0]);
          }
          if (tag.contains("NNS")) {
            tNNSs.add(fields[0]);
          }
        }

      }
      treader.close();

    } catch (IOException e) {
      throw new RuntimeException(String.format("Error reading: %s\n",
          PATH_TO_LIST_OF_fWords));
    }
  }

  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {
    return null;
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteTranslationOption<IString,String>> options, Sequence<IString> foreign, Index<String> featureIndex) {
    previousForeign = currentForeign;
    currentForeign = foreign;
    previousForeignWords.clear();
    fExistNouns.clear();
    for (IString fWord : previousForeign) {
      previousForeignWords.add(fWord.toString());
      if (fNouns.contains(fWord.toString()))
        fExistNouns.add(fWord.toString());
    }
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(
      Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = new LinkedList<FeatureValue<String>>();

    int fPhrSz = f.sourcePhrase.size();
    String fCon = "";
    String fWord = "";
    for (int fPos = 0; fPos < fPhrSz; fPos++) {
      fWord = f.sourcePhrase.get(fPos).toString();
      if (fNouns.contains(fWord)) {
        int foreignPhrasePos = f.sourcePosition;
        int fWordPos = fPos + foreignPhrasePos;
        String fPU = "f";
        String fPN = "f";
        String fDT = "f";
        String fPUi = "f";
        String fsingle = "f";
        String fEX = "f";

        if (fExistNouns.contains(fWord)) {
          fEX = "t";
        }

        for (int sourcePos = fWordPos - 1; sourcePos > 0; sourcePos--) {
          if (fSingles.contains(f.sourceSentence.get(sourcePos).toString())) {
            fsingle = "t";
          }
          if (fDTs.contains(f.sourceSentence.get(sourcePos).toString())) {
            fDT = "t";
          }
          if (fPNs.contains(f.sourceSentence.get(sourcePos).toString())) {
            fPN = "t";
          }
          if (fPUs.contains(f.sourceSentence.get(sourcePos).toString())) {
            fPUi = "t";
            break;
          }
        }

        if ((fWordPos + 1) < f.sourceSentence.size()
            && fPUs.contains(f.sourceSentence.get(fWordPos + 1))) {
          fPU = "t";
        }

        fCon = fEX + "_" + fDT + "_" + fPN + "_" + fPUi + "_" + fPU + "_"
            + fsingle;
        break;
      }
    }

    // List<IString> transVerbs = new LinkedList<IString>();
    int tPhrSz = f.targetPhrase.size();
    String tSg = "f";
    String tPl = "f";

    for (int tPos = 0; tPos < tPhrSz; tPos++) {
      IString tWord = f.targetPhrase.get(tPos);

      if (tNNs.contains(tWord.toString())) {
        tSg = "t";
      }
      if (tNNSs.contains(tWord.toString()) || tWord.toString().endsWith("s")) {
        tPl = "t";
      }

    }

    String tCnd = "";
    tCnd = tSg + "_" + tPl;

    features.add(new FeatureValue<String>(PREFIX + fCon + ":" + tCnd, 1.0));
    features.add(new FeatureValue<String>(PREFIX + fWord + ":" + tCnd, 1.0));
    features.add(new FeatureValue<String>(PREFIX + fCon + ":" + fWord + ":"
        + tCnd, 1.0));

    return features;
  }

  @Override
  public void reset() {
  }

}
