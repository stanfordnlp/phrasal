package mt.train;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeSet;

import mt.base.IString;
import mt.base.Sequence;

import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.IntQuadruple;
import edu.stanford.nlp.stats.TwoDimensionalCounter;


/**
 * This class follows the idea of LexicalReorderingFeatureExtractor, but instead of 
 * conditioning on the phrases, we label each statistical phrase with the syntatic
 * phrasal categories and condition on those.
 * 
 * @author Pi-Chuan Chang
 */

public class ReorderingWithSyntaticPhrasalCategoryFeatureExtractor extends AbstractFeatureExtractor {

  Map<AlignmentTemplateInstance, Set<Integer>> labelsForAlTemp = new HashMap<AlignmentTemplateInstance, Set<Integer>>();
  Index<Pair<String, Integer>> phraseNamesIndex = new Index<Pair<String, Integer>>();

  enum ReorderingTypes { ordered, distorted };

  TwoDimensionalCounter<String, ReorderingTypes> theCounter = new TwoDimensionalCounter<String, ReorderingTypes>();
  
  public boolean needAlGrid() { return true; }


  List<AlignmentTemplateInstance> getAdjacentLabeledEnglishTemplates(AlignmentTemplateInstance template, AlignmentGrid alGrid) {

    List<AlignmentTemplateInstance> list = new ArrayList<AlignmentTemplateInstance>();

    int template_f1 = template.fStartPos();
    int template_f2 = template.fEndPos();
    int template_e2 = template.eEndPos();

    if (template_e2 == alGrid.esize()-1) { // this template is already at the end on the English side
      return list;
    }

    for (int fidx = 0; fidx < alGrid.fsize(); fidx++) {
      AlGridCell<AlignmentTemplateInstance> cell = alGrid.cellAt(fidx,template_e2+1);
      if (cell.hasTopLeft()) {
        List<AlignmentTemplateInstance> topLefts = cell.getTopLeft();
        for (AlignmentTemplateInstance t : topLefts) {
          int f1 = t.fStartPos();
          int f2 = t.fEndPos();

          // check if this template unfortunately overlaps with the "template" that we'll looking at
          if ((template_f2-f1)*(f2-template_f1) >= 0) {
            continue; // overlaps!
          }
          
          Set<Integer> labels = labelsForAlTemp.get(t);
          if (labels.size() == 0) {
            continue; // t not labeled!;
          }
          
          list.add(t);
        }
      }
    }
    
    return list;
  }

  List<AlignmentTemplateInstance> getSameLabelTemplates(List<AlignmentTemplateInstance> list, String phraseStr, int num) {
    List<AlignmentTemplateInstance> newlist = new ArrayList<AlignmentTemplateInstance>();

    for(AlignmentTemplateInstance t : list) {
      Set<Integer> labels = labelsForAlTemp.get(t);
      for(Integer l : labels) {
        Pair<String, Integer> p = phraseNamesIndex.get(l);
        if (phraseStr.equals(p.first()) && p.second()!=num) { // if this can be labeled as the same phrase, but different part of the phrase
          newlist.add(t);
          break;
        }
      }
    }
    
    return newlist;
  }

  public void extract(SymmetricalWordAlignment sent, String info, AlignmentGrid alGrid) {

    labelAlTemps(sent, info, alGrid);
    System.err.println("END OF labelAlTemps");
    
    
    List<AlignmentTemplateInstance> allAlTemps = alGrid.getAlTemps();

    for (AlignmentTemplateInstance t : allAlTemps) {
      int f1 = t.fStartPos();
      int f2 = t.fEndPos();
      int e1 = t.eStartPos();
      int e2 = t.eEndPos();

      // check if this template has a label at all
      Set<Integer> labels = labelsForAlTemp.get(t);
      if (labels.size() == 0) {
        continue;
      }

      List<AlignmentTemplateInstance> list = getAdjacentLabeledEnglishTemplates(t, alGrid);
      
      System.err.printf("EXAMINING Template: f(%d-%d) e(%d-%d) %s\n", f1, f2, e1, e2, t.toString(true));

      for(Integer l : labels) {
        Pair<String, Integer> p = phraseNamesIndex.get(l);
        String phraseStr = p.first();

        String[] phraseAndRange = phraseStr.split("\\(");
        String phCat = phraseAndRange[0];

        int num = p.second();
        List<AlignmentTemplateInstance> sameLabelList = getSameLabelTemplates(list, phraseStr, num);
        if (sameLabelList.size() > 0) {
          System.err.printf("  (label) %s - %d\n", phraseStr, num);
        }
        for (AlignmentTemplateInstance sameLabelTemp : sameLabelList) {
          Set<Integer> possibleLabels = labelsForAlTemp.get(sameLabelTemp);
          System.err.printf("    Template: f(%d-%d) e(%d-%d) %s\n", 
                            sameLabelTemp.fStartPos(), sameLabelTemp.fEndPos(), sameLabelTemp.eStartPos(), sameLabelTemp.eEndPos(), 
                            sameLabelTemp.toString(true));

          // since "template" is the one that came first on the English side, if it's "num" is "1", that means this is an "ordered" case
          // otherwise, it's an "distorted" case
          if (num==1) {
            theCounter.incrementCount(phCat, ReorderingTypes.ordered);
            System.err.println("    (DEBUG) "+phCat+"\t"+ReorderingTypes.ordered);
          }
          else if (num==2) {
            theCounter.incrementCount(phCat, ReorderingTypes.distorted);
            System.err.println("    (DEBUG) "+phCat+"\t"+ReorderingTypes.distorted);
          }
          else 
            throw new RuntimeException("Why is num=="+num+"???");
          
          // for displaying only
          for(Integer pl : possibleLabels) {
            Pair<String, Integer> p2 = phraseNamesIndex.get(pl);
            if (phraseStr.equals(p2.first()) && num != p2.second()) {
              System.err.printf("      (label2) %s - %d\n", p2.first(), p2.second());
            }
          }
        }
      }
      System.err.println("--------------------------------------------------");
    }



    for (int eidx = 0; eidx < sent.e().size()-1; eidx++) {
      System.err.printf("::::For boundary between English word %s (%d) && %s (%d) ::::\n", 
                         sent.e().get(eidx),eidx,sent.e().get(eidx+1),eidx+1);
      
      for (int fidx = 0; fidx < sent.f().size(); fidx++) {
        AlGridCell<AlignmentTemplateInstance> cell = alGrid.cellAt(fidx,eidx+1);
        if (cell.hasTopLeft()) {
          List<AlignmentTemplateInstance> topLefts = cell.getTopLeft();
          for (AlignmentTemplateInstance t : topLefts) {
            int f1 = t.fStartPos();
            int f2 = t.fEndPos();
            int e1 = t.eStartPos();
            int e2 = t.eEndPos();

            Set<Integer> labels = labelsForAlTemp.get(t);
            if (labels.size() > 0) {
              System.err.printf(" ---> f(%d-%d) e(%d-%d) %s\n", f1, f2, e1, e2, t.toString(true));
              
              for(Integer l : labels) {
                Pair<String, Integer> p = phraseNamesIndex.get(l);
                System.err.println(" ---> --->"+p);
              }
            }            
          }
        }
      }
    }
  }

  private void labelAlTemps(SymmetricalWordAlignment sent, String info, AlignmentGrid alGrid) {
    List<AlignmentTemplateInstance> allAlTemps = alGrid.getAlTemps();
    VerbPhraseBoundary pb = new VerbPhraseBoundary(info);
    Map<IntQuadruple,String> boundaries = pb.getBoundaries();
    System.err.print("fLine = ");
    Sequence<IString> f = sent.f();
    for (int i = 0; i < f.size(); i++) {
      IString word = f.get(i);
      System.err.print(word+"("+i+") ");
    }
    System.err.println();
    //Sequence<IString> e = sent.e();
    System.err.print("eLine = ");
    for (int i = 0; i < sent.e().size(); i++) {
      IString word = sent.e().get(i);
      System.err.print(word+"("+i+") ");
    }
    System.err.println();

    for (AlignmentTemplateInstance t : allAlTemps) {
      int f1 = t.fStartPos();
      int f2 = t.fEndPos();
      int e1 = t.eStartPos();
      int e2 = t.eEndPos();

      System.err.printf("ALL alTemp: f(%d-%d) e(%d-%d) ||| %s\n",f1,f2,e1,e2,t.toString(true));

      Set<Integer> labels = labelsForAlTemp.get(t);
      if (labels == null) labels = new TreeSet<Integer>();

      for(Map.Entry<IntQuadruple, String> e : boundaries.entrySet()) {
        IntQuadruple ranges = e.getKey();
        String str = e.getValue();

        StringBuilder sb = new StringBuilder();
        sb.append(str).append("(")
          .append(ranges.getSource()).append("-").append(ranges.getMiddle()).append(";")
          .append(ranges.getTarget()).append("-").append(ranges.getTarget2())
          .append(")");

        int idx_ph1 = phraseNamesIndex.indexOf(new Pair<String, Integer>(sb.toString(), 1), true);
        int idx_ph2 = phraseNamesIndex.indexOf(new Pair<String, Integer>(sb.toString(), 2), true);

        if (f1 >= ranges.getSource() && f2 <= ranges.getMiddle()) {
          System.err.printf("alTemp (%d-%d)(%d-%d) %s inside %s - 1\n", f1, f2, e1, e2, t.toString(true), sb.toString());
          if (labels.contains(idx_ph1)) throw new RuntimeException("duplicate labels?");
          labels.add(idx_ph1);
        }
        /*
        if (f1 < ranges.getSource() && (f2 >= ranges.getSource() && f2 <= ranges.getMiddle())) {
          System.err.printf("alTemp (%d-%d)(%d-%d) %s overlap-front %s - 1\n", f1, f2, e1, e2, t.toString(true), sb.toString());
          if (labels.contains(idx_ph1)) throw new RuntimeException("duplicate labels?");
          labels.add(idx_ph1);
        }
        */
        if (f2 == ranges.getMiddle()) {
          System.err.printf("alTemp (%d-%d)(%d-%d) %s boundary-rear %s - 1\n", f1, f2, e1, e2, t.toString(true), sb.toString());
          if ( ! labels.contains(idx_ph1)) {
            labels.add(idx_ph1);
          }
        }
        /*
        if (f1 >= ranges.getSource() 
            && (f1 <=ranges.getMiddle() && f2 > ranges.getMiddle())
            && (f1 <=ranges.getMiddle() && f2 > ranges.getMiddle())
            && f2 < ranges.getTarget2()) {
          System.err.printf("alTemp (%d-%d)(%d-%d) %s overlap-end %s - 1\n", f1, f2, e1, e2, t.toString(true), sb.toString());
          if (labels.contains(idx_ph1)) throw new RuntimeException("duplicate labels?");
          labels.add(idx_ph1);
        }
        */
        if (f1 >= ranges.getTarget() && f2 <= ranges.getTarget2()) {
          System.err.printf("alTemp (%d-%d)(%d-%d) %s inside %s - 2\n", f1, f2, e1, e2, t.toString(true), sb.toString());
          if (labels.contains(idx_ph2)) {
            System.err.printf("Inserting(%d) failed\n", idx_ph2);
            System.err.printf("Inserting %s (%d) failed\n", phraseNamesIndex.get(idx_ph2), idx_ph2);
            throw new RuntimeException("duplicate labels?");
          }
          labels.add(idx_ph2);
        }
        /*
        if (f1 < ranges.getTarget() 
            && (f2 <= ranges.getTarget2() && f2 >= ranges.getTarget())
            && f1 > ranges.getSource()) {
          System.err.printf("alTemp (%d-%d) %s overlap-front %s - 2\n", f1, f2, t.toString(true), sb.toString());
          if (labels.contains(idx_ph2)) throw new RuntimeException("duplicate labels?");
          labels.add(idx_ph2);
        }
        if ((f1 >= ranges.getTarget() && f1 <= ranges.getTarget2()) && f2 > ranges.getTarget2()) {
          System.err.printf("alTemp (%d-%d) %s overlap-end %s - 2\n", f1, f2, t.toString(true), sb.toString());
          if (labels.contains(idx_ph2)) throw new RuntimeException("duplicate labels?");
          labels.add(idx_ph2);
        }
        */
        if (f1 == ranges.getTarget()) {
          System.err.printf("alTemp (%d-%d) %s boundary-front %s - 2\n", f1, f2, t.toString(true), sb.toString());
          if ( ! labels.contains(idx_ph2)) {
            labels.add(idx_ph2);
          }
        }
      }
      labelsForAlTemp.put(t, labels);
    }
  }

  public void extract(AlignmentTemplateInstance alTemp, AlignmentGrid alGrid) {}

  public Object score(AlignmentTemplate alTemp) { return null; }

  public Object scoreNames() { return null; }

  public void report() { 
    System.out.println("====================================================================");
    System.out.println(theCounter);
    System.out.println("====================================================================");
  
  }


}
