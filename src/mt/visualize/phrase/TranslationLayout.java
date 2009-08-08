package mt.visualize.phrase;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import edu.stanford.nlp.util.Pair;

public class TranslationLayout {

  private final Translation translation;
  private final int numOptions;
  private final List<VisualPhrase> vPhrases;
  private final Map<Phrase,VisualPhrase> vPhraseLookup;
  private final boolean RIGHT_TO_LEFT;
  private int optionsApplied = 0;
  private JPanel panel = null;
  private List<BitSet> coverages;

  private final int numColumns;
  private int numRows;

  private int numFullTranslationRows;
  private final LinkedList<Integer> unusedRows;
  private final Map<String,Pair<Integer,JLabel>> fullTranslations;

  public TranslationLayout(Translation t, boolean rightToLeft) {
    translation = t;
    numColumns = translation.getNumSourceWords();
    RIGHT_TO_LEFT = rightToLeft;
    numOptions = t.numPhrases();
    numFullTranslationRows = 0;
    vPhrases = new ArrayList<VisualPhrase>();
    vPhraseLookup = new HashMap<Phrase,VisualPhrase>();
    unusedRows = new LinkedList<Integer>();
    fullTranslations = new HashMap<String, Pair<Integer,JLabel>>();
  }

  public boolean createLayout(int numOptionRows) {
    numRows = numOptionRows + 1;
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

          VisualPhrase vp = new VisualPhrase(phrase, optionsApplied + 1);
          vp.addMouseListener(new LabelMouseHandler());

          GridBagConstraints c = buildConstraints(phrase,i,numColumns,sourceWordsCovered);

          panel.add(vp,c);

          optionsApplied++;
          setCoverage(phrase,bitSet);
          cellsFilled += sourceWordsCovered;
          vPhrases.add(vp);
          
          //The Phrase hash function is imperfect, but we can't do much about collisions
          vPhraseLookup.put(phrase, vp);
          break;
        }
      }
      if(cellsFilled >= optionGridSize)
        break;
    }

    return true;
  }

  private GridBagConstraints buildConstraints(Phrase phrase, int rowId, int maxColumn, int sourceWordsCovered) {
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridx = (RIGHT_TO_LEFT) ? maxColumn - phrase.getEnd() - 1 : phrase.getStart();
    c.gridy = rowId + 1;
    c.gridwidth = sourceWordsCovered;
    c.ipadx = 30;
    c.ipady = 20;
    return c;
  }

  private class LabelMouseHandler extends MouseAdapter {
    @Override
    public void mouseClicked(MouseEvent e) {
      VisualPhrase label = (VisualPhrase) e.getComponent();
      PhraseController controller = PhraseController.getInstance();
      controller.addClickToStream(label);
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

  public VisualPhrase lookupVisualPhrase(Phrase p) {
    return (vPhraseLookup.containsKey(p)) ? vPhraseLookup.get(p) : null;
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

  public int getNumColumns() {
    return numColumns;
  }

  public boolean addTranslationRow(String name, String trans, Color bgColor) {
    JLabel label = new JLabel(trans);
    label.setOpaque(true);
    label.setBackground(bgColor);
    label.setForeground(Color.WHITE);

    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridx = 0;
    c.ipady = 20;
    c.gridwidth = numColumns;

    if(unusedRows.isEmpty()) {
      ++numFullTranslationRows;
      c.gridy = numRows + numFullTranslationRows;
    } else {
      c.gridy = unusedRows.removeFirst();
    }

    if(panel != null)
      panel.add(label,c);
    fullTranslations.put(name, new Pair<Integer, JLabel>(c.gridy, label));

    return true;
  }

  public void updateTranslationRow(String name, String trans) {
    Pair<Integer,JLabel> labelPair = fullTranslations.get(name);
    if(labelPair != null)
      labelPair.second().setText(trans);
  }

  public boolean removeTranslationRow(String name) {
    Pair<Integer,JLabel> labelPair = fullTranslations.get(name);
    if(labelPair != null) {
      unusedRows.addFirst(labelPair.first());
      if(panel != null)
        panel.remove(labelPair.second());

      return true;
    }
    return false;
  }

  public List<VisualPhrase> getLabels() {
    return Collections.unmodifiableList(vPhrases);
  }

}
