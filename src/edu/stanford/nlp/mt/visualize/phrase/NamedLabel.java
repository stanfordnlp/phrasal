package edu.stanford.nlp.mt.visualize.phrase;

import javax.swing.JLabel;

/**
 * 
 * @author Spence Green
 */
public class NamedLabel extends JLabel {
  private final String id;
  public NamedLabel(String id) {
    super();
    this.id = id;
  }

  public String getId() {
    return id;
  }

  private static final long serialVersionUID = -5388372308097145326L;
}
