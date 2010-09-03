package edu.stanford.nlp.mt.train;

import java.util.List;
import java.util.ArrayList;
import java.io.PrintStream;

import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.Sequence;

/**
 * A representation of an AlignmentTemplate collection as a matrix.
 * Note that rows correspond to e, and columns correspond to f.
 *
 * @author Michel Galley
 */
public class AlignmentGrid {

  public static final int MAX_SENT_LEN = 256;

  private int fSize, eSize; //, maxSizeF, maxSizeE;

  private WordAlignment sent;
  Integer sentId;

  public enum RelativePos { N, NW, W, SW, S, SE, E, NE, I }

  private static final String SYM_C = "+"; 
  private static final String SYM_V = "|"; 
  private static final String SYM_H = "-"; 
  private static final String SYM_U = "."; // unaligned
  private static final String SYM_P = "#"; // altemp
  private static final String SYM_A = "x"; // word alignment

  private final List<AlignmentTemplateInstance>
    alTempList = new ArrayList<AlignmentTemplateInstance>();

  private AlGridCell<AlignmentTemplateInstance>[][] alGridCells = null;

  /**
   * Create an alignment grid of size esize x fsize.
   */
  @SuppressWarnings("unchecked")
	public AlignmentGrid(int eSize, int fSize) {
    alGridCells = new AlGridCell[MAX_SENT_LEN][MAX_SENT_LEN];
    //System.err.printf("AlignmentGrid: constructor (%d x %d).\n",MAX_SENT_LEN,MAX_SENT_LEN);
    this.sent = null;
    //this.maxSizeE = -1;
    //this.maxSizeF = -1;
    init(eSize, fSize);
  }

  /**
   * Return all alignment templates contained in the grid.
   */
  public List<AlignmentTemplateInstance> getAlTemps() { return alTempList; }

  /**
   * Initialize an alignment grid of size esize x fsize.
   */

  public void init(WordAlignment s) {
    if (sentId != null && s.getId().equals(sentId)) {
      return;
    }
    init(s.e().size(), s.f().size());
    this.sent = s;
    sentId = s.getId();
  }

  public void init(int eSize, int fSize) {

    this.sent = null;

    if (eSize >= MAX_SENT_LEN || fSize >= MAX_SENT_LEN)
      throw new UnsupportedOperationException("Sentence too long: fsize="+ fSize +" esize="+ eSize);

    this.eSize = eSize;
    this.fSize = fSize;

    alTempList.clear();

    for (int fi = 0; fi < fSize; ++fi) {
      for (int ei = 0; ei < eSize; ++ei) {
        AlGridCell<AlignmentTemplateInstance> cell = alGridCells[fi][ei];
        if (cell == null)
          alGridCells[fi][ei] = new AlGridCell<AlignmentTemplateInstance>();
        else
          cell.init();
      }
    }
  }

  public int fsize() { return fSize; }

  public int esize() { return eSize; }

  public WordAlignment getWordAlignment() { return sent; }

  public AlGridCell<AlignmentTemplateInstance> cellAt(int fi, int ei) { return alGridCells[fi][ei]; }

  /**
   * Add alignment template to the grid.
   */
  public void addAlTemp(AlignmentTemplateInstance alTemp, boolean isConsistent) {
    int e1 = alTemp.eStartPos(), e2 = alTemp.eEndPos(), f1 = alTemp.fStartPos(), f2 = alTemp.fEndPos();
    if (isConsistent) {
      alGridCells[f1][e1].addTopLeft(alTemp);
      alGridCells[f2][e1].addTopRight(alTemp);
      alGridCells[f1][e2].addBottomLeft(alTemp);
      alGridCells[f2][e2].addBottomRight(alTemp);
    }
    alTempList.add(alTemp);
  }

  /**
   * Add coordinates of an alignment template to the grid. Allows to cut down memory usage,
   * since one doesn't need to allocate each alignment template.
   */
  public void addAlTemp(int f1, int f2, int e1, int e2) {
    alGridCells[f1][e1].setTopLeft(true);
    alGridCells[f2][e1].setTopRight(true);
    alGridCells[f1][e2].setBottomLeft(true);
    alGridCells[f2][e2].setBottomRight(true);
  }

  public static RelativePos relativePos(AlignmentTemplateInstance alTemp, int f, int e) {
    int e1 = alTemp.eStartPos(), e2 = alTemp.eEndPos(), f1 = alTemp.fStartPos(), f2 = alTemp.fEndPos();

    if (f < f1) {
      if(e < e1) return RelativePos.NW;
      if(e > e2) return RelativePos.SW;
      return RelativePos.W;
    }
    if (f > f2) {
      if(e < e1) return RelativePos.NE;
      if(e > e2) return RelativePos.SE;
      return RelativePos.N;
    }
    if (e < e1) return RelativePos.N;
    if (e > e2) return RelativePos.S;
    return RelativePos.I;
  }

  /** 
   * Display alTemp and word alignments in the alignment grid. The alTemp is displayed
   * as a big filled square, and individual word alignments are shown as crosses ("x").
   * The grid also displays contextual alignment templates that are consistent with alTemp
   * (consistent means both E and F spans do not overlap). For instance, the "1" symbol 
   * indicates the location of the bottom-left corner of any alignment template found 
   * north-west relative to altemp. The same goes for "2", "3", and "4" for the three
   * other directions.
   */
  public void printAlTempInGrid(String id, AlignmentTemplateInstance alTemp, PrintStream out) {

    if (id != null)
      out.println(id);
    out.print(SYM_C);

    for (int fi=0; fi<fSize; ++fi) {
      if (fi>0) out.print(SYM_H);
      out.print(SYM_H+SYM_H);
    }
    out.println(SYM_H+SYM_C);

    for (int ei=0; ei<eSize; ++ei) {

      out.print(SYM_V);

      for (int fi=0; fi<fSize; ++fi) {

        if (fi>0) out.print(" ");
        assert (sent != null);
        String alignSym = sent.f2e(fi).contains(ei) ? SYM_A : SYM_U;
        String phraseSym = " ";

        if (alTemp != null) {
          RelativePos pos = relativePos(alTemp,fi,ei);
          if (pos == RelativePos.NW && alGridCells[fi][ei].hasBottomRight()) phraseSym = "1";
          if (pos == RelativePos.SW && alGridCells[fi][ei].hasTopRight()) phraseSym = "2";
          if (pos == RelativePos.NE && alGridCells[fi][ei].hasBottomLeft()) phraseSym = "3";
          if (pos == RelativePos.SE && alGridCells[fi][ei].hasTopLeft()) phraseSym = "4";
          if (alTemp.fStartPos() <= fi && fi <= alTemp.fEndPos() &&
             alTemp.eStartPos() <= ei && ei <= alTemp.eEndPos()) {
             assert(" ".equals(phraseSym));
             phraseSym = SYM_P;
             if (alignSym.equals(SYM_U))
              alignSym = SYM_P;
          }
        }

        out.print(phraseSym+alignSym);

      }

      out.printf(" %s %2d %s\n",SYM_V,ei,sent.e().get(ei).toString());
    }
    out.print(SYM_C);

    for (int fi=0; fi<fSize; ++fi) {
      if (fi>0) out.print(SYM_H);
      out.print(SYM_H+SYM_H);
    }
    out.print(SYM_H+SYM_C+"\n ");

    for (int fi=0; fi<fSize; ++fi) {
      if (fi>0) out.print(" ");
      out.printf("%2d",fi);
    }
    out.println();

    for (int fi=0; fi<fSize; ++fi) {
      out.printf("%d=%s ",fi,sent.f().get(fi).toString());
    }
    //out.println("\n\n");
  } 

  /**
   * Print alignment grid corresponding decoding history.
   */
  public static void printDecoderGrid(Featurizable<IString, String> f, PrintStream out) {
    if (!f.done)
      throw new RuntimeException("AlignmentGrid: not finished decoding!");
    Sequence<IString> eSeq = f.partialTranslation, fSeq = f.foreignSentence;
    SymmetricalWordAlignment sent = new SymmetricalWordAlignment(fSeq, eSeq);
    AlignmentGrid alGrid = new AlignmentGrid(eSeq.size(),fSeq.size());
    while (f != null) {
      for (int fi = f.foreignPosition; fi<f.foreignPosition+f.foreignPhrase.size(); ++fi)
        for (int ei = f.translationPosition; ei<f.translationPosition+f.translatedPhrase.size(); ++ei)
          sent.addAlign(fi,ei);
      f = f.prior;
    }
    alGrid.sent = sent;
    alGrid.printAlTempInGrid("Alignment grid for decoded sentence: "+eSeq, null, out);
  }

  public void setWordAlignment(WordAlignment sent) {
    this.sent = sent;
  }

}
