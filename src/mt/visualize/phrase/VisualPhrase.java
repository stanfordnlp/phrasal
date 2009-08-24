package mt.visualize.phrase;

import java.awt.Color;
import java.util.Stack;

import javax.swing.JLabel;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;

/**
 * 
 * @author Spence Green
 */
public class VisualPhrase extends JLabel {

  private final double score;
  private final int id;
  private final int fStart;
  private final int fEnd;

  private Stack<Format> formats;
  private Format currentFormat = null;

  private static Format DEFAULT_FORMAT;
  static {
    DEFAULT_FORMAT = new Format();
    DEFAULT_FORMAT.fg = Color.BLACK;
    DEFAULT_FORMAT.bg = Color.WHITE;
    DEFAULT_FORMAT.border = new LineBorder(Color.BLACK);
  }

  public VisualPhrase(Phrase p, int id) {
    super(p.getPhrase());
    
    score = p.getScore();
    this.id = id;
    fStart = p.getStart();
    fEnd = p.getEnd();
    
    setToDefaultFormat();
    setOpaque(true);
    
    String toolTip = String.format("<html>rank: %d<br>score: %.4f</html>", id, score);
    setToolTipText(toolTip);
  }

  public double getScore() {
    return score;
  }

  public int getId() {
    return id;
  }
  
  public Phrase getPhrase() {
    return new Phrase(this.getText(),fStart,fEnd,score);
  }

  public void setFormat(Format newFormat) {
    formats.push(currentFormat);
    
    if(newFormat.fg == null)
      newFormat.fg = currentFormat.fg;
    if(newFormat.bg == null)
      newFormat.bg = currentFormat.bg;
    if(newFormat.border == null)
      newFormat.border = currentFormat.border;
    
    setFormatHelper(newFormat);
  }

  private void setFormatHelper(Format f) {
    currentFormat = f;
    setForeground(f.fg);
    setBackground(f.bg);
    setBorder(f.border);
  }

  public static class Format {
    public Color fg = null;
    public Color bg = null;
    public Border border = null;
  }

  public void setToDefaultFormat() {
    formats = new Stack<Format>();
    setFormatHelper(DEFAULT_FORMAT);
  }
  
  public void revertToLastFormat() {
    if(formats.size() == 0) 
      return;
    else
      setFormatHelper(formats.pop());
  }

  private static final long serialVersionUID = -4250430096242892222L;
}
