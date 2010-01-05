package mt.train;

import java.util.List;
import java.util.ArrayList;

/**
 * Information associated with each cell of an AlignmentGrid.
 * Contains information such as whether there is an AlignmentTemplate
 * whose top left corner lies in that cell.
 *
 * @author Michel Galley
 */
public class AlGridCell<T extends AlignmentTemplateInstance> {

  private List<T>
   topLeft = new ArrayList<T>(), 
   topRight = new ArrayList<T>(), 
   bottomLeft = new ArrayList<T>(), 
   bottomRight = new ArrayList<T>();

  boolean 
    hasTopLeft, hasTopRight, 
    hasBottomLeft, hasBottomRight;

  AlGridCell() { init(); }

  public void init() {
    topLeft.clear(); topRight.clear(); bottomLeft.clear(); bottomRight.clear();
    hasTopLeft=false; hasTopRight=false;
    hasBottomLeft=false; hasBottomRight=false;
  }

  public void setTopLeft(boolean s) { hasTopLeft = s; }
  public void setTopRight(boolean s) { hasTopRight = s; }
  public void setBottomLeft(boolean s) { hasBottomLeft = s; }
  public void setBottomRight(boolean s) { hasBottomRight = s; }

  public void addTopLeft(T h) { topLeft.add(h); hasTopLeft=true; }
  public void addTopRight(T h) { topRight.add(h); hasTopRight=true; }
  public void addBottomLeft(T h) { bottomLeft.add(h); hasBottomLeft=true; }
  public void addBottomRight(T h) { bottomRight.add(h); hasBottomRight=true; }

  public boolean hasTopLeft() { return hasTopLeft; }
  public boolean hasTopRight() { return hasTopRight; }
  public boolean hasBottomLeft() { return hasBottomLeft; }
  public boolean hasBottomRight() { return hasBottomRight; }

  public List<T> getTopLeft() { return topLeft; }
  public List<T> getTopRight() { return topRight; }
  public List<T> getBottomLeft() { return bottomLeft; }
  public List<T> getBottomRight() { return bottomRight; }
}
