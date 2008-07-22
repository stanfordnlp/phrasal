package mt.train.zh;

import mt.train.AlGridCell;
import mt.train.AlignmentGrid;
import mt.train.AlignmentTemplate;
import mt.train.AlignmentTemplateInstance;
import mt.train.AlignmentTemplates;
import mt.train.SymmetricalWordAlignment;

import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Properties;

import edu.stanford.nlp.util.Index;

public class LexicalReorderingFeatureExtractor extends AbstractChineseSyntaxFeatureExtractor<String> {

  ArrayList<Object> forwardCounts = new ArrayList<Object>();
  ArrayList<Object> backwardCounts = new ArrayList<Object>();
  ArrayList<Object> forwardCounts_f = new ArrayList<Object>();
  ArrayList<Object> backwardCounts_f = new ArrayList<Object>();
  

  static int printCounter = 0;

  enum DirectionTypes { forward, backward, bidirectional, joint };
  enum ReorderingTypes { monotone, discontinuous, swap };

  private DirectionTypes directionType;

  //private static double LAPLACE_SMOOTHING = 0.0;
  private static int LAPLACE_SMOOTHING = 0;

  @Override
	public void init(Properties prop, Index<String> featureIndex, AlignmentTemplates alTemps) {
    super.init(prop,featureIndex,alTemps);
    directionType = DirectionTypes.bidirectional;
  }

  @Override
	public boolean needAlGrid() { return true; }

  @Override
	public void extract(SymmetricalWordAlignment sent, String info, AlignmentGrid alGrid) {}

  @Override
	public void extract(AlignmentTemplateInstance alTemp, AlignmentGrid alGrid, AlignmentGrid fullAlGrid, String infoLine) {
    int f1 = alTemp.fStartPos(), f2 = alTemp.fEndPos(), e1 = alTemp.eStartPos(), e2 = alTemp.eEndPos();
    ReorderingTypes type1 = ReorderingTypes.discontinuous, type2 = ReorderingTypes.discontinuous;
    if(directionType == DirectionTypes.forward || directionType == DirectionTypes.bidirectional) {
      // Analyze reordering (forward):
      //boolean connectedTopLeft = isAligned(sent,f1-1,e1-1);
      //boolean connectedTopRight = isAligned(sent,f2+1,e1-1);
      boolean connectedTopLeft = isConnectedTopLeft(fullAlGrid,f1,e1);
      boolean connectedTopRight = isConnectedTopRight(fullAlGrid,f2,e1);
      if(connectedTopLeft && !connectedTopRight)
        type1 = ReorderingTypes.monotone;
      if(!connectedTopLeft && connectedTopRight)
        type1 = ReorderingTypes.swap;
      if (connectedTopLeft && connectedTopRight) {
        //System.err.println("DEBUG: What is the type (fwd)??");
        throw new RuntimeException("DEBUG: What is the type (fwd)??");
      }
      if (ChineseSyntaxCombinedFeatureExtractor.VERBOSE) System.err.printf("ADD FWD: (%s) %s\n", type1, alTemp.toString(true));
      addCountToArray(forwardCounts, type1, alTemp);
      addCountToArray(forwardCounts_f, type1, alTemp.getFKey());
    }
    if(directionType == DirectionTypes.backward || directionType == DirectionTypes.bidirectional) {
      // Analyze reordering (backward):
      //boolean connectedBottomLeft = isAligned(sent,f1-1,e2+1);
      //boolean connectedBottomRight = isAligned(sent,f2+1,e2+1);
      boolean connectedBottomLeft = isConnectedBottomLeft(fullAlGrid,f1,e2);
      boolean connectedBottomRight = isConnectedBottomRight(fullAlGrid,f2,e2);
      if(connectedBottomRight && !connectedBottomLeft)
        type2 = ReorderingTypes.monotone;
      if(!connectedBottomRight && connectedBottomLeft)
        type2 = ReorderingTypes.swap;
      if (connectedBottomRight && connectedBottomLeft) {
        throw new RuntimeException("DEBUG: What is the type (bwd)??");
      }
      if (ChineseSyntaxCombinedFeatureExtractor.VERBOSE) System.err.printf("ADD BWD: (%s) %s\n", type2, alTemp.toString(true));
      addCountToArray(backwardCounts, type2, alTemp);
      addCountToArray(backwardCounts_f, type2, alTemp.getFKey());
    }
  }

  @Override
	public void report(AlignmentTemplates alTemps) {
    Set<Integer> isPrinted = new HashSet<Integer>();
    int[] countsP_f = null;
    int[] countsN_f = null;
    System.out.println("----------------------------------------------");

    AlignmentTemplateInstance alTemp = new AlignmentTemplateInstance();

    for(int idx=0; idx<alTemps.size(); ++idx) {
      alTemps.reconstructAlignmentTemplate(alTemp, idx);
      int fidx = alTemp.getFKey();
      if (isPrinted.contains(fidx)) continue;
      isPrinted.add(fidx);
      
      countsP_f = (int[]) forwardCounts_f.get(fidx);
      countsN_f = (int[]) backwardCounts_f.get(fidx);
      int sumP_f = countsP_f[0]+countsP_f[1]+countsP_f[2];
      int sumN_f = countsN_f[0]+countsN_f[1]+countsN_f[2];
      if (sumP_f != sumN_f) { throw new RuntimeException("sumP_f != sumN_f"); };
      System.out.printf("REPORT:\t%s\t%d\t%.4f\t%.4f\t%.4f\t%.4f\t%.4f\t%.4f\n",
                        alTemp.f().toString(),
                        sumP_f,
                        (double)countsP_f[0]/sumP_f,
                        (double)countsP_f[1]/sumP_f,
                        (double)countsP_f[2]/sumP_f,
                        (double)countsN_f[0]/sumN_f,
                        (double)countsN_f[1]/sumN_f,
                        (double)countsN_f[2]/sumN_f);
    }
    System.out.println("----------------------------------------------");
  }

  @Override
	public Object score(AlignmentTemplate alTemp) {
    int idx = alTemp.getKey();
    int[] countsP = null;
    int[] countsN = null;
    int[] countsP_f = null;
    int[] countsN_f = null;
    
    if(directionType == DirectionTypes.forward || directionType == DirectionTypes.bidirectional) {
      countsP   = (int[]) forwardCounts.get(idx);
      countsP_f = (int[]) forwardCounts_f.get(alTemp.getFKey());
    }
    if(directionType == DirectionTypes.backward || directionType == DirectionTypes.bidirectional) {
      countsN   = (int[]) backwardCounts.get(idx);
      countsN_f = (int[]) backwardCounts_f.get(alTemp.getFKey());
    }
    int sumP = countsP[0]+countsP[1]+countsP[2];
    int sumN = countsN[0]+countsN[1]+countsN[2];
    int sumP_f = countsP_f[0]+countsP_f[1]+countsP_f[2];
    int sumN_f = countsN_f[0]+countsN_f[1]+countsN_f[2];
    /*
    return new int[] {
      countsP[0]/sumP, countsP[2]/sumP, countsP[1]/sumP,
      countsN[0]/sumN, countsN[2]/sumN, countsN[1]/sumN };
    */
    if (sumP != sumN) { throw new RuntimeException("sumP != sumN"); };
    if (sumP_f != sumN_f) { throw new RuntimeException("sumP_f != sumN_f"); };
    return new double[] {
      sumP, sumP_f
    };
  }


  private boolean isConnectedTopLeft(AlignmentGrid fullAlGrid, int fi, int ei) {
    int cellf = fi-1;
    int celle = ei-1;
    if (cellf < 0 || celle < 0 || cellf >= fullAlGrid.fsize() || celle >= fullAlGrid.esize()) 
      return false;
    AlGridCell<AlignmentTemplateInstance> cell = fullAlGrid.cellAt(cellf,celle);
    if (cell == null) return false;
    return cell.hasBottomRight();
  }

  private boolean isConnectedTopRight(AlignmentGrid fullAlGrid, int fi, int ei) {
    int cellf = fi+1;
    int celle = ei-1;
    if (cellf < 0 || celle < 0 || cellf >= fullAlGrid.fsize() || celle >= fullAlGrid.esize())
      return false;
    AlGridCell<AlignmentTemplateInstance> cell = fullAlGrid.cellAt(cellf,celle);
    if (cell == null) return false;
    //return cell.hasTopRight();
    //System.err.println("DEBUG: isConnectedTopRight : cell.hasBottomLeft()="+cell.hasBottomLeft());
    return cell.hasBottomLeft();
  }


  private boolean isConnectedBottomLeft(AlignmentGrid fullAlGrid, int fi, int ei) {
    int cellf = fi-1;
    int celle = ei+1;
    if (cellf < 0 || celle < 0 || cellf >= fullAlGrid.fsize() || celle >= fullAlGrid.esize())
      return false;
    AlGridCell<AlignmentTemplateInstance> cell = fullAlGrid.cellAt(cellf,celle);
    if (cell == null) return false;
    return cell.hasTopRight();
    //return cell.hasBottomLeft();
  }

  private boolean isConnectedBottomRight(AlignmentGrid fullAlGrid, int fi, int ei) {
    int cellf = fi+1;
    int celle = ei+1;
    if (cellf < 0 || celle < 0 || cellf >= fullAlGrid.fsize() || celle >= fullAlGrid.esize())
      return false;
    AlGridCell<AlignmentTemplateInstance> cell = fullAlGrid.cellAt(cellf,celle);
    if (cell == null) return false;
    return cell.hasTopLeft();

  }

  private void addCountToArray(ArrayList<Object> list, ReorderingTypes type, AlignmentTemplate alTemp) {
    int idx = alTemp.getKey();
    // Exit if alignment template was filtered out:
    if(idx < 0)
      return;
    // Handle distinction between swap and discontinuous:
    int[] counts = null;
    while(idx >= list.size())
      list.add(new int[] {LAPLACE_SMOOTHING, LAPLACE_SMOOTHING, LAPLACE_SMOOTHING});
    counts = (int[]) list.get(idx);
    ++counts[type.ordinal()];
  }

  private void addCountToArray(ArrayList<Object> list, ReorderingTypes type, int idx) {
    // Exit if alignment template was filtered out:
    if(idx < 0)
      return;
    // Handle distinction between swap and discontinuous:
    int[] counts = null;
    while(idx >= list.size())
      list.add(new int[] {LAPLACE_SMOOTHING, LAPLACE_SMOOTHING, LAPLACE_SMOOTHING});
    counts = (int[]) list.get(idx);
    ++counts[type.ordinal()];
  }
}
