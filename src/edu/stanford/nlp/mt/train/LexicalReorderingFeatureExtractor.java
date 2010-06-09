package edu.stanford.nlp.mt.train;

import java.util.ArrayList;
import java.util.Properties;
import java.util.Arrays;

import edu.stanford.nlp.mt.train.AlignmentGrid.RelativePos;

import edu.stanford.nlp.util.Index;

/**
 * Extractor for lexicalized re-ordering probabilities of Moses.
 *
 * @author Michel Galley
 */
public class LexicalReorderingFeatureExtractor extends AbstractFeatureExtractor {

  public static final String DEBUG_PROPERTY = "DebugLexicalReorderingFeatureExtractor";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  public static final String UNNORM_PROPERTY = "Unnormalize";
  public static final boolean UNNORM = Boolean.parseBoolean(System.getProperty(UNNORM_PROPERTY, "false"));

  public static final String LAPLACE_PROPERTY = "LaplaceSmoothing";
  private static float LAPLACE_SMOOTHING = Float.parseFloat(System.getProperty(LAPLACE_PROPERTY, "0.5f"));

  ArrayList<Object> forwardCounts = null, backwardCounts = null, jointCounts = null;
  float[] totalForwardCounts = null, totalBackwardCounts = null, totalJointCounts = null;

  enum DirectionTypes { forward, backward, bidirectional, joint }
  enum ReorderingTypes { monotone, swap, discont1, discont2 } //, inside, outside }
  enum PermType { x, y } //, nil }
  enum LanguageTypes { fe, f, e }

  int modelSize = 0;
  int numModels = 0;
  private boolean[] enabledTypes = new boolean[ReorderingTypes.values().length];
  private int[] typeToIdx = new int[ReorderingTypes.values().length];

  private DirectionTypes directionType;
  private LanguageTypes languageType;

  private boolean phrasalReordering = false;

  @Override
	public void init(Properties prop, Index<String> featureIndex, AlignmentTemplates alTemps) {
    super.init(prop,featureIndex,alTemps);
    boolean fail = false;
    // Categories enabled by default:
    enabledTypes[ReorderingTypes.monotone.ordinal()] = true;
    enabledTypes[ReorderingTypes.discont1.ordinal()] = true;
    // Type of reordering model:
    String type = prop.getProperty(PhraseExtract.LEX_REORDERING_TYPE_OPT,"msd-bidirectional-fe");
    String[] tokens = type.split("-");
    assert(2 <= tokens.length && tokens.length <= 3);
    // Type of extraction: word-phrase (Moses) vs. phrase-phrase (Tillmann, etc):
    phrasalReordering = Boolean.parseBoolean(prop.getProperty(PhraseExtract.LEX_REORDERING_PHRASAL_OPT, "false"));
    System.err.println("phrase-phrase reordering: "+phrasalReordering);
    if(phrasalReordering) {
      enabledTypes[ReorderingTypes.discont2.ordinal()] =
        Boolean.parseBoolean(prop.getProperty(PhraseExtract.LEX_REORDERING_2DISC_CLASS_OPT, "false"));
    }
    // Get categories:
    if("msd".equals(tokens[0]) || "msd2".equals(tokens[0]) || "orientation".equals(tokens[0])) {
      enabledTypes[ReorderingTypes.swap.ordinal()] = true;
      if("msd2".equals(tokens[0])) {
        enabledTypes[ReorderingTypes.discont2.ordinal()] = true;
        System.err.println("msd2: yes");
      }
    } else if("monotonicity".equals(tokens[0])) {
      // No swap category.
    }
    else
      fail= true;
    modelSize = initTypeToIdx();
    // Determine whether model is forward, backward, both, or joint:
    switch(tokens.length) {
    case 2:
      directionType = DirectionTypes.forward;
      numModels = 1;
      break;
    case 3:
      if("bidirectional".equals(tokens[1])) {
        directionType = DirectionTypes.bidirectional;
        numModels = 2;
      } else if("joint".equals(tokens[1])) {
        directionType = DirectionTypes.joint;
        numModels = 1;
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
      totalForwardCounts = new float[modelSize];
    }
    if(directionType == DirectionTypes.backward || directionType == DirectionTypes.bidirectional) {
      backwardCounts = new ArrayList<Object>();
      totalBackwardCounts = new float[modelSize];
    }
    if(directionType == DirectionTypes.joint) {
      modelSize *= modelSize;
      jointCounts = new ArrayList<Object>();
      totalJointCounts = new float[modelSize];
    }
    if(Boolean.parseBoolean(prop.getProperty(DTUPhraseExtractor.WITH_GAPS_OPT))) {
      //withDTU = true;
      if (!phrasalReordering)
        System.err.println("Reordering model: discontinuous phrases without phrasal reordering.");
    }
  }

  @Override
	public boolean needAlGrid() { return phrasalReordering; }

  @Override
	public void extract(SymmetricalWordAlignment sent, String info, AlignmentGrid alGrid) {}

  private ReorderingTypes getReorderingType(AlignmentTemplateInstance alTemp, AlignmentGrid alGrid, boolean forward) {
    WordAlignment sent = alTemp.getSentencePair();
    int f1 = alTemp.fStartPos()-1, f2 = alTemp.fEndPos()+1, e1 = alTemp.eStartPos()-1, e2 = alTemp.eEndPos()+1;
    boolean connectedMonotone = forward ?
      isAligned(sent,e1,f1,RelativePos.NW,alGrid) : isAligned(sent,e2,f2,RelativePos.SE,alGrid);
    boolean connectedSwap = forward ? 
      isAligned(sent,e1,f2,RelativePos.NE,alGrid) : isAligned(sent,e2,f1,RelativePos.SW,alGrid);
    // Determine if Monotone, Swap, or Discontinuous:
    if(connectedMonotone && !connectedSwap) return ReorderingTypes.monotone;
    if(!connectedMonotone && connectedSwap) return ReorderingTypes.swap;
    // If distinction between discont1 and discont2 is impossible, return discont1:
    if(!enabledTypes[ReorderingTypes.discont2.ordinal()] || !phrasalReordering) 
      return ReorderingTypes.discont1;
    // If needed, distinguish between forward and backward discontinuous:
    if(forward) {
      if(e1 >= 0)
        for(int fPos = f2; fPos<sent.f().size(); ++fPos)
          if(alGrid.cellAt(fPos,e1).hasBottomLeft())
            return ReorderingTypes.discont2;
    } else {
      if(e2 < sent.e().size())
        for(int fPos = f1; fPos>=0; --fPos) {
          if(alGrid.cellAt(fPos,e2) == null) {
            System.err.printf("ERROR: no grid cell at [%d,%d] in sentence pair of length [%d,%d]\n",
              fPos,e2,sent.f().size(), sent.e().size());
            throw new RuntimeException("Out of bounds.");
          }
          if(alGrid.cellAt(fPos,e2).hasTopRight())
            return ReorderingTypes.discont2;
        }
    }
    if(DEBUG) System.err.println("warning: falling back to default (3)");
    return ReorderingTypes.discont1;
  }

  /*
  private ReorderingTypes getDTUReorderingType(AlignmentTemplateInstance alTemp, AlignmentGrid alGrid, boolean forward) {
    
    DTUInstance dtu = alTemp instanceof DTUInstance ? ((DTUInstance) alTemp) : null;
    WordAlignment sent = null;
    BitSet thisPhraseCoverage = null;

    // 4 corners of this phrase:
    int f1 = alTemp.fStartPos()-1, f2 = alTemp.fEndPos()+1, e1 = alTemp.eStartPos()-1, e2 = alTemp.eEndPos()+1;
    int fLast = alGrid.fsize()-1;
    
    if (dtu != null) {
      thisPhraseCoverage = dtu.getFAlignment();
      sent = dtu.sent;
      assert (f1 == thisPhraseCoverage.nextSetBit(0)-1);
      assert (f2 == thisPhraseCoverage.length());
    }

    // Special case: first phrase and last phrase:
    if (forward && e1 == -1) return (f1 == -1) ?
      ReorderingTypes.monotone : ReorderingTypes.discont1;
    if (!forward && e2 == alGrid.esize()) return (f2 == alGrid.fsize()) ?
      ReorderingTypes.monotone : ReorderingTypes.discont1;
    if (f1 == -1) ++f1;
    if (f2 == alGrid.fsize()) --f2;

    //System.err.printf("new: f1=%d f2=%d e1=%d e2=%d forward=%s\n", f1, f2, e1, e2, forward);

    // Other phrases:
    Deque<PermType> typeEls = new LinkedList<PermType>();
    int ei = forward ? e1 : e2;
    for (int fi=Math.max(f1,0); fi<=Math.min(f2,fLast); ++fi) {
      AlGridCell<AlignmentTemplateInstance> otherPhraseCell = alGrid.cellAt(fi,ei);
      final boolean thisPhraseActive, otherPhraseActive;
      if (thisPhraseCoverage != null) {
        thisPhraseActive = thisPhraseCoverage.get(fi);
        if (sent.f2e(fi).contains(ei)) {
          otherPhraseActive = true;
          //System.err.println("other(1): "+otherPhraseActive);
        } else {
          if (forward) {
            otherPhraseActive =
             (otherPhraseCell.hasBottomLeft() && thisPhraseCoverage.get(fi-1)) ||
             (otherPhraseCell.hasBottomRight() && thisPhraseCoverage.get(fi+1));
            //System.err.println("other(2): "+otherPhraseActive);
          } else {
            otherPhraseActive =
             (otherPhraseCell.hasTopLeft() && thisPhraseCoverage.get(fi-1)) ||
             (otherPhraseCell.hasTopRight() && thisPhraseCoverage.get(fi+1));
            //System.err.println("other(2): "+otherPhraseActive);
          }
        }
      } else {
        thisPhraseActive = f1 < fi && fi < f2;
        if (forward) {
          otherPhraseActive =
            (otherPhraseCell.hasBottomLeft() && fi == f2) ||
            (otherPhraseCell.hasBottomRight() && fi == f1);
          //System.err.println("other(3): "+otherPhraseActive);
        } else {
          otherPhraseActive =
            (otherPhraseCell.hasTopLeft() && fi == f2) ||
            (otherPhraseCell.hasTopRight() && fi == f1);
          //System.err.println("other(4): "+otherPhraseActive);
          //System.err.println("other(4a): "+otherPhraseCell.hasTopLeft());
          //System.err.println("other(4b): "+(fi == f2-1));
          //System.err.println("other(4c): "+otherPhraseCell.hasTopRight());
          //System.err.println("other(4d): "+(fi == f1+1));
        }
      }
      if(otherPhraseActive && thisPhraseActive) {
        System.err.printf(" [fi=%d ei=%d : curOn=%s]\n", fi, ei+(forward?1:-1), thisPhraseActive);
        System.err.printf("  fi=%d ei=%d : altOn=%s {%s,%s,%s,%s}\n", fi, ei, otherPhraseActive, otherPhraseCell.hasTopLeft(), otherPhraseCell.hasTopRight(), otherPhraseCell.hasBottomLeft(), otherPhraseCell.hasBottomRight());
        alGrid.printAlTempInGrid("",alTemp,System.err);
      }
      assert (!otherPhraseActive || !thisPhraseActive);
      PermType pt = otherPhraseActive ? PermType.x : (thisPhraseActive ? PermType.y : PermType.nil );
      if (typeEls.isEmpty() || typeEls.getLast() != pt)
        if (!typeEls.isEmpty() || pt != PermType.nil)
          typeEls.add(pt);
    }
    if (typeEls.getLast() == PermType.nil)
      typeEls.removeLast();
    //if (dtu != null)
    //  System.err.printf("dtu: %s | %s | %s | %s\n", alTemp.toString(true), dtu.getFAlignment(), dtu.getEAlignment(), typeEls);
    //else
    //  System.err.printf("ctu: %s | %d-%d | %d-%d | %s\n", alTemp.toString(true), f1+1,f2-1,e1+1,e2-1, typeEls);
    if (typeEls.size() == 1)
      return getReorderingType(alTemp, alGrid, forward);
    return ReorderingTypes.monotone;
  }
  */

  @Override
	public void extract(AlignmentTemplateInstance alTemp, AlignmentGrid alGrid) {
    if(getCurrentPass()+1 != getRequiredPassNumber())
      return;
    //System.err.printf("id=%d a=%d p=[%d,%d] s=%s\n", alGrid.sentId, getCurrentPass(), alTemp.fStartPos, alTemp.eStartPos, alTemp.toString(true));
    //ReorderingTypes type1 = getDTUReorderingType(alTemp, alGrid, true);
    //ReorderingTypes type2 = getDTUReorderingType(alTemp, alGrid, false);
    //ReorderingTypes type1 = withDTU ? getDTUReorderingType(alTemp, alGrid, true) : getReorderingType(alTemp, alGrid, true);
    //ReorderingTypes type2 = withDTU ? getDTUReorderingType(alTemp, alGrid, false) : getReorderingType(alTemp, alGrid, false);
    ReorderingTypes type1 = getReorderingType(alTemp, alGrid, true);
    ReorderingTypes type2 = getReorderingType(alTemp, alGrid, false);
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
      int type = typeToIdx[type1.ordinal()]*modelSize + typeToIdx[type2.ordinal()];
      addCountToArray(jointCounts, totalJointCounts, type, alTemp);
    }
  }

  @Override
	public Object score(AlignmentTemplate alTemp) {
    int idx = -1;
    if(languageType == LanguageTypes.fe) idx = alTemp.getKey();
    else if(languageType == LanguageTypes.f) idx = alTemp.getFKey();
    else if(languageType == LanguageTypes.e) idx = alTemp.getEKey();
    assert(idx >= 0);
    float[] scores = new float[modelSize*numModels];
    if(directionType == DirectionTypes.joint) {
      fillProbDist((float[])jointCounts.get(idx), scores, 0);
    } else {
      int offset = 0;
      if(directionType == DirectionTypes.forward || directionType == DirectionTypes.bidirectional) {
        fillProbDist((float[])forwardCounts.get(idx), scores, offset);
        offset += modelSize;
      }
      if(directionType == DirectionTypes.backward || directionType == DirectionTypes.bidirectional) {
        fillProbDist((float[])backwardCounts.get(idx), scores, offset);
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
      System.err.printf("reordering type enabled: %s, idx=%d\n",ReorderingTypes.values()[i],curIdx);
    }
    return curIdx+1;
  }

  private void debugOrientation(String id, AlignmentTemplateInstance alTemp, AlignmentGrid alGrid, ReorderingTypes type) {
    if(alTemp.f().size() > 20 || alTemp.e().size() > 20) return;
    assert(alGrid != null);
    System.err.printf("Model %s has orientation %s according to the grid:\n", id, type.toString());
    alGrid.setWordAlignment(alTemp.getSentencePair());
    alGrid.printAlTempInGrid(null,alTemp,System.err);
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
      AlGridCell<AlignmentTemplateInstance> cell = alGrid.cellAt(fi,ei);
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

  public void fillProbDist(float[] counts, float[] probs, int offset) {
    float norm=0.0f;
    for(int i=0; i<modelSize; ++i)
      norm += counts[i];
    if(norm > 0)
      for(int i=0; i<modelSize; ++i)
        if(UNNORM) {
          probs[i+offset] = counts[i];
        } else {
          probs[i+offset] = counts[i]/norm;
        }
  }

  private void addCountToArray
      (final ArrayList<Object> list, final float[] totalCounts, int type, AlignmentTemplate alTemp) {
    int idx = alTemp.getKey();
    synchronized(totalCounts) {
      ++totalCounts[type];
    }
    // Exit if alignment template was filtered out:
    if(idx < 0)
      return;
    // Determine which language to condition on:
    if(languageType == LanguageTypes.f) idx = alTemp.getFKey();
    if(languageType == LanguageTypes.e) idx = alTemp.getEKey();
    // Get array of count:
    float[] counts;
    synchronized(list) {
      while(idx >= list.size()) {
        float[] arr = new float[modelSize];
        Arrays.fill(arr, LAPLACE_SMOOTHING);
        list.add(arr);
      }
      counts = (float[]) list.get(idx);
      ++counts[type];
    }
  }

  @Override
	public void report() {
    System.err.println("OldLexicalReorderingFeatureExtractor: done.");
    float[] prob = new float[totalForwardCounts.length];
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
