package mt.visualize.phrase;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.*;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.*;

public class TranslationLayout {

  //This should have a Coverage set and some other shit to build
  //the panel with a gridbag layout
  private final Translation translation;
  private final int numOptions;
  private final List<JLabel> ranking;
  private int optionsApplied = 0;
  private JPanel panel = null;
  private List<BitSet> coverages;
  private boolean RIGHT_TO_LEFT = false;
  
  //Graphical members
  private static final LineBorder cellBorder = new LineBorder(Color.BLACK);

  public TranslationLayout(Translation t, boolean rightToLeft) {
    translation = t;
    RIGHT_TO_LEFT = rightToLeft;
    numOptions = t.numPhrases();
    ranking = new ArrayList<JLabel>();
  }

  public boolean doLayout(int numOptionRows) {
    final int numColumns = translation.getNumSourceWords();
    final int numRows = numOptionRows + 1;
    final int optionGridSize = numColumns * numRows;

    coverages = new ArrayList<BitSet>();
    for(int i = 0; i < numOptionRows; i++)
      coverages.add(new BitSet(numColumns));

    //Setup the panel dimensions
    panel = new JPanel(new GridBagLayout());
    panel.setBackground(Color.WHITE);
    panel.setBorder(new EmptyBorder(15,15,15,15));

    //Layout the source phrase
    StringTokenizer st = new StringTokenizer(translation.getSource());
    int curCol = (RIGHT_TO_LEFT) ? numColumns - 1 : 0;
    while(st.hasMoreTokens()) {
      String token = st.nextToken();
      JLabel label = new JLabel(token);
      GridBagConstraints c = new GridBagConstraints();
      c.fill = GridBagConstraints.HORIZONTAL;
      c.gridx = curCol;
      c.gridy = 0;
      
      //WSGDEBUG
      c.ipadx = 30;
      c.ipady = 20;
      
      panel.add(label,c);
      curCol = (RIGHT_TO_LEFT) ? curCol - 1 : curCol + 1;
    }

    Phrase phrase;
    int cellsFilled = 0;
    while((phrase = translation.getBestPhrase()) != null) {
      for(int i = 0; i < coverages.size(); i++) {
        BitSet bitSet = coverages.get(i);
        int sourceWordsCovered = phrase.getEnd() - phrase.getStart() + 1;

        if(testCoverage(phrase,bitSet)) {
          JLabel label = buildLabel(phrase,optionsApplied + 1);
          GridBagConstraints c = buildConstraints(phrase,i,numColumns,sourceWordsCovered);
          
          panel.add(label,c);

          optionsApplied++;
          setCoverage(phrase,bitSet);
          cellsFilled += sourceWordsCovered;
          ranking.add(label);
          break;
        }
      }
      if(cellsFilled >= optionGridSize)
        break;
    }

    return true;
  }
  
  private JLabel buildLabel(Phrase phrase, int rank) {
    JLabel label = new JLabel(" " + phrase.getPhrase());
    
    label.setOpaque(true);
    label.setBackground(Color.WHITE);
    label.setBorder(cellBorder);
    String toolTip = String.format("<html>rank: %d<br>score: %.4f</html>", rank, phrase.getScore());
    label.setToolTipText(toolTip);
    label.addMouseListener(new LabelMouseHandler());
    return label;
  }
  
  private GridBagConstraints buildConstraints(Phrase phrase, int rowId, int maxColumn, int sourceWordsCovered) {
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridx = (RIGHT_TO_LEFT) ? maxColumn - phrase.getEnd() - 1 : phrase.getStart();
    c.gridy = rowId + 1;
    c.gridwidth = sourceWordsCovered;
    
    //WSGDEBUG - Padding
    c.ipadx = 30;
    c.ipady = 20;
    return c;
  }
  

  private class LabelMouseHandler implements MouseListener {

    private boolean isHighlighted = false;
    
    public void mouseClicked(MouseEvent e) {
      JLabel label = (JLabel) e.getComponent();
      if(isHighlighted) {
        label.setForeground(Color.BLACK);
        label.setBackground(Color.WHITE);
        label.setBorder(cellBorder);
      }
      else {
        label.setForeground(Color.WHITE);
        label.setBackground(Color.GRAY);
        label.setBorder(new LineBorder(Color.LIGHT_GRAY));
      }
      isHighlighted = !isHighlighted;
    }

    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
    public void mousePressed(MouseEvent e) {}
    public void mouseReleased(MouseEvent e) {}
  }
  
  
  private boolean testCoverage(Phrase phrase, BitSet bs) {
    int sourceWordsCovered = phrase.getEnd() - phrase.getStart() + 1;
    if(sourceWordsCovered == 1)
      return (bs.get(phrase.getStart()) == false);
    else
      return (bs.get(phrase.getStart(), phrase.getEnd()+1).cardinality() == 0);
  }
  
  private void setCoverage(Phrase phrase, BitSet bs) {
    int sourceWordsCovered = phrase.getEnd() - phrase.getStart() + 1;
    if(sourceWordsCovered == 1)
      bs.set(phrase.getStart());
    else
      bs.set(phrase.getStart(), phrase.getEnd()+1);
  }
  
  
  public JPanel getPanel() {
    return panel;
  }
  
  public int getNumOptions() {
    return numOptions;
  }
  
  public int getNumOptionsApplied() {
    return optionsApplied;
  }
  
  public List<JLabel> getRanking() {
    return Collections.unmodifiableList(ranking);
  }

}
