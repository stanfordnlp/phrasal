package edu.stanford.nlp.mt.decoder.efeat;

import edu.stanford.nlp.mt.base.*;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.decoder.feat.IncrementalFeaturizer;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.util.Index;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * This featurizer attempts to detect Arabic verb-initial sentences. The aim is
 * to penalize verb-initial sentences that are translated monotonically, rather
 * than with a word order swap. The code strongly depends on using the IBM
 * (ATBp3v3) POS tag set.
 * 
 * @author Michel Galley
 */
public class ArabicInitialVerbFeaturizer implements
    IncrementalFeaturizer<IString, String> {

  static final String FEATURE_NAME = "EnglishVSOPenalty";
  private static final double ENGLISH_VSO_PENALTY = -1.0;

  private static final boolean verbose = false;
  static boolean strict = false;

  // Tags currently handled:

  private static final String[] TAGS_ANYWHERE = new String[] // tokens that can
                                                             // appear anywhere
  { "FUT", "JUS", "INTERJ", "DET", "ADV", "PART", "NEG+PART", "CONNEC+PART",
      "RESTRIC+PART", "FOCUS+PART", "INTERROG+PART", "RC+PART", "VOC+PART",
      "FUT+PART", "EMPHATIC+PART" };
  private static final String[] TAGS_BEFORE = new String[] // words before the
                                                           // verb in an VSO
  { "CONJ", "PUNC" };
  private static final String[] TAGS_VERB = new String[] // VSO verbs
  { "PV", "IV", "NOUN.VN", "ADJ.VN", "DET+ADJ.VN", "DET+NOUN.VN" };
  private static final String[] TAGS_BETWEEN = new String[] // between verb and
                                                            // subject
  { "IVSUFF+DO", "PVSUFF+DO" };
  private static final String[] TAGS_SBJ_START = new String[] // anything that
                                                              // may start an
                                                              // NP-SBJ or
                                                              // SBAR-SBJ:
  { // common NP starts (> 100 in ATB):
  "NOUN", "DET+NOUN", "NOUN+PROP", "PRON", "DET+NOUN+PROP", "DEM+PRON", "NUM",
      // unusual NP starts:
      "ABBREV", "INTERROG+PRON", "DET+NUM", "EXCLAM+PRON", "DET+ABBREV",
      "POSS+PRON",
      // Often at the start of an SBAR-SBJ:
      "SUB+CONJ" };

  // Tags currently not handled:

  private static final String[] TAGS_VERB_FAIL = new String[] { "PSEUDO+VERB",
      "VERB+PART" };
  private static final String[] TAGS_SBJ_FAIL = new String[] {};
  private static final String[] TAGS_FAIL = new String[] { // This usually
                                                           // starts an SBAR or
                                                           // SBARQ:
      "REL+ADV", "REL+PRON", "INTERROG+ADV",
      // PREP usually starts a PP, whereas the +PREP typically do not
      // Since it is fine to leave the PP after the verb, currently don't handle
      // PPs
      // (though it would be nice to handle the V PP NP-SBJ -> NP-SBJ V PP)
      "PREP",
      // Almost never starts (NP) subjects:
      "DET+ADJ", "ADJ",
      // Words unrecognized:
      "TYPO", "DIALECT", "FOREIGN", "GRAMMAR+PROBLEM", "DET+FOREIGN", "LATIN" };

  private static final Set<String> beforeTags = new HashSet<String>(),
      verbTags = new HashSet<String>(), betweenTags = new HashSet<String>(),
      sbjTags = new HashSet<String>(), failTags = new HashSet<String>();

  static {
    beforeTags.addAll(Arrays.asList(TAGS_BEFORE));
    beforeTags.addAll(Arrays.asList(TAGS_ANYWHERE));

    verbTags.addAll(Arrays.asList(TAGS_VERB)); // V

    betweenTags.addAll(Arrays.asList(TAGS_BETWEEN));
    betweenTags.addAll(Arrays.asList(TAGS_ANYWHERE));

    sbjTags.addAll(Arrays.asList(TAGS_SBJ_START)); // S

    failTags.addAll(Arrays.asList(TAGS_FAIL));
    failTags.addAll(Arrays.asList(TAGS_VERB_FAIL));
    failTags.addAll(Arrays.asList(TAGS_SBJ_FAIL));
  }

  /** The Arabic tagger (must use IBM tags!). */
  private static MaxentTagger tagger;
  private static String DEFAULT_TAGGER_FILE = "/scr/nlp/data/gale2/IBM_ATB/ibm-stanfordized/utf8/arabic.tagger";

  private boolean vso;
  private int vsoVerbIdx;
  private int vsoSubjectIdx;

  TaggedWord[] tags;

  public ArabicInitialVerbFeaturizer() {
    this(DEFAULT_TAGGER_FILE);
  }

  public ArabicInitialVerbFeaturizer(String taggerFile) {
    try {
      System.err.printf("Loading tagger from serialized file %s ...\n",
          taggerFile);
      // mg2008: note this doesn't load the serialized config file!!
      // cdm 2009: now it does! honest.
      tagger = new MaxentTagger(taggerFile);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public FeatureValue<String> featurize(Featurizable<IString, String> f) {

    String name = getClass().toString();

    if (!vso)
      return null;

    int f1 = f.sourcePosition;
    int f2 = f1 + f.sourcePhrase.size();

    Featurizable<IString, String> pf = f.prior;

    if (f1 <= vsoVerbIdx && vsoVerbIdx < f2) {
      if (verbose) {
        System.err
            .println(name + ": tagged sentence: " + Arrays.toString(tags));
        System.err.println(name + ": partial translation: "
            + f.targetPrefix.toString());
        System.err.println(name + ": current target phrase: "
            + f.targetPhrase.toString());
        System.err.printf(
            "%s: now translating range [%d,%d) containing verb (%d) of VSO.\n",
            name, f1, f2, vsoVerbIdx);
      }
      if (f2 <= vsoSubjectIdx) {
        // Currently translating the verb of a VSO construction, but not the
        // subject:
        while (pf != null) {
          int pf1 = pf.sourcePosition;
          int pf2 = pf1 + f.sourcePhrase.size();
          if (verbose) {
            System.err.printf(
                "%s: checking previous phrase of range [%d,%d)....\n", name,
                pf1, pf2);
          }
          if (f2 <= pf1 && !strict) {
            // [f1 ... verb ... f2] ... [pf1 ...
            if (verbose) {
              System.err
                  .printf(
                      "%s: previous phrase [%d,%d) swaps with current phrase, good! (subject at %d)\n",
                      name, pf1, pf2, vsoSubjectIdx);
            }
            return null;
          } else if (pf1 <= vsoSubjectIdx && vsoSubjectIdx < pf2) {
            // [f1 ... verb ... f2] ... [pf1 ... subject ... pf2]
            if (verbose) {
              System.err
                  .printf(
                      "%s: range [%d,%d) was used to translate subject (%d) of VSO, great!\n",
                      name, pf1, pf2, vsoSubjectIdx);
            }
            return null;
          }
          pf = pf.prior;
        }
        if (verbose) {
          System.err.println(name + ": remains VSO.");
        }
        // VSO apparently remained in VSO order in English, bad:
        return new FeatureValue<String>(FEATURE_NAME, ENGLISH_VSO_PENALTY);
      } else {
        // Currently translating both verb and subject: don't do anything, since
        // phrase-based MT can/should provide the correct order.
        if (verbose) {
          System.err
              .printf(
                  "%s: range [%d,%d) alreading contains subject (%d) of VSO, skipping.\n",
                  name, f1, f2, vsoSubjectIdx);
        }
      }
    }
    return null;
  }

  @Override
  public void initialize(int sourceInputId,
      List<ConcreteTranslationOption<IString,String>> options, Sequence<IString> foreign, Index<String> featureIndex) {

    String[] words = IStrings.toStringArray(Sequences.toIntArray(foreign));
    ArrayList<TaggedWord> sentence = tagger.tagSentence(Sentence
        .toUntaggedList(Arrays.asList(words)));
    tags = sentence.toArray(new TaggedWord[sentence.size()]);

    String taggedSent = Arrays.toString(tags);

    vso = false;
    vsoVerbIdx = -1;
    vsoSubjectIdx = -1;
    for (int pos = 0; pos < sentence.size(); ++pos) {
      String curTag = sentence.get(pos).tag();
      System.err.println("current tag: " + curTag);

      // Fail if can't handle tag:
      if (failTags.contains(curTag))
        break;

      // Step 1: Skip words such as conjunctions and punctuations, then check if
      // first word is a verb:
      if (vsoVerbIdx < 0) {
        if (beforeTags.contains(curTag)) {
          // continue;
        } else if (verbTags.contains(curTag)) {
          vsoVerbIdx = pos;
        } else {
          break; // first word is not a verb.
        }
      } else

      // Step 2: Identify first word possibly part of an NP-SBJ:
      if (vsoVerbIdx >= 0 && vsoSubjectIdx < 0) {
        if (betweenTags.contains(curTag)) {
          // continue;
        } else if (sbjTags.contains(curTag)) {
          vsoSubjectIdx = pos;
          vso = true;
          break;
        } else {
          break; // some word is unrecognized before the next NP
        }
      } else {
        throw new RuntimeException("Shouldn't be here: pos=" + pos);
      }
    }
    if (vso) {
      assert (vsoVerbIdx >= 0);
      assert (vsoSubjectIdx >= 0);
      System.err.printf("VSO=yes V=%d S=%d : %s\n", vsoVerbIdx, vsoSubjectIdx,
          taggedSent);
    } else {
      vsoVerbIdx = -1;
      vsoSubjectIdx = -1;
      System.err.println("VSO=no : " + taggedSent);
    }
  }

  @Override
  public List<FeatureValue<String>> listFeaturize(
      Featurizable<IString, String> f) {
    return null;
  }

  public void reset() {
  }

  public static void main(String[] args) {
    if (args.length != 1) {
      System.err
          .println("Usage: java edu.stanford.nlp.mt.decoder.efeat.ArabicInitialVerbFeaturizer (ar-file)");
      System.exit(1);
    }
    IncrementalFeaturizer<IString, String> f = new ArabicInitialVerbFeaturizer();
    for (String line : IOUtils.slurpFileNoExceptions(args[0]).split("\\n")) {
      f.initialize(
          0,
          null, new SimpleSequence<IString>(true, IStrings.toIStringArray(line
                  .split("\\s+"))), null);
    }
  }
}

// ////////////////
/*
 * Old categorization: VERB: (verbs that can appear in VSO) 13581 PV 12388 IV
 * 
 * VERB-SKIP: (other types of verbs, which are either unlikely to appear in a
 * VSO, or that do not generally translate as a verb -- e.g. PSEUDO-VERB) 1181
 * PSEUDO+VERB 1097 NOUN.VN 723 ADJ.VN 670 VERB+PART 661 DET+ADJ.VN 40
 * DET+NOUN.VN
 * 
 * NP: 61799 NOUN 42949 DET+NOUN 20151 NOUN+PROP 7373 POSS+PRON 6091 PRON 5581
 * NUM 4564 REL+PRON 3902 DET+NOUN+PROP 2919 DEM+PRON 820 ABBREV 81
 * INTERROG+PRON 5 DET+NUM 1 EXCLAM+PRON 1 DET+ABBREV
 * 
 * NP-SKIP: (stuff rarely seen in NP-SBJ) 1124 IVSUFF+DO 1154 PVSUFF+DO
 * 
 * SKIP: (words that are skipped in our analysis) 39815 PREP 22784 CONJ 33612
 * PUNC 18453 DET+ADJ 8977 ADJ 6530 SUB+CONJ 2162 NEG+PART 868 FUT 831 ADV 338
 * REL+ADV 331 CONNEC+PART 226 PART 188 DET 183 RESTRIC+PART 165 FOCUS+PART 143
 * INTERROG+PART 137 INTERROG+ADV 135 RC+PART 134 TYPO 59 VOC+PART 32 JUS 32
 * DIALECT 36 FOREIGN 27 INTERJ 23 FUT+PART 11 EMPHATIC+PART 4 GRAMMAR+PROBLEM 3
 * DET+FOREIGN 1 LATIN
 */
