package mt.visualize.phrase;

import java.awt.Color;
import java.util.Stack;

import javax.swing.JLabel;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;

public class VisualPhrase extends JLabel {

  private double score = 0.0;
  private int id = -1;

  private Stack<Format> formats;
  private Format currentFormat = null;

  private static Format DEFAULT_FORMAT;
  static {
    DEFAULT_FORMAT = new Format();
    DEFAULT_FORMAT.fg = Color.BLACK;
    DEFAULT_FORMAT.bg = Color.WHITE;
    DEFAULT_FORMAT.border = new LineBorder(Color.BLACK);
  }

  public VisualPhrase(String text) {
    super(text);
    setToDefaultFormat();
  }

  public VisualPhrase(String text, double score) {
    this(text);
    this.score = score;
  }

  public VisualPhrase(String text, double score, int id) {
    this(text, score);
    this.id = id;
  }

  public double getScore() {
    return score;
  }

  public int getId() {
    return id;
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
