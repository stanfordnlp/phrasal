package mt.visualize.phrase;

import javax.swing.GroupLayout;
import javax.swing.JPanel;
import javax.swing.JFrame;
import javax.swing.JSplitPane;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import javax.swing.border.LineBorder;

public class AnalysisDialog extends JFrame {

  private static final long serialVersionUID = 1L;

  private static int DEFAULT_WIDTH = 800;
  private static int DEFAULT_HEIGHT = 600;
  private static int NAV_HEIGHT = 30;
  private static int currentTranslationId = 0;
  private static boolean VERBOSE = false;
  private PhraseController controller = null;
  private static List<Color> heatMapPalette = null;
  private static int scoreHalfRange = 0;

  private JSplitPane jSplitPane = null; 

  private JScrollPane jScrollPane = null;

  private JPanel navPanel = null;

  private GroupLayout navLayout = null;

  private JButton navPrevButton = null;

  private JButton navNextButton = null;

  private JTextField navSentTextField = null;

  private JLabel navStatusBar = null;

  private JSeparator navLeftSeparator = null;

  private JSeparator navRightSeparator = null;

  private JPanel currentTranslationPanel = null;

  private JLabel navNumTranslationsLabel = null;

  private JButton heatMapButton = null;

  private JButton resetAnimationButton = null;

  /**
   * This is the default constructor
   */
  public AnalysisDialog() {
    super();

    controller = PhraseController.getInstance();
    VERBOSE = controller.getVerbose();    

    //Load the model asynchronously
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    modelBuilderThread.execute();
    guiUpdaterThread.execute();

    this.setTitle("Phrase Analysis");
    this.setSize(new Dimension(DEFAULT_WIDTH,DEFAULT_HEIGHT));
    this.setPreferredSize(new Dimension(DEFAULT_WIDTH,DEFAULT_HEIGHT));
  }

  private SwingWorker<Void,Void> modelBuilderThread = 
    new SwingWorker<Void,Void>() {
    @Override
    protected Void doInBackground() throws Exception {
      controller.buildModel();
      scoreHalfRange = controller.getScoreHalfRange();
      heatMapPalette = createPalette((2 * scoreHalfRange) + 1);
      return null;
    }
    @Override
    protected void done() {
      getHeatMapButton().setEnabled(true);
    }
  };

  private SwingWorker<Void,Integer> guiUpdaterThread = 
    new SwingWorker<Void,Integer>() {

    private boolean initialized = false;

    @Override
    protected Void doInBackground() throws Exception {
      while(!controller.modelIsBuilt()) {
        publish(controller.getNumTranslations());
      }
      publish(controller.getNumTranslations());
      return null;
    }
    @Override
    protected void process(List<Integer> updates) {
      int numTranslations = updates.get(updates.size() - 1);
      if(numTranslations != 0) {
        getNavNumTranslationsLabel().setText(String.format("of %d", numTranslations));
        if(!initialized) {
          setCurrentTranslation(1);
          setContentPane(getJSplitPane());
          initialized = true;
          setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        } 
        validate();
      }
    }
  };

  /**
   * This method initializes jSplitPane	
   * 	
   * @return javax.swing.JSplitPane	
   */
  private JSplitPane getJSplitPane() {
    if (jSplitPane == null) {
      jSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,getJScrollPane(),getNavPanel());      
      jSplitPane.setDoubleBuffered(true);
      jSplitPane.setResizeWeight(1.0); //Fix the nav bar during resizing
    }
    return jSplitPane;
  }

  /**
   * This method initializes jScrollPane	
   * 	
   * @return javax.swing.JScrollPane	
   */
  private JScrollPane getJScrollPane() {
    if (jScrollPane == null) {
      jScrollPane = new JScrollPane(currentTranslationPanel);
      jScrollPane.setPreferredSize(new Dimension(DEFAULT_WIDTH,DEFAULT_HEIGHT-NAV_HEIGHT));
      jScrollPane.setMinimumSize(new Dimension(DEFAULT_WIDTH,DEFAULT_HEIGHT-NAV_HEIGHT));
    }
    return jScrollPane;
  }



  /**
   * This method initializes jPanel	
   * 	
   * @return javax.swing.JPanel	
   */
  private JPanel getNavPanel() {
    if (navPanel == null) {
      navPanel = new JPanel();
      if(navLayout == null)
        navLayout = new GroupLayout(navPanel);
      navPanel.setLayout(navLayout);
      navPanel.setPreferredSize(new Dimension(DEFAULT_WIDTH,NAV_HEIGHT));

      //Setup the layout
      navLayout.setAutoCreateGaps(true);
      navLayout.setAutoCreateContainerGaps(true);
      navLayout.setHorizontalGroup(navLayout.createSequentialGroup()
          .addGroup(navLayout.createSequentialGroup()
              .addComponent(getNavStatusBar())
          )
          .addComponent(this.getNavLeftSeparator())
          .addGroup(navLayout.createSequentialGroup()
              .addComponent(this.getNavPrevButton())
              .addComponent(this.getNavSentTextField())
              .addComponent(this.getNavNumTranslationsLabel())
              .addComponent(this.getNavNextButton())
          )
          .addComponent(this.getNavRightSeparator())
          .addGroup(navLayout.createSequentialGroup()
              .addComponent(this.getHeatMapButton())
              .addComponent(this.getResetAnimationButton())
          )
      );
      navLayout.setVerticalGroup(navLayout.createParallelGroup()
          .addGroup(navLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
              .addComponent(getNavStatusBar())
          )
          .addComponent(this.getNavLeftSeparator())
          .addGroup(navLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
              .addComponent(this.getNavPrevButton())
              .addComponent(this.getNavSentTextField())
              .addComponent(this.getNavNumTranslationsLabel())
              .addComponent(this.getNavNextButton())
          )
          .addComponent(this.getNavRightSeparator())
          .addGroup(navLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
              .addComponent(this.getHeatMapButton())
              .addComponent(this.getResetAnimationButton())
          )
      );

    }
    return navPanel;
  }

  /**
   * This method initializes jButton	
   * 	
   * @return javax.swing.JButton	
   */
  private JButton getNavPrevButton() {
    if (navPrevButton == null) {
      navPrevButton = new JButton();
      navPrevButton.setText("<<");
      navPrevButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          int newValue = currentTranslationId - 1;
          if(newValue >= 1)
            setCurrentTranslation(newValue);
        }
      });
    }
    return navPrevButton;
  }

  /**
   * This method initializes jButton1	
   * 	
   * @return javax.swing.JButton	
   */
  private JButton getNavNextButton() {
    if (navNextButton == null) {
      navNextButton = new JButton();
      navNextButton.setText(">>");
      navNextButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          int newValue = currentTranslationId + 1;
          if(newValue <= controller.getNumTranslations())
            setCurrentTranslation(newValue);
        }
      });
    }
    return navNextButton;
  }

  /**
   * This method initializes jTextField	
   * 	
   * @return javax.swing.JTextField	
   */
  private JTextField getNavSentTextField() {
    if (navSentTextField == null) {
      navSentTextField = new JTextField();
      navSentTextField.setText("1");
      navSentTextField.setPreferredSize(new Dimension(40,27));
      navSentTextField.setMaximumSize(new Dimension(40,27));
      navSentTextField.setMinimumSize(new Dimension(40,27));
      navSentTextField.setHorizontalAlignment(JTextField.CENTER);
      navSentTextField.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          String strNewValue = getNavSentTextField().getText().trim();
          if(strNewValue.matches("\\d+")) {
            int newValue = Integer.parseInt(strNewValue);
            if(newValue <= controller.getNumTranslations()) {
              setCurrentTranslation(newValue);
              return;
            }
          }
          getNavSentTextField().setText(Integer.toString(currentTranslationId));
        }
      });
    }
    return navSentTextField;
  }

  private JLabel getNavStatusBar() {
    if(navStatusBar == null) {
      navStatusBar = new JLabel();
      navStatusBar.setHorizontalAlignment(JLabel.CENTER);
      navStatusBar.setPreferredSize(new Dimension(200,NAV_HEIGHT));
      navStatusBar.setMaximumSize(new Dimension(200,NAV_HEIGHT));
    }
    return navStatusBar;
  }

  private JSeparator getNavLeftSeparator() {
    if(navLeftSeparator == null)
      navLeftSeparator = new JSeparator(JSeparator.VERTICAL);
    return navLeftSeparator;
  }

  private JSeparator getNavRightSeparator() {
    if(navRightSeparator == null)
      navRightSeparator = new JSeparator(JSeparator.VERTICAL);
    return navRightSeparator;
  }

  private JLabel getNavNumTranslationsLabel() {
    if(navNumTranslationsLabel == null) {
      navNumTranslationsLabel = new JLabel();
      navNumTranslationsLabel.setHorizontalAlignment(JLabel.CENTER);
      navNumTranslationsLabel.setText("of 0");
    }
    return navNumTranslationsLabel;
  }

  private void setCurrentTranslation(int i) {

    TranslationLayout currentLayout = controller.getTranslation(i);

    if(currentLayout == null) {
      if(VERBOSE)
        System.err.printf("%s: Invalid translation id %d passed from interface\n", this.getClass().getName(), i);
    } else {
      //Update text fields
      getNavSentTextField().setText(Integer.toString(i));
      String newStatus = String.format("%d of %d options applied",
          currentLayout.getNumOptionsApplied(),currentLayout.getNumOptions());
      getNavStatusBar().setText(newStatus);

      //Re-load the viewport
      getJScrollPane().setViewportView(currentLayout.getPanel());

      currentTranslationId = i;
    }
  }

  private JButton getResetAnimationButton() {
    if(resetAnimationButton == null) {
      resetAnimationButton = new JButton("Reset");
      resetAnimationButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          resetLayout();
        }
      });
    }
    return resetAnimationButton;
  }

  //TODO Should blank JPanel in the background then render to screen
  private void resetLayout() {
    TranslationLayout currentLayout = controller.getTranslation(currentTranslationId);
    for(JLabel label : currentLayout.getLabels()) {
      label.setForeground(Color.BLACK);
      label.setBackground(Color.WHITE);
      label.setBorder(new LineBorder(Color.BLACK));
    }
  }

  private JButton getHeatMapButton() {
    if(heatMapButton == null) {
      heatMapButton = new JButton("Heat Map");
      heatMapButton.setEnabled(false);
      heatMapButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent arg0) {
          setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
          drawHeatMap();
          setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
      });
    }
    return heatMapButton;
  }

  private void drawHeatMap() {
    TranslationLayout currentLayout = controller.getTranslation(currentTranslationId);

    for(VisualPhrase vPhrase : currentLayout.getLabels()) {
      vPhrase.setForeground(Color.WHITE);

      int colorScore = controller.getScoreRank(vPhrase.getScore());
      colorScore = Math.abs(colorScore - scoreHalfRange);
      vPhrase.setBackground(heatMapPalette.get(colorScore));
    }
  }

  private List<Color> createFullPalette() {
    List<Color> palette = new ArrayList<Color>();
    for(float b = 1.0f; b >= 0.0f; b -= 0.025f)
      for(float s = 1.0f; s >= 0.95f; s -= 0.01f)
        for(float h = 0.0f; h <= 0.05f; h += 0.01f)
          palette.add(Color.getHSBColor(h, s, b));

    return palette;
  }

  private List<Color> createPalette(int numSamples) {
    List<Color> fullPalette = createFullPalette();

    int step = (int) ((double) fullPalette.size() / (double) numSamples);
    List<Color> palette = new ArrayList<Color>();
    for(int i = 0; i < fullPalette.size(); i += step)
      palette.add(fullPalette.get(i));

    Color black = Color.getHSBColor(0.0f, 0.0f, 0.0f);
    if(palette.size() < numSamples)
      palette.addAll(Collections.nCopies(numSamples - palette.size(), black));

    return palette;
  }

}
