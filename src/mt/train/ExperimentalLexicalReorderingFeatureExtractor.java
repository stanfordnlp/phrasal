package mt.train;

import java.util.ArrayList;
import java.util.Properties;
import java.util.Arrays;

import mt.train.AlignmentGrid.RelativePos;

import edu.stanford.nlp.util.Index;

/**
 * Extractor for lexicalized re-ordering probabilities of Moses (new version).
 *
 * @author Michel Galley
 */
public class ExperimentalLexicalReorderingFeatureExtractor extends AbstractFeatureExtractor<String> {

  public static final String DEBUG_PROPERTY = "DebugLexicalReorderingFeatureExtractor";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  ArrayList<Object> forwardCounts = null, backwardCounts = null, jointCounts = null;
  double[] totalForwardCounts = null, totalBackwardCounts = null, totalJointCounts = null;

  static int printCounter = 0;

  enum DirectionTypes { forward, backward, bidirectional, joint };
  enum ReorderingTypes { monotone, swap, discont1, discont2, start, end };
  enum LanguageTypes { fe, f, e };

  int modelSize = 0;
  private boolean[] enabledTypes = new boolean[ReorderingTypes.values().length];
  private int[] typeToIdx = new int[ReorderingTypes.values().length];

  private DirectionTypes directionType;
  private LanguageTypes languageType;

  private boolean phrasalReordering = false;

  private static double LAPLACE_SMOOTHING = .5;

  public void init(Properties prop, Index featureIndex, AlignmentTemplates alTemps) {
    super.init(prop,featureIndex,alTemps);
    boolean fail = false;
    // Categories enabled by default:
    enabledTypes[ReorderingTypes.monotone.ordinal()] = true;
    enabledTypes[ReorderingTypes.discont1.ordinal()] = true;
    // Type of reordering model:
    String type = prop.getProperty(CombinedFeatureExtractor.LEX_REORDERING_TYPE_OPT,"msd-bidirectional-fe");
    String[] tokens = type.split("-");
    assert(2 <= tokens.length && tokens.length <= 3);
    // Type of extraction: word-phrase (Moses) vs. phrase-phrase (Tillmann, etc):
    phrasalReordering = Boolean.getBoolean(prop.getProperty(CombinedFeatureExtractor.LEX_REORDERING_PHRASAL_OPT, "false"));
    // Get categories:
    if("msd".equals(tokens[0]) || "orientation".equals(tokens[0])) {
      enabledTypes[ReorderingTypes.swap.ordinal()] = true;
    } else if("monotonicity".equals(tokens[0])) {
    }
    else
      fail= true;
    if(phrasalReordering) {
      enabledTypes[ReorderingTypes.swap.ordinal()] =
        Boolean.getBoolean(prop.getProperty(CombinedFeatureExtractor.LEX_REORDERING_START_CLASS_OPT, "false"));
      enabledTypes[ReorderingTypes.swap.ordinal()] =
      Boolean.getBoolean(prop.getProperty(CombinedFeatureExtractor.LEX_REORDERING_2DISC_CLASS_OPT, "false"));
    }
    modelSize = initTypeToIdx();
    // Determine whether model is forward, backward, both, or joint:
    switch(tokens.length) {
    case 2:
      directionType = DirectionTypes.forward;
      break;
    case 3:
      if("bidirectional".equals(tokens[1])) {
        directionType = DirectionTypes.bidirectional;
      } else if("joint".equals(tokens[1])) {
        directionType = DirectionTypes.joint;
      } else
        fail = true;
      break;
    default:
      fail = true;
    }
    // Get language:
    String lang = tokens[tokens.length-1];
    if("fe".equals(lang)) languageType = LanguageTypes.fe;
    else if("f".equals(lang)) languageType = LanguageTypes.f;
    else if("e".equals(lang)) languageType = LanguageTypes.e;
    else fail = true;
    if(languageType != LanguageTypes.fe)
      throw new UnsupportedOperationException
          ("LexicalReorderingFeatureExtractor: model currently not supported.");
    if(fail)
      throw new UnsupportedOperationException("unknown model type: "+type);
    // Check whether the re-ordering model is a phrase-phrase ordering model rather than
    // the phrase-word ordering model of moses:
    System.err.println("lexicalized reordering model: "+type);
    System.err.println("direction type: "+directionType.toString());
    // Store total counts for each reordering type:
    // Init count arrays:
    if(directionType == DirectionTypes.forward || directionType == DirectionTypes.bidirectional) {
      forwardCounts = new ArrayList<Object>();
      totalForwardCounts = new double[modelSize];
    }
    if(directionType == DirectionTypes.backward || directionType == DirectionTypes.bidirectional) {
      backwardCounts = new ArrayList<Object>();
      totalBackwardCounts = new double[modelSize];
    }
    if(directionType == DirectionTypes.joint) {
      modelSize *= modelSize;
      jointCounts = new ArrayList<Object>();
      totalJointCounts = new double[modelSize];
    }
  }

  public boolean needAlGrid() { return phrasalReordering; }

  public void extract(SymmetricalWordAlignment sent, String info, AlignmentGrid alGrid) {}

  private ReorderingTypes getLeftToRightOrder(AlignmentTemplateInstance alTemp, AlignmentGrid alGrid) {
    WordAlignment sent = alTemp.getSentencePair();
    int f1 = alTemp.fStartPos(), f2 = alTemp.fEndPos(), e1 = alTemp.eStartPos();
    boolean connectedMonotone = isAligned(sent,e1-1,f1-1,RelativePos.NW,alGrid);
    boolean connectedSwap = isAligned(sent,e1-1,f2+1,RelativePos.NE,alGrid);
    if(connectedMonotone && !connectedSwap) return ReorderingTypes.monotone;
    else if(!connectedMonotone && connectedSwap) return ReorderingTypes.swap;
    return ReorderingTypes.discont1;
  }

  private ReorderingTypes getRightToLeftOrder(AlignmentTemplateInstance alTemp, AlignmentGrid alGrid) {
    WordAlignment sent = alTemp.getSentencePair();
    int f1 = alTemp.fStartPos(), f2 = alTemp.fEndPos(), e2 = alTemp.eEndPos();
    boolean connectedMonotone = isAligned(sent,e2+1,f1-1,RelativePos.SW,alGrid);
    boolean connectedSwap = isAligned(sent,e2+1,f2+1,RelativePos.SE,alGrid);
    if(connectedMonotone && !connectedSwap) return ReorderingTypes.monotone;
    else if(!connectedMonotone && connectedSwap) return ReorderingTypes.swap;
    return ReorderingTypes.discont1;
  }

  public void extract(AlignmentTemplateInstance alTemp, AlignmentGrid alGrid) {
    if(getCurrentPass()+1 != getRequiredPassNumber())
      return;
    ReorderingTypes type1 = getLeftToRightOrder(alTemp, alGrid);
    ReorderingTypes type2 = getRightToLeftOrder(alTemp, alGrid);
    if(directionType == DirectionTypes.forward || directionType == DirectionTypes.bidirectional) {
      // Analyze reordering (forward):
      if(DEBUG) debugOrientation("forward_global="+ phrasalReordering, alTemp, alGrid, type1);
      addCountToArray(forwardCounts, totalForwardCounts, typeToIdx[type1.ordinal()], alTemp);
    }
    if(directionType == DirectionTypes.backward || directionType == DirectionTypes.bidirectional) {
      // Analyze reordering (backward):
      if(DEBUG) debugOrientation("backward_global="+ phrasalReordering, alTemp, alGrid, type2);
      addCountToArray(backwardCounts, totalBackwardCounts, typeToIdx[type2.ordinal()], alTemp);
    }
    if(directionType == DirectionTypes.joint) {
      // Analyze reordering (bidirectional):
      int type = typeToIdx[type1.ordinal()]*modelSize+ typeToIdx[type2.ordinal()];
      addCountToArray(jointCounts, totalJointCounts, type, alTemp);
    }
  }

  public Object score(AlignmentTemplate alTemp) {
    int idx = -1;
    if(languageType == LanguageTypes.fe) idx = alTemp.getKey();
    else if(languageType == LanguageTypes.f) idx = alTemp.getFKey();
    else if(languageType == LanguageTypes.e) idx = alTemp.getEKey();
    assert(idx >= 0);
    double[] scores = new double[modelSize];
    if(directionType == DirectionTypes.joint) {
      fillProbDist((double[])jointCounts.get(idx), scores, 0);
    } else {
      int offset = 0;
      if(directionType == DirectionTypes.forward || directionType == DirectionTypes.bidirectional) {
        fillProbDist((double[])forwardCounts.get(idx), scores, offset);
        offset += modelSize;
      }
      if(directionType == DirectionTypes.backward || directionType == DirectionTypes.bidirectional) {
        fillProbDist((double[])backwardCounts.get(idx), scores, offset);
        offset += modelSize;
      }
    }
    return scores;
  }

  private int initTypeToIdx() {
    int curOrd = -1, curIdx = -1;
    for(int i=0; i<enabledTypes.length; ++i) {
      ++curOrd;
      if(!enabledTypes[i]) continue;
      typeToIdx[curOrd] = ++curIdx;
      System.err.println("reordering type enabled: "+ReorderingTypes.values()[i]);
    }
    return curIdx+1;
  }

  private void debugOrientation(String id, AlignmentTemplateInstance alTemp, AlignmentGrid alGrid, ReorderingTypes type) {
    if(alTemp.f().size() > 20 || alTemp.e().size() > 20) return;
    assert(alGrid != null);
    System.err.printf("Model %s has orientation %s according to the grid:\n", id, type.toString());
    alGrid.printAlTempInGrid(null,alTemp.getSentencePair(),alTemp,System.err);
  }

  private boolean isAligned(WordAlignment sent, int ei, int fi, RelativePos pos, AlignmentGrid alGrid) {
    assert(fi >= -1 && ei >= -1);
    assert(fi <= sent.f().size() && ei <= sent.e().size());
    if(fi == -1 && ei == -1) return true;
    if(fi == -1 || ei == -1) return false;
    if(fi == sent.f().size() && ei == sent.e().size()) return true;
    if(fi == sent.f().size() || ei == sent.e().size()) return false;
    if(phrasalReordering) {
      // Phrase-phrase reordering as in Tillman:
      AlGridCell cell = alGrid.cellAt(fi,ei);
      if(DEBUG) {
        System.err.printf("relative position: %s\n",pos.toString());
        System.err.printf("orientation at: f=%d e=%d\n",fi,ei);
        System.err.println("  hasTopLeft: "+cell.hasTopLeft());
        System.err.println("  hasTopRight: "+cell.hasTopRight());
        System.err.println("  hasBottomLeft: "+cell.hasBottomLeft());
        System.err.println("  hasBottomRight: "+cell.hasBottomRight());
      }
      return
        (
         (pos == RelativePos.NW && cell.hasBottomRight()) ||
         (pos == RelativePos.NE && cell.hasBottomLeft()) ||
         (pos == RelativePos.SW && cell.hasTopRight()) ||
         (pos == RelativePos.SE && cell.hasTopLeft())
        );
    } else {
      // Word-phrase reordering as in Moses:
      return sent.f2e(fi).contains(ei);
    }
  }

  public void fillProbDist(double[] counts, double[] probs, int offset) {
    double norm=0.0;
    for(int i=0; i<modelSize; ++i)
      norm += counts[i];
    if(norm > 0)
      for(int i=0; i<modelSize; ++i)
        probs[i+offset] = counts[i]/norm;
  }

  private void addCountToArray
      (ArrayList<Object> list, double[] totalCounts, int type, AlignmentTemplate alTemp) {
    int idx = alTemp.getKey();
    ++totalCounts[type];
    // Exit if alignment template was filtered out:
    if(idx < 0)
      return;
    // Determine which language to condition on:
    if(languageType == LanguageTypes.f) idx = alTemp.getFKey();
    if(languageType == LanguageTypes.e) idx = alTemp.getEKey();
    // Get array of count:
    double[] counts = null;
    while(idx >= list.size()) {
      double[] arr = new double[modelSize];
      Arrays.fill(arr, LAPLACE_SMOOTHING);
      //arr[size-1] += (maxSize-size)*LAPLACE_SMOOTHING; // TODO: figure this out
      list.add(arr);
    }
    counts = (double[]) list.get(idx);
    ++counts[type];
  }

  public void report() {
    System.err.println("LexicalReorderingFeatureExtractor: done.");
    double[] prob = new double[totalForwardCounts.length];
    if(directionType == DirectionTypes.forward || directionType == DirectionTypes.bidirectional) {
      fillProbDist(totalForwardCounts, prob, 0);
      System.err.println("Distrbuction over labels (forward):");
      System.err.println(Arrays.toString(totalForwardCounts));
      System.err.println(Arrays.toString(prob));
    }
    if(directionType == DirectionTypes.backward || directionType == DirectionTypes.bidirectional) {
      fillProbDist(totalBackwardCounts, prob, 0);
      System.err.println("Distrbuction over labels (backward):");
      System.err.println(Arrays.toString(totalBackwardCounts));
      System.err.println(Arrays.toString(prob));
    }
    if(directionType == DirectionTypes.joint) {
      fillProbDist(totalJointCounts, prob, 0);
      System.err.println("Distrbuction over labels (joint):");
      System.err.println(Arrays.toString(totalJointCounts));
      System.err.println(Arrays.toString(prob));
    }
  }
}