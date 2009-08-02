package mt.visualize.phrase;

import javax.swing.JLabel;

public class VisualPhrase extends JLabel {
  
  private double score = 0.0;
  private int id = -1;
  
  public VisualPhrase(String text) {
    super(text);
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
  
  public double getId() {
    return id;
  }


  
  private static final long serialVersionUID = -4250430096242892222L;
}
