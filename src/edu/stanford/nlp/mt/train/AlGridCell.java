package edu.stanford.nlp.mt.train;

import java.util.List;

/**
 * Information associated with each cell of an AlignmentGrid.
 * Contains information such as whether there is an AlignmentTemplate
 * whose top left corner lies in that cell.
 *
 * @author Michel Galley
 */
public class AlGridCell<T extends AlignmentTemplateInstance> {

  boolean  hasTopLeft, hasTopRight, hasBottomLeft, hasBottomRight;

  AlGridCell() { init(); }

  public void init() {
    hasTopLeft=false; hasTopRight=false;
    hasBottomLeft=false; hasBottomRight=false;
  }

  public void setTopLeft(boolean s) { hasTopLeft = s; }
  public void setTopRight(boolean s) { hasTopRight = s; }
  public void setBottomLeft(boolean s) { hasBottomLeft = s; }
  public void setBottomRight(boolean s) { hasBottomRight = s; }

  public void addTopLeft(T h) { if (!h.isDiscontinuous()) { hasTopLeft=true; } }
  public void addTopRight(T h) { if (!h.isDiscontinuous()) { hasTopRight=true; } }
  public void addBottomLeft(T h) { if (!h.isDiscontinuous()) { hasBottomLeft=true; } }
  public void addBottomRight(T h) { if (!h.isDiscontinuous()) { hasBottomRight=true; } }

  public boolean hasTopLeft() { return hasTopLeft; }
  public boolean hasTopRight() { return hasTopRight; }
  public boolean hasBottomLeft() { return hasBottomLeft; }
  public boolean hasBottomRight() { return hasBottomRight; }

  @SuppressWarnings("unused")
  public static List<AlignmentTemplateInstance> getTopLeft() { throw new UnsupportedOperationException(); }
  @SuppressWarnings("unused")
  public static List<AlignmentTemplateInstance> getTopRight() { throw new UnsupportedOperationException(); }
  @SuppressWarnings("unused")
  public static List<AlignmentTemplateInstance> getBottomLeft() { throw new UnsupportedOperationException(); }
  @SuppressWarnings("unused")
  public static List<AlignmentTemplateInstance> getBottomRight() { throw new UnsupportedOperationException(); }

}
