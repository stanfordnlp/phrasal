package mt.visualize.phrase;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

public class TranslationLayout {

  //This should have a Coverage set and some other shit to build
  //the panel with a gridbag layout
  private final Translation translation;
  private final PathModel pathModel;
  private final int numOptions;
  private final List<VisualPhrase> labels;
  private int optionsApplied = 0;
  private JPanel panel = null;
  private List<BitSet> coverages;
  private boolean RIGHT_TO_LEFT = false;
  
  //Graphical members
  private static final LineBorder cellBorder = new LineBorder(Color.BLACK);

  public TranslationLayout(Translation t, boolean rightToLeft, PathModel m) {
    translation = t;
    RIGHT_TO_LEFT = rightToLeft;
    pathModel = m;
    numOptions = t.numPhrases();
    labels = new ArrayList<VisualPhrase>();
  }

  public boolean createLayout(int numOptionRows) {
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
      String token = st.nextToken().intern();
      JLabel label = new JLabel(token);
      GridBagConstraints c = new GridBagConstraints();
      c.fill = GridBagConstraints.HORIZONTAL;
      c.gridx = curCol;
      c.gridy = 0;
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
          VisualPhrase label = buildLabel(phrase,optionsApplied + 1);
          GridBagConstraints c = buildConstraints(phrase,i,numColumns,sourceWordsCovered);
          
          panel.add(label,c);

          optionsApplied++;
          setCoverage(phrase,bitSet);
          cellsFilled += sourceWordsCovered;
          labels.add(label);
          break;
        }
      }
      if(cellsFilled >= optionGridSize)
        break;
    }

    return true;
  }
  
  private VisualPhrase buildLabel(Phrase phrase, int rank) {
    VisualPhrase label = new VisualPhrase(" " + phrase.getPhrase(), phrase.getScore(), rank);
    
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
  
  private class LabelMouseHandler extends MouseAdapter {
    @Override
    public void mouseClicked(MouseEvent e) {
      VisualPhrase label = (VisualPhrase) e.getComponent();
      pathModel.addClickToStream(label);
    }
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
  
  public List<VisualPhrase> getLabels() {
    return Collections.unmodifiableList(labels);
  }

}
