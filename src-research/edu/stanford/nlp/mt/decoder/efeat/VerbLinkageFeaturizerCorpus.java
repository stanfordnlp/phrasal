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

/**
 * Feature template
 * 
 * @author huihsin
 * @author danielcer
 * 
 */
public class VerbLinkageFeaturizerCorpus implements
    IncrementalFeaturizer<IString, String> {
  public static final String PREFIX = "Cor:";
  public Sequence<IString> previousForeign; // previous foreign sentence
  public Sequence<IString> currentForeign = new SimpleSequence<IString>(
      new IString("<BEGIN-CORPUS>"));
  public Set<String> previousForeignWords = new HashSet<String>(); // set of
                                                                   // foreign
                                                                   // words
  public static final Set<String> fFuns = new HashSet<String>();
  public static final Set<String> fVVs = new HashSet<String>();
  public static final Set<String> fVAs = new HashSet<String>();
  public static final Set<String> fNouns = new HashSet<String>();
  public static final Set<String> fAdvs = new HashSet<String>();
  public static final Set<String> fCrosss = new HashSet<String>();

  public static final Set<String> tAnds = new HashSet<String>();
  public static final Set<String> tCommas = new HashSet<String>();
  public static final Set<String> tTos = new HashSet<String>();
  public static final Set<String> tVerbs = new HashSet<String>();
  public static final Set<String> tRels = new HashSet<String>();
  public static final Set<String> tNouns = new HashSet<String>();
  public static final HashMap<String, Double> corpusRule;

  static String PATH_TO_LIST_OF_fWords = "/juice/scr1/htseng/rule/clist.new";
  static String PATH_TO_LIST_OF_tWords = "/juice/scr1/htseng/rule/list.wsd";
  static String PATH_TO_LIST_OF_rule = "/juice/scr1/htseng/rule/rule.txt";
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
          if (tag.contains("PU") || tag.equals("P") || tag.equals("VC")
              || tag.equals("VE") || tag.equals("LC") || tag.equals("CC")
              || tag.equals("CS") || tag.equals("BA") || tag.equals("LB")
              || tag.equals("SB") || tag.equals("IJ") || tag.equals("ON")
              || tag.equals("AS") || tag.equals("DE") || tag.equals("SP")
              || tag.equals("MSP")) {
            fFuns.add(fields[0]);
          }
          if (tag.contains("VV")) {
            fVVs.add(fields[0]);
          }
          if (tag.contains("VA")) {
            fVAs.add(fields[0]);
          }
          if (tag.contains("NN") || tag.contains("NR")) {
            fNouns.add(fields[0]);
          }
          if (tag.contains("AD")) {
            fAdvs.add(fields[0]);
          }
          if (tag.contains("PU")) {
            fCrosss.add(fields[0]);
          }
        }
      }
      freader.close();

      BufferedReader treader = new BufferedReader(new FileReader(
          PATH_TO_LIST_OF_tWords));
      for (String line; (line = treader.readLine()) != null;) {
        if (line.equals(""))
          continue;
        String[] fields = line.split("  ");
        if (fields.length == 2) {
          if (fields[0].contains("and") || fields[0].contains("and")) {
            tAnds.add(fields[0]);
          }
          if (fields[1].contains(",")) {
            tCommas.add(fields[0]);
          }
          if (fields[1].startsWith("T")) {
            tTos.add(fields[0]);
          }
          if (fields[1].startsWith("V")) {
            tVerbs.add(fields[0]);
          }
          if (fields[1].contains("WDT")) {
            tRels.add(fields[0]);
          }
          if ((fields[1].contains("PRP") && fields[1].startsWith("$"))
              || fields[1].startsWith("N")) {
            tNouns.add(fields[0]);
          }
        }
      }
      treader.close();

      BufferedReader rreader = new BufferedReader(new FileReader(
          PATH_TO_LIST_OF_rule));
      corpusRule = new HashMap<String, Double>();
      for (String line; (line = rreader.readLine()) != null;) {
        if (line.equals(""))
          continue;
        String[] fields = line.split("	");
        if (fields.length == 2) {
          corpusRule.put(fields[0], Double.parseDouble(fields[1]));
        }
      }
      rreader.close();

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
  public void initialize(List<ConcreteTranslationOption<IString>> options,
      Sequence<IString> foreign) {
    // previousForeign = currentForeign;
    // currentForeign = foreign;
    // previousForeignWords.clear();
    // fExistNouns.clear();
    // for (IString fWord : previousForeign) {
    // previousForeignWords.add(fWord.toString());
    // if (fNouns.contains(fWord.toString())) fExistNouns.add(fWord.toString());
    // }
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(
      Featurizable<IString, String> f) {
    List<FeatureValue<String>> features = new LinkedList<FeatureValue<String>>();
    double sumNumf = 0;
    double sumNumt = 0;

    int fPhrSz = f.foreignPhrase.size();
    String fConLexP = "";
    String fConLexS = "";
    String fWord = "";
    String fConPCross = "";
    String fConSCross = "";
    String fConPVerb = "";
    String fConPAdj = "";
    String fConPAdv = "";
    String fConPNoun = "";
    String fConSVerb = "";
    String fConSAdj = "";
    String fConSNoun = "";

    for (int fPos = 0; fPos < fPhrSz; fPos++) {
      fWord = f.foreignPhrase.get(fPos).toString();
      if (fVVs.contains(fWord)) {
        int foreignPhrasePos = f.foreignPosition;
        int fWordPos = fPos + foreignPhrasePos;
        for (int sourcePos = fWordPos - 1; sourcePos > 0; sourcePos--) {
          if (fCrosss.contains(f.foreignSentence.get(sourcePos).toString())) {
            fConPCross = "non-first-clause";
            break;
          }
          if (fVVs.contains(f.foreignSentence.get(sourcePos).toString())) {
            fConPVerb = "Verb";
          }
          if (fVAs.contains(f.foreignSentence.get(sourcePos).toString())) {
            fConPAdj = "Adj";
          }
          if (fNouns.contains(f.foreignSentence.get(sourcePos).toString())) {
            fConPNoun = "Noun";
          }
          if (fAdvs.contains(f.foreignSentence.get(sourcePos).toString())) {
            fConPAdv = "Adv";
          }
          if (fFuns.contains(f.foreignSentence.get(sourcePos).toString())) {
            fConLexP = fConLexP + f.foreignSentence.get(sourcePos).toString();
          }
        }

        int lastPos = f.foreignSentence.size();
        for (int sourcePos = fWordPos + 1; sourcePos < lastPos; sourcePos++) {
          if (fCrosss.contains(f.foreignSentence.get(sourcePos).toString())) {
            fConSCross = "non-end-clause";
            break;
          }
          if (fVVs.contains(f.foreignSentence.get(sourcePos).toString())) {
            fConSVerb = "Verb2";
          }
          if (fVAs.contains(f.foreignSentence.get(sourcePos).toString())) {
            fConSAdj = "Adj2";
          }
          if (fNouns.contains(f.foreignSentence.get(sourcePos).toString())) {
            fConSNoun = "Noun2";
          }
          if (fFuns.contains(f.foreignSentence.get(sourcePos).toString())) {
            fConLexS = fConLexS + f.foreignSentence.get(sourcePos).toString();
          }
        }
      }
    }
    String fCon = "";
    fCon = fConPVerb + fConPAdj + fConPNoun + fConPAdv + fConSVerb + fConSAdj
        + fConSNoun + fConLexP + "P" + fConLexS + "S" + fConPCross + fConSCross;

    int tPhrSz = f.translatedPhrase.size();
    String tCon = "";
    for (int tPos = 0; tPos < tPhrSz; tPos++) {
      IString tWord = f.translatedPhrase.get(tPos);
      if (tVerbs.contains(tWord.toString())) {
        if (tPos > 0) {
          IString tWordP = f.translatedPhrase.get(tPos - 1);
          if (tNouns.contains(tWordP.toString())) {
            tCon = "Enoun";
          } else if (tAnds.contains(tWordP.toString())) {
            tCon = "Eand";
          } else if (tCommas.contains(tWordP.toString())) {
            tCon = "Ecomma";
          } else if (tTos.contains(tWordP.toString())) {
            tCon = "Eprep";
          } else if (tRels.contains(tWordP.toString())) {
            tCon = "Erel";
          } else {
            tCon = "Eother";
          }
        } else {
          tCon = "BEG";
        }
        break;
      } else {
        tCon = "Eother";
      }
    }

    if (fCon.equals("PS") && tCon.equals("")) {
    } else {
      if (fCon.equals("PS")) {
      } else {
        double fCndS = corpusRule.get(fCon + tCon);
        double tCndS = corpusRule.get(tCon + fCon);
        sumNumf += Math.log(fCndS);
        sumNumt += Math.log(tCndS);
      }
    }
    features.add(new FeatureValue<String>(PREFIX + "f", sumNumf));
    features.add(new FeatureValue<String>(PREFIX + "t", sumNumt));
    return features;
  }

  @Override
  public void reset() {
  }

}
