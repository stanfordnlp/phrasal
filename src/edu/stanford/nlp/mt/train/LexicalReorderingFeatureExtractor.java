package edu.stanford.nlp.mt.train;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Arrays;

import edu.stanford.nlp.mt.train.AlignmentGrid.RelativePos;

import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.PropertiesUtils;

/**
 * Extractor for lexicalized re-ordering probabilities of Moses.
 * 
 * @author Michel Galley
 */
public class LexicalReorderingFeatureExtractor extends AbstractFeatureExtractor {

  public static final String DEBUG_PROPERTY = "DebugLexicalReorderingFeatureExtractor";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
      DEBUG_PROPERTY, "false"));

  public static final String UNNORM_PROPERTY = "Unnormalize";
  public static final boolean UNNORM = Boolean.parseBoolean(System.getProperty(
      UNNORM_PROPERTY, "false"));

  public static final String LAPLACE_PROPERTY = "LaplaceSmoothing";
  private static final float LAPLACE_SMOOTHING = Float.parseFloat(System
      .getProperty(LAPLACE_PROPERTY, "0.5f"));

  // Default specification of the re-ordering model.
  public static final String DEFAULT_MODEL_TYPE = "msd2-bidirectional-fe";
  
  List<int[]> forwardCounts = null, backwardCounts = null, jointCounts = null;
  int[] totalForwardCounts = null, totalBackwardCounts = null,
      totalJointCounts = null;

  enum DirectionTypes {
    forward, backward, bidirectional, joint
  }

  public static enum ReorderingTypes {
    monotone, swap, discont1, discont2, containment
  }

  enum PermType {
    x, y, nil
  }

  enum LanguageTypes {
    fe, f, e
  }

  int modelSize = 0;
  int numModels = 0;
  private final boolean[] enabledTypes = new boolean[ReorderingTypes.values().length];
  private final int[] typeToIdx = new int[ReorderingTypes.values().length];

  private DirectionTypes directionType;
  private LanguageTypes languageType;

  private boolean phrasalReordering = false;
  private boolean hasContainment = false;

  @Override
  public void init(Properties prop, Index<String> featureIndex,
      AlignmentTemplates alTemps) {

    super.init(prop, featureIndex, alTemps);
    boolean fail = false;

    // Categories enabled by default:
    enabledTypes[ReorderingTypes.monotone.ordinal()] = true;
    enabledTypes[ReorderingTypes.discont1.ordinal()] = true;

    // Type of reordering model:
    String type = prop.getProperty(PhraseExtract.LEX_REORDERING_TYPE_OPT,
        DEFAULT_MODEL_TYPE);
    System.err.println("Orientation type: " + type);
    String[] tokens = type.split("-");
    assert (2 <= tokens.length && tokens.length <= 3);

    // Type of extraction: word-phrase (Moses), phrase-phrase (Tillmann, etc),
    // or hierarchical:
    phrasalReordering = PropertiesUtils.getBool(prop,
        PhraseExtract.LEX_REORDERING_PHRASAL_OPT, false);
    boolean hierReordering = PropertiesUtils.getBool(prop,
        PhraseExtract.LEX_REORDERING_HIER_OPT, false);
    
    System.err.println("Phrasal orientation model: " + phrasalReordering);
    System.err.println("Hierarchical orientation model: " + hierReordering);
    
    if (hierReordering) {
      phrasalReordering = true;
    }
    if (phrasalReordering) {
      enabledTypes[ReorderingTypes.discont2.ordinal()] = PropertiesUtils.getBool(prop,
              PhraseExtract.LEX_REORDERING_2DISC_CLASS_OPT, false);
    }

    // Get categories:
    boolean msdType = "msd".equals(tokens[0]);
    boolean msd2Type = "msd2".equals(tokens[0]);
    boolean msd2cType = "msd2c".equals(tokens[0]);
    boolean mType = "monotonicity".equals(tokens[0]);

    if (msdType || msd2Type || msd2cType) {
      enabledTypes[ReorderingTypes.swap.ordinal()] = true;
      if (msd2Type || msd2cType) {
        enabledTypes[ReorderingTypes.discont2.ordinal()] = true;
        System.err.println("Left and right discontinuous: yes");
        if (msd2cType) {
          hasContainment = true;
          enabledTypes[ReorderingTypes.containment.ordinal()] = true;
          System.err.println("Containment orientation: yes");
        }
      }
    } else if (mType) {
      // No swap category.
    } else
      fail = true;
    modelSize = initTypeToIdx();

    // Determine whether model is forward, backward, both, or joint:
    switch (tokens.length) {
    case 2:
      directionType = DirectionTypes.forward;
      numModels = 1;
      break;
    case 3:
      switch (tokens[1]) {
        case "bidirectional":
          directionType = DirectionTypes.bidirectional;
          numModels = 2;
          break;
        case "joint":
          directionType = DirectionTypes.joint;
          numModels = 1;
          break;
        default:
          fail = true;
          break;
      }
      break;
    default:
      fail = true;
    }

    // Get language:
    String lang = tokens[tokens.length - 1];
    switch (lang) {
      case "fe":
        languageType = LanguageTypes.fe;
        break;
      case "f":
        languageType = LanguageTypes.f;
        break;
      case "e":
        languageType = LanguageTypes.e;
        break;
      default:
        fail = true;
        break;
    }
    if (languageType != LanguageTypes.fe)
      throw new UnsupportedOperationException(
          "LexicalReorderingFeatureExtractor: model currently not supported.");
    if (fail)
      throw new UnsupportedOperationException("Unknown model type: " + type);

    // Check whether the re-ordering model is a phrase-phrase ordering model
    // rather than
    // the phrase-word ordering model of moses:
    System.err.println("Orientation model direction: "
        + directionType.toString());

    // Store total counts for each reordering type:
    // Init count arrays:
    if (directionType == DirectionTypes.forward
        || directionType == DirectionTypes.bidirectional) {
      forwardCounts = new ArrayList<int[]>();
      totalForwardCounts = new int[modelSize];
    }
    if (directionType == DirectionTypes.backward
        || directionType == DirectionTypes.bidirectional) {
      backwardCounts = new ArrayList<int[]>();
      totalBackwardCounts = new int[modelSize];
    }
    if (directionType == DirectionTypes.joint) {
      modelSize *= modelSize;
      jointCounts = new ArrayList<int[]>();
      totalJointCounts = new int[modelSize];
    }
    if (Boolean.parseBoolean(prop.getProperty(PhraseExtract.WITH_GAPS_OPT))) {
      // withDTU = true;
      if (!phrasalReordering)
        System.err
            .println("Reordering model: discontinuous phrases without phrasal reordering.");
    }
  }

  @Override
  public boolean needAlGrid() {
    return phrasalReordering;
  }

  @Override
  public void featurizeSentence(SymmetricalWordAlignment sent, AlignmentGrid alGrid) {
  }

  private ReorderingTypes getReorderingType(AlignmentTemplateInstance alTemp,
      AlignmentGrid alGrid, boolean forward) {

    WordAlignment sent = alTemp.getWordAlignment();
    int f1 = alTemp.fStartPos() - 1, f2 = alTemp.fEndPos() + 1, e1 = alTemp
        .eStartPos() - 1, e2 = alTemp.eEndPos() + 1;

    boolean connectedMonotone, connectedSwap;
    if (forward) {
      connectedMonotone = isPhraseAligned(alGrid, e1, f1, RelativePos.NW);
      connectedSwap = isPhraseAligned(alGrid, e1, f2, RelativePos.NE);
    } else {
      connectedMonotone = isPhraseAligned(alGrid, e2, f2, RelativePos.SE);
      connectedSwap = isPhraseAligned(alGrid, e2, f1, RelativePos.SW);
    }

    // Detect local containment class:
    if (hasContainment) {
      if (forward) {
        if (isWordAligned(alGrid, e1, f1) && isWordAligned(alGrid, e1, f2))
          return ReorderingTypes.containment;
      } else {
        if (isWordAligned(alGrid, e2, f2) && isWordAligned(alGrid, e2, f1))
          return ReorderingTypes.containment;
      }
    }

    // Determine if Monotone or Swap:
    if (connectedMonotone && !connectedSwap)
      return ReorderingTypes.monotone;
    if (!connectedMonotone && connectedSwap)
      return ReorderingTypes.swap;

    // If distinction between discont1 and discont2 is impossible, return
    // discont1:
    if (!enabledTypes[ReorderingTypes.discont2.ordinal()] || !phrasalReordering)
      return ReorderingTypes.discont1;

    // If needed, distinguish between forward and backward discontinuous:
    if (forward) {
      if (e1 >= 0) {
        if (hasContainment)
          for (int fPos : sent.e2f(e1))
            if (f1 < fPos && fPos < f2)
              return ReorderingTypes.containment;
        for (int fPos = f2; fPos < sent.f().size(); ++fPos)
          if (alGrid.cellAt(fPos, e1).hasBottomLeft())
            return ReorderingTypes.discont2;
      }
    } else {
      if (e2 < sent.e().size()) {
        if (hasContainment)
          for (int fPos : sent.e2f(e2))
            if (f1 < fPos && fPos < f2)
              return ReorderingTypes.containment;
        for (int fPos = f1; fPos >= 0; --fPos) {
          if (alGrid.cellAt(fPos, e2).hasTopRight())
            return ReorderingTypes.discont2;
        }
      }
    }

    if (DEBUG)
      System.err.println("warning: falling back to default (3)");

    return ReorderingTypes.discont1;
  }

  @Override
  public void featurizePhrase(AlignmentTemplateInstance alTemp,
      AlignmentGrid alGrid) {
    if (getCurrentPass() + 1 != getRequiredPassNumber())
      return;

    ReorderingTypes type1 = getReorderingType(alTemp, alGrid, true);
    ReorderingTypes type2 = getReorderingType(alTemp, alGrid, false);

    if (directionType == DirectionTypes.forward
        || directionType == DirectionTypes.bidirectional) {
      // Analyze reordering (forward):
      if (DEBUG)
        debugOrientation("forward_global=" + phrasalReordering, alTemp, alGrid,
            type1);
      addCountToArray(forwardCounts, totalForwardCounts,
          typeToIdx[type1.ordinal()], alTemp);
    }
    if (directionType == DirectionTypes.backward
        || directionType == DirectionTypes.bidirectional) {
      // Analyze reordering (backward):
      if (DEBUG)
        debugOrientation("backward_global=" + phrasalReordering, alTemp,
            alGrid, type2);
      addCountToArray(backwardCounts, totalBackwardCounts,
          typeToIdx[type2.ordinal()], alTemp);
    }
    if (directionType == DirectionTypes.joint) {
      // Analyze reordering (bidirectional):
      int type = typeToIdx[type1.ordinal()] * modelSize
          + typeToIdx[type2.ordinal()];
      addCountToArray(jointCounts, totalJointCounts, type, alTemp);
    }
  }

  @Override
  public Object score(AlignmentTemplate alTemp) {
    int idx = -1;
    if (languageType == LanguageTypes.fe)
      idx = alTemp.getKey();
    else if (languageType == LanguageTypes.f)
      idx = alTemp.getFKey();
    else if (languageType == LanguageTypes.e)
      idx = alTemp.getEKey();
    assert (idx >= 0);
    float[] scores = new float[modelSize * numModels];
    if (directionType == DirectionTypes.joint) {
      fillProbDist(jointCounts.get(idx), scores, 0);
    } else {
      int offset = 0;
      if (directionType == DirectionTypes.forward
          || directionType == DirectionTypes.bidirectional) {
        fillProbDist(forwardCounts.get(idx), scores, offset);
        offset += modelSize;
      }
      if (directionType == DirectionTypes.backward
          || directionType == DirectionTypes.bidirectional) {
        fillProbDist(backwardCounts.get(idx), scores, offset);
        offset += modelSize;
      }
    }
    return scores;
  }

  private int initTypeToIdx() {
    int curOrd = -1, curIdx = -1;
    for (int i = 0; i < enabledTypes.length; ++i) {
      ++curOrd;
      if (!enabledTypes[i])
        continue;
      typeToIdx[curOrd] = ++curIdx;
      System.err.printf("Orientation enabled: %s, idx=%d\n",
          ReorderingTypes.values()[i], curIdx);
    }
    return curIdx + 1;
  }

  private static void debugOrientation(String id,
      AlignmentTemplateInstance alTemp, AlignmentGrid alGrid,
      ReorderingTypes type) {
    if (alTemp.f().size() > 20 || alTemp.e().size() > 20)
      return;
    assert (alGrid != null);
    System.err.printf("Model %s has orientation %s according to the grid:\n",
        id, type.toString());
    alGrid.setWordAlignment(alTemp.getWordAlignment());
    alGrid.printAlTempInGrid(null, alTemp, System.err);
  }

  // Determine if position (ei,fi) is aligned (at the phrase level).
  private static boolean isWordAligned(AlignmentGrid alGrid, int ei, int fi) {
    WordAlignment sent = alGrid.getWordAlignment();
    if (fi == -1 && ei == -1)
      return true;
    if (fi == -1 || ei == -1)
      return false;
    if (fi == sent.f().size() && ei == sent.e().size())
      return true;
    if (fi == sent.f().size() || ei == sent.e().size())
      return false;
    boolean e2f = sent.e2f(ei).contains(fi);
    boolean f2e = sent.f2e(fi).contains(ei);
    assert (f2e == e2f);
    return f2e;
  }

  // Determine if position (ei,fi) is aligned (at the phrase level).
  private boolean isPhraseAligned(AlignmentGrid alGrid, int ei, int fi,
      RelativePos pos) {
    WordAlignment sent = alGrid.getWordAlignment();
    assert (fi >= -1 && ei >= -1);
    assert (fi <= sent.f().size() && ei <= sent.e().size());
    if (fi == -1 && ei == -1)
      return true;
    if (fi == -1 || ei == -1)
      return false;
    if (fi == sent.f().size() && ei == sent.e().size())
      return true;
    if (fi == sent.f().size() || ei == sent.e().size())
      return false;
    if (phrasalReordering) {
      // Phrase-phrase reordering as in Tillman:
      AlGridCell<AlignmentTemplateInstance> cell = alGrid.cellAt(fi, ei);
      if (DEBUG) {
        System.err.printf("relative position: %s\n", pos.toString());
        System.err.printf("orientation at: f=%d e=%d\n", fi, ei);
        System.err.println("  hasTopLeft: " + cell.hasTopLeft());
        System.err.println("  hasTopRight: " + cell.hasTopRight());
        System.err.println("  hasBottomLeft: " + cell.hasBottomLeft());
        System.err.println("  hasBottomRight: " + cell.hasBottomRight());
      }
      return ((pos == RelativePos.NW && cell.hasBottomRight())
          || (pos == RelativePos.NE && cell.hasBottomLeft())
          || (pos == RelativePos.SW && cell.hasTopRight()) || (pos == RelativePos.SE && cell
          .hasTopLeft()));
    } else {
      // Word-phrase reordering as in Moses:
      return sent.f2e(fi).contains(ei);
    }
  }

  public void fillProbDist(int[] counts, float[] probs, int offset) {
    float norm = modelSize * LAPLACE_SMOOTHING;
    for (int i = 0; i < modelSize; ++i)
      norm += counts[i];
    if (norm > 0)
      for (int i = 0; i < modelSize; ++i)
        if (UNNORM) {
          probs[i + offset] = counts[i] + LAPLACE_SMOOTHING;
        } else {
          probs[i + offset] = (counts[i] + LAPLACE_SMOOTHING) / norm;
        }
  }

  public void fillProbDistI(int[] counts, float[] probs, int offset) {
    float norm = 0.0f;
    for (int i = 0; i < modelSize; ++i)
      norm += counts[i];
    if (norm > 0)
      for (int i = 0; i < modelSize; ++i)
        if (UNNORM) {
          probs[i + offset] = counts[i];
        } else {
          probs[i + offset] = counts[i] / norm;
        }
  }

  private void addCountToArray(final List<int[]> list, final int[] totalCounts,
      int type, AlignmentTemplate alTemp) {
    int idx = alTemp.getKey();
    synchronized (totalCounts) {
      ++totalCounts[type];
    }
    // Exit if alignment template was filtered out:
    if (idx < 0)
      return;
    // Determine which language to condition on:
    if (languageType == LanguageTypes.f)
      idx = alTemp.getFKey();
    if (languageType == LanguageTypes.e)
      idx = alTemp.getEKey();
    // Get array of count:
    int[] counts;
    synchronized (list) {
      while (idx >= list.size()) {
        int[] arr = new int[modelSize];
        list.add(arr);
      }
      counts = list.get(idx);
      ++counts[type];
    }
  }

  @Override
  public void report() {
    // System.err.println("LexicalReorderingFeatureExtractor: done.");
    float[] prob = new float[totalForwardCounts.length];
    if (directionType == DirectionTypes.forward
        || directionType == DirectionTypes.bidirectional) {
      fillProbDistI(totalForwardCounts, prob, 0);
      System.err.println("Counts of MSD labels (forward):");
      System.err.println("Counts: " + Arrays.toString(totalForwardCounts));
      System.err.println("RelFreq: " + Arrays.toString(prob));
    }
    if (directionType == DirectionTypes.backward
        || directionType == DirectionTypes.bidirectional) {
      fillProbDistI(totalBackwardCounts, prob, 0);
      System.err.println("Counts of MSD labels (backward):");
      System.err.println("Counts: " + Arrays.toString(totalBackwardCounts));
      System.err.println("RelFreq: " + Arrays.toString(prob));
    }
    if (directionType == DirectionTypes.joint) {
      fillProbDistI(totalJointCounts, prob, 0);
      System.err.println("Counts of MSD labels (joint):");
      System.err.println("Counts: " + Arrays.toString(totalJointCounts));
      System.err.println("RelFreq: " + Arrays.toString(prob));
    }
  }
}
