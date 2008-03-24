package mt;

import java.util.ArrayList;
import java.util.Properties;
import java.util.Arrays;

import edu.stanford.nlp.util.Index;
import mt.AlignmentGrid.RelativePos;

/**
 * Extractor for lexicalized re-ordering probabilities of Moses.
 * 
 * @author Michel Galley
 */
public class LexicalReorderingFeatureExtractor extends AbstractFeatureExtractor<String> {

  public static final String DEBUG_PROPERTY = "DebugLexicalReorderingFeatureExtractor";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  ArrayList<Object> 
    forwardCounts = new ArrayList<Object>(),
    backwardCounts = new ArrayList<Object>(),
    jointCounts = new ArrayList<Object>();

  double[] totalForwardCounts = null, totalBackwardCounts = null, totalJointCounts = null;

  static int printCounter = 0;
  static boolean modelStart = false;

  enum DirectionTypes { forward, backward, bidirectional, joint };
  enum ReorderingTypes { monotone, swap, discontinuous, start };
  enum LanguageTypes { fe, f, e };

  // Number of re-ordeing models, and size of each model:
  private int modelNum, modelSize, modelOrder=1;

  private DirectionTypes directionType;
  private ReorderingTypes reorderingType;
  private LanguageTypes languageType;
  private boolean hasSwap = true;
  private boolean phrasalContext = false;

  private static double LAPLACE_SMOOTHING = .5;

  public void init(Properties prop, Index featureIndex, AlignmentTemplates alTemps) {
    super.init(prop,featureIndex,alTemps);
    boolean fail = false;
    String type = prop.getProperty(CombinedFeatureExtractor.PHARAOH_LEX_MODEL_OPT,"msd-bidirectional-fe");
    String[] tokens = type.split("-");
    assert(2 <= tokens.length && tokens.length <= 3);
    // Get categories:
    if("msd".equals(tokens[0]) || "global_msd".equals(tokens[0]) || 
       "orientation".equals(tokens[0]) || "global_orientation".equals(tokens[0])) {
      hasSwap = true;
      modelSize = 3;
    } else if("monotonicity".equals(tokens[0]) || "global_monotonicity".equals(tokens[0])) {
      hasSwap = false;
      modelSize = 2;
    }
    else fail= true;
    // Determine the number of models:
    switch(tokens.length) {
    case 2: 
      directionType = DirectionTypes.forward; 
      modelNum = 1;
      break;
    case 3: 
      if("bidirectional".equals(tokens[1])) {
        directionType = DirectionTypes.bidirectional;
        modelNum = 2;
      } else if("joint".equals(tokens[1])) {
        directionType = DirectionTypes.joint;
        modelNum = 1;
        modelOrder = 2;
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
    if(fail)
      throw new UnsupportedOperationException("unknown model type: "+type);
    // Check whether the re-ordering model is a phrase-phrase ordering model rather than
    // the phrase-word ordering model of moses:
    if(tokens[0].startsWith("global_"))
      phrasalContext = true;
    System.err.println("lexicalized reordering model: "+type);
    System.err.println("direction type: "+directionType.toString());
    System.err.println("has swap: "+hasSwap);
    // Error messages for unsupported models:
    if(languageType != LanguageTypes.fe)
      throw new UnsupportedOperationException
       ("LexicalReorderingFeatureExtractor: model currently not supported.");
    // Store total counts for each reordering type:
    int size = (int)Math.pow(modelSize,modelOrder);
    totalForwardCounts = new double[size];
    totalBackwardCounts = new double[size];
    totalJointCounts = new double[size];
  }

  public boolean needAlGrid() { return phrasalContext; }

  public void extract(SymmetricalWordAlignment sent, String info, AlignmentGrid alGrid) {}

  public void extract(AlignmentTemplateInstance alTemp, AlignmentGrid alGrid) {
    if(getCurrentPass()+1 != getRequiredPassNumber())
      return;
    int maxSize = 3;
    int f1 = alTemp.fStartPos(), f2 = alTemp.fEndPos(), e1 = alTemp.eStartPos(), e2 = alTemp.eEndPos();
    WordAlignment sent = alTemp.getSentencePair();
    ReorderingTypes type1 = ReorderingTypes.discontinuous, type2 = ReorderingTypes.discontinuous;
    boolean connectedTopLeft = isAligned(sent,e1-1,f1-1,RelativePos.NW,alGrid);
    boolean connectedTopRight = isAligned(sent,e1-1,f2+1,RelativePos.NE,alGrid);
    boolean connectedBottomLeft = isAligned(sent,e2+1,f1-1,RelativePos.SW,alGrid);
    boolean connectedBottomRight = isAligned(sent,e2+1,f2+1,RelativePos.SE,alGrid);
    if(modelStart && f1==-1 && e1==-1)
      type1 = ReorderingTypes.start;
    else if(connectedTopLeft && !connectedTopRight) 
      type1 = ReorderingTypes.monotone;
    else if(!connectedTopLeft && connectedTopRight) 
      type1 = ReorderingTypes.swap;
    if(modelStart && f1==sent.f().size() && e1==sent.e().size())
      type1 = ReorderingTypes.start;
    if(connectedBottomRight && !connectedBottomLeft) 
      type2 = ReorderingTypes.monotone;
    else if(!connectedBottomRight && connectedBottomLeft) 
      type2 = ReorderingTypes.swap;
    if(directionType == DirectionTypes.forward || directionType == DirectionTypes.bidirectional) {
      // Analyze reordering (forward):
      if(DEBUG) debugOrientation("forward_global="+phrasalContext, alTemp, alGrid, type1);
      addCountToArray(forwardCounts, totalForwardCounts, type1.ordinal(), modelSize, maxSize, alTemp);
    }
    if(directionType == DirectionTypes.backward || directionType == DirectionTypes.bidirectional) {
      // Analyze reordering (backward):
      if(DEBUG) debugOrientation("backward_global="+phrasalContext, alTemp, alGrid, type2);
      addCountToArray(backwardCounts, totalBackwardCounts, type2.ordinal(), modelSize, maxSize, alTemp);
    }
    if(directionType == DirectionTypes.joint) {
      assert(modelSize == 3); // breaks otherwise
      // Analyze reordering (bidirectional):
      int type = type1.ordinal()*modelSize+ type2.ordinal();
      addCountToArray(jointCounts, totalJointCounts, type, modelSize*modelSize, 9, alTemp);
    }
  }

  private void debugOrientation(String id, AlignmentTemplateInstance alTemp, AlignmentGrid alGrid, ReorderingTypes type) {
    if(alTemp.f().size() > 20 || alTemp.e().size() > 20) return;
    assert(alGrid != null);
    System.err.printf("Model %s has orientation %s according to the grid:\n", id, type.toString());
    alGrid.printAlTempInGrid(null,alTemp.getSentencePair(),alTemp,System.err);
  }

  public Object score(AlignmentTemplate alTemp) {
    int idx = -1;
    if(languageType == LanguageTypes.fe) idx = alTemp.getKey();
    else if(languageType == LanguageTypes.f) idx = alTemp.getFKey();
    else if(languageType == LanguageTypes.e) idx = alTemp.getEKey();
    assert(idx >= 0);
    int size = (int)Math.pow(modelSize,modelOrder);
    double[] scores = new double[modelNum*size];
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

  private boolean isAligned(WordAlignment sent, int ei, int fi, RelativePos pos, AlignmentGrid alGrid) {
    assert(fi >= -1 && ei >= -1);
    assert(fi <= sent.f().size() && ei <= sent.e().size());
    if(fi == -1 && ei == -1) return true;
    if(fi == -1 || ei == -1) return false;
    if(fi == sent.f().size() && ei == sent.e().size()) return true;
    if(fi == sent.f().size() || ei == sent.e().size()) return false;
    if(phrasalContext) {
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
    }
    return sent.f2e(fi).contains(ei);
  }

  public void fillProbDist(double[] counts, double[] probs, int offset) {
    double norm=0.0;
    int size = (int)Math.pow(modelSize,modelOrder);
    for(int i=0; i<size; ++i)
      norm += counts[i];
    if(norm > 0)
      for(int i=0; i<size; ++i)
        probs[i+offset] = counts[i]/norm;
  }

  private void addCountToArray
      (ArrayList<Object> list, double[] totalCounts, int type, int modelSize, int maxSize, AlignmentTemplate alTemp) {
    int idx = alTemp.getKey();
    int size = (int)Math.pow(modelSize,modelOrder);
    if(type >= size)
      type = size-1;
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
      double[] arr = new double[size];
      Arrays.fill(arr, LAPLACE_SMOOTHING);
      arr[size-1] += (maxSize-size)*LAPLACE_SMOOTHING;
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
