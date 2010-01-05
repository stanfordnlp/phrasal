package edu.stanford.nlp.mt.visualize.phrase;

import javax.swing.GroupLayout;
import javax.swing.JPanel;
import javax.swing.JFrame;
import javax.swing.JSplitPane;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.JLabel;
import javax.swing.JSeparator;

/**
 * 
 * @author Spence Green
 */
public class AnalysisDialog extends JFrame {

  private static final int DEFAULT_WIDTH = 800;
  private static final int DEFAULT_HEIGHT = 600;
  private static final int NAV_HEIGHT = 30;
  private static int currentTranslationId = 0;
  private static int minTranslationId = Integer.MIN_VALUE;
  private static boolean VERBOSE = false;
  private final PhraseController controller;
  private List<Color> heatMapPalette;
  private static int scoreHalfRange = 0;
  private static int MAX_PATHS = 0;

  private JSplitPane mainSplitPane = null; 

  private JScrollPane translationScrollPane = null;

  private JPanel navPanel = null;

  private GroupLayout navLayout = null;

  private JButton navPrevButton = null;

  private JButton navNextButton = null;

  private JTextField transIDTextField = null;

  private JLabel navStatusBar = null;

  private JSeparator navLeftSeparator = null;

  private JSeparator navRightSeparator = null;

  private JPanel currentTranslationPanel = null;

  private JLabel navNumTranslationsLabel = null;

  private JButton heatMapButton = null;

  private JButton resetAnimationButton = null;

  private JButton pathButton = null;

  private PathDialog pathDialog = null;

  private List<VisualPhrase.Format> currentCell;
  private List<VisualPhrase.Format> previousCell;
  private boolean isRecording = false;
  private Stack<VisualPhrase> recordingPath = null;
  private String recordingPathName = null;

  public AnalysisDialog() {
    super();

    controller = PhraseController.getInstance();
    initialSetup();
  }

  public void initialSetup() {
    VERBOSE = controller.getVerbose();
    MAX_PATHS = controller.getMaxPaths();

    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    modelBuilderThread.execute();
    guiUpdaterThread.execute();

    this.setTitle("Phrase Analysis");
    this.setPreferredSize(new Dimension(DEFAULT_WIDTH,DEFAULT_HEIGHT));
    this.setMinimumSize(new Dimension(DEFAULT_WIDTH,DEFAULT_HEIGHT));  

    setupPathColors();
  }

  public void finalSetup() {
    getNavNumTranslationsLabel().setText(String.format("of %d", controller.getNumTranslationLayouts()));
    controller.addClickEventListener(clickStreamListener);
  }

  public void setupPathColors() {
    currentCell = new ArrayList<VisualPhrase.Format>(MAX_PATHS);
    VisualPhrase.Format f = new VisualPhrase.Format();
    f.bg = new Color(92,162,216);
    f.fg = Color.WHITE;
    currentCell.add(f);
    f = new VisualPhrase.Format();
    f.bg = new Color(241,175,0);
    f.fg = Color.WHITE;
    currentCell.add(f);
    f = new VisualPhrase.Format();
    f.bg = new Color(44,180,49);
    f.fg = Color.WHITE;
    currentCell.add(f);
    f= new VisualPhrase.Format();
    f.bg = new Color(233,113,23);
    f.fg = Color.WHITE;
    currentCell.add(f);
    f= new VisualPhrase.Format();
    f.bg = new Color(45,75,155);
    f.fg = Color.WHITE;
    currentCell.add(f);

    previousCell = new ArrayList<VisualPhrase.Format>(MAX_PATHS);
    f= new VisualPhrase.Format();
    f.bg = new Color(45,75,155);
    f.fg = Color.WHITE;
    previousCell.add(f);
    f= new VisualPhrase.Format();
    f.bg = new Color(233,113,23);
    f.fg = Color.WHITE;
    previousCell.add(f);
    f= new VisualPhrase.Format();
    f.bg = new Color(0,120,73);
    f.fg = Color.WHITE;
    previousCell.add(f);
    f= new VisualPhrase.Format();
    f.bg = new Color(207,2,38);
    f.fg = Color.WHITE;
    previousCell.add(f);
    f= new VisualPhrase.Format();
    f.bg = new Color(53,58,144);
    f.fg = Color.WHITE;
    previousCell.add(f);
  }

  public void freeResources() {
    if(pathDialog != null) {
      pathDialog.freeResources();
      pathDialog.setVisible(false);
      pathDialog.dispose();
      pathDialog = null;
    }

    if(!modelBuilderThread.isDone() && !modelBuilderThread.cancel(true))
      System.err.printf("%s: Could not kill model builder thread\n",this.getClass().getName());
    if(!guiUpdaterThread.isDone() && !guiUpdaterThread.cancel(true))
      System.err.printf("%s: Could not kill gui updater thread\n",this.getClass().getName());        

    controller.removeClickEventListener(clickStreamListener);
  }

  private SwingWorker<Boolean,Void> modelBuilderThread = 
    new SwingWorker<Boolean,Void>() {
    @Override
    protected Boolean doInBackground() throws Exception {
      if(controller.buildModel()) {
        scoreHalfRange = controller.getScoreHalfRange();
        heatMapPalette = createPalette((2 * scoreHalfRange) + 1);
        return true;
      }
      return false;
    }
    @Override
    protected void done() {
      try {
        if(get())
          getHeatMapButton().setEnabled(true);
        else {
          PhraseGUI gui = PhraseGUI.getInstance();
          gui.setStatusMessage("Failed to build model");
          guiUpdaterThread.cancel(true);
          setVisible(false);
        }
      } catch (InterruptedException e) {
        System.err.println("Model builder thread interrupted");
        e.printStackTrace();
      } catch (ExecutionException e) {
        System.err.println("Model builder thread execution problem");
        e.printStackTrace();
      } catch (CancellationException e) {
        System.err.println("Model builder thread cancelled. Final setup incomplete");
      }
    }
  };

  private SwingWorker<Void,Integer> guiUpdaterThread = 
    new SwingWorker<Void,Integer>() {

    private boolean initialized = false;

    @Override
    protected Void doInBackground() throws Exception {
      do {
        Thread.sleep(100);
        publish(controller.getNumTranslationLayouts());
      } while(!controller.modelIsBuilt());
      return null;
    }
    @Override
    protected void process(List<Integer> updates) {
      int numTranslations = updates.get(updates.size() - 1);

      if(numTranslations != 0) {
        getNavNumTranslationsLabel().setText(String.format("of %d", numTranslations));
        if(!initialized) {
          setCurrentTranslation(controller.getMinTranslationId());
          setContentPane(getMainSplitPane());
          initialized = true;
          setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        } 
        validate();
      }
    }
    @Override
    protected void done() {
      finalSetup();
    }
  };

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

  private JSplitPane getMainSplitPane() {
    if (mainSplitPane == null) {
      mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,getTranslationScrollPane(),getNavPanel());      
      mainSplitPane.setDoubleBuffered(true);
      mainSplitPane.setResizeWeight(1.0); //Fix the nav bar during resizing
    }
    return mainSplitPane;
  }

  private JScrollPane getTranslationScrollPane() {
    if (translationScrollPane == null) {
      translationScrollPane = new JScrollPane(currentTranslationPanel);
      translationScrollPane.setPreferredSize(new Dimension(DEFAULT_WIDTH,DEFAULT_HEIGHT-NAV_HEIGHT));
      translationScrollPane.setMinimumSize(new Dimension(DEFAULT_WIDTH,DEFAULT_HEIGHT-NAV_HEIGHT));
    }
    return translationScrollPane;
  }

  private JPanel getNavPanel() {
    if (navPanel == null) {
      navPanel = new JPanel();
      if(navLayout == null)
        navLayout = new GroupLayout(navPanel);
      navPanel.setLayout(navLayout);
      navPanel.setPreferredSize(new Dimension(DEFAULT_WIDTH,NAV_HEIGHT));

      navLayout.setAutoCreateGaps(true);
      navLayout.setAutoCreateContainerGaps(true);
      navLayout.setHorizontalGroup(navLayout.createSequentialGroup()
          .addGroup(navLayout.createSequentialGroup()
              .addComponent(this.getNavStatusBar())
          )
          .addComponent(this.getNavLeftSeparator())
          .addGroup(navLayout.createSequentialGroup()
              .addComponent(this.getNavPrevButton())
              .addComponent(this.getTransIDTextField())
              .addComponent(this.getNavNumTranslationsLabel())
              .addComponent(this.getNavNextButton())
          )
          .addComponent(this.getNavRightSeparator())
          .addGroup(navLayout.createSequentialGroup()
              .addComponent(this.getPathButton())
              .addComponent(this.getHeatMapButton())
              .addComponent(this.getResetAnimationButton())
          )
      );
      navLayout.setVerticalGroup(navLayout.createParallelGroup()
          .addGroup(navLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
              .addComponent(this.getNavStatusBar())
          )
          .addComponent(this.getNavLeftSeparator())
          .addGroup(navLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
              .addComponent(this.getNavPrevButton())
              .addComponent(this.getTransIDTextField())
              .addComponent(this.getNavNumTranslationsLabel())
              .addComponent(this.getNavNextButton())
          )
          .addComponent(this.getNavRightSeparator())
          .addGroup(navLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
              .addComponent(this.getPathButton())
              .addComponent(this.getHeatMapButton())
              .addComponent(this.getResetAnimationButton())
          )
      );

    }
    return navPanel;
  }

  private JButton getNavPrevButton() {
    if (navPrevButton == null) {
      navPrevButton = new JButton();
      navPrevButton.setText("<<");
      navPrevButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          int newValue = currentTranslationId - 1;
          if(newValue >= minTranslationId)
            setCurrentTranslation(newValue);
        }
      });
    }
    return navPrevButton;
  }

  private JButton getNavNextButton() {
    if (navNextButton == null) {
      navNextButton = new JButton();
      navNextButton.setText(">>");
      navNextButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          int newValue = currentTranslationId + 1;
          if(newValue <= minTranslationId + controller.getNumTranslationLayouts())
            setCurrentTranslation(newValue);
        }
      });
    }
    return navNextButton;
  }

  private JTextField getTransIDTextField() {
    if (transIDTextField == null) {
      transIDTextField = new JTextField();
      transIDTextField.setPreferredSize(new Dimension(40,27));
      transIDTextField.setMaximumSize(new Dimension(40,27));
      transIDTextField.setMinimumSize(new Dimension(40,27));
      transIDTextField.setHorizontalAlignment(JTextField.CENTER);
      transIDTextField.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          String strNewValue = getTransIDTextField().getText().trim();
          if(strNewValue.matches("\\d+")) {
            int newValue = Integer.parseInt(strNewValue);
            if(newValue <= controller.getNumTranslationLayouts()) {
              setCurrentTranslation(newValue);
              return;
            }
          }
          getTransIDTextField().setText(Integer.toString(currentTranslationId));
        }
      });
    }
    return transIDTextField;
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

  private void setCurrentTranslation(int newId) {

    TranslationLayout currentLayout = controller.getTranslationLayout(currentTranslationId);
    TranslationLayout newLayout = controller.getTranslationLayout(newId);
    
    if(newLayout == null) {
      if(VERBOSE)
        System.err.printf("%s: Invalid translation id %d passed from interface\n", this.getClass().getName(), newId);
    } else {
      
      if(minTranslationId < 0)
        minTranslationId = newId;
      
      //Update text fields
      getTransIDTextField().setText(Integer.toString(newId - minTranslationId + 1));
      String newStatus = String.format("%d of %d options applied",
          newLayout.getNumOptionsApplied(),newLayout.getNumOptions());
      getNavStatusBar().setText(newStatus);

      //Remove translations from current layout
      if(controller.getPathNames(currentTranslationId) != null)
        for(String name : controller.getPathNames(currentTranslationId))
          removeTranslationFromLayout(currentLayout, name);

      //Add active sentences to new layout
      if(controller.getPathNames(newId) != null)
        for(String name : controller.getPathNames(newId))
          if(controller.isEnabled(newId, name))
            addTranslationToLayout(newLayout, newId, name);

      //Re-load the viewport
      getTranslationScrollPane().setViewportView(newLayout.getPanel());

      currentTranslationId = newId;

      getPathDialog().setCurrentTranslationId(currentTranslationId);
    }
  }

  private void removeTranslationFromLayout(TranslationLayout layout, String name) {
    if(layout == null) return;
    layout.removeTranslationRow(name);
    layout.getPanel().validate();
    layout.getPanel().repaint();
  }

  public void removeTranslationFromLayout(String name) {
    TranslationLayout currentLayout = controller.getTranslationLayout(currentTranslationId);
    removeTranslationFromLayout(currentLayout,name);
  }

  private void addTranslationToLayout(TranslationLayout layout, int translationId, String name) {
    if(layout == null) return;

    final int formatId = controller.getFormatId(translationId, name);
    String trans = controller.getTranslationFromPath(translationId, name);
    if(formatId != -1) {
      VisualPhrase.Format format = previousCell.get(formatId);
      layout.addTranslationRow(name, trans, format.bg);
    }
    layout.getPanel().validate();
    layout.getPanel().repaint();
  }

  public void addTranslationToLayout(int translationId, String name) {
    TranslationLayout currentLayout = controller.getTranslationLayout(currentTranslationId);
    addTranslationToLayout(currentLayout,translationId, name);
  }

  public void updateTranslationOnLayout(int translationId, String name, String trans) {
    TranslationLayout layout = controller.getTranslationLayout(translationId);
    if(layout != null) {
      layout.updateTranslationRow(name, trans);
      layout.getPanel().validate();
      layout.getPanel().repaint();
    }
  }

  private JButton getResetAnimationButton() {
    if(resetAnimationButton == null) {
      resetAnimationButton = new JButton("Reset");
      resetAnimationButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          TranslationLayout currentLayout = controller.getTranslationLayout(currentTranslationId);
          for(VisualPhrase label : currentLayout.getLabels())
            label.setToDefaultFormat();
        }
      });
    }
    return resetAnimationButton;
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
    TranslationLayout currentLayout = controller.getTranslationLayout(currentTranslationId);

    for(VisualPhrase vPhrase : currentLayout.getLabels()) {
      VisualPhrase.Format f = new VisualPhrase.Format();
      f.fg = Color.WHITE;

      int colorScore = controller.getScoreRank(vPhrase.getScore());
      colorScore = Math.abs(colorScore - scoreHalfRange);
      f.bg = heatMapPalette.get(colorScore);

      vPhrase.setFormat(f);
    }
  }

  private JButton getPathButton() {
    if(pathButton == null) {
      pathButton = new JButton("Paths...");
      pathButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          PathDialog dialog = getPathDialog();
          Point loc = getLocation();
          loc.translate(getWidth() - 20, 20);
          dialog.setLocation(loc);
          dialog.pack();
          dialog.setCurrentTranslationId(currentTranslationId);
          dialog.setVisible(true);
          getPathButton().setEnabled(false);
        }
      });
    }
    return pathButton;
  }

  private PathDialog getPathDialog() {
    if(pathDialog == null) {
      pathDialog = new PathDialog(this,currentTranslationId);
      pathDialog.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
      pathDialog.addWindowListener(new PathDialogListener());
    }
    return pathDialog;
  }

  private class PathDialogListener extends WindowAdapter {
    public void windowClosing(WindowEvent e) {
      getPathDialog().setVisible(false);
      getPathButton().setEnabled(true);
    }
  }

  public boolean togglePath(boolean isOn, String name) {    
    TranslationLayout currentLayout = controller.getTranslationLayout(currentTranslationId);
    Map<String,List<VisualPhrase>> paths = controller.getPaths(currentTranslationId);
    int formatId = controller.getFormatId(currentTranslationId, name);

    if(isOn && paths.containsKey(name) && formatId != -1) {
      addTranslationToLayout(currentLayout,currentTranslationId,name);
      for(VisualPhrase vp : paths.get(name))
        vp.setFormat(previousCell.get(formatId));
      return true;

    } else if(!isOn && paths.containsKey(name)) {
      removeTranslationFromLayout(currentLayout,name);
      for(VisualPhrase vp : paths.get(name))
        vp.revertToLastFormat();
      return true;
    }

    return false;
  }

  public void toggleNavigation(boolean isOn) {
    getNavPrevButton().setEnabled(isOn);
    getNavNextButton().setEnabled(isOn);
    getTransIDTextField().setEditable(isOn);
  }

  public void toggleRecording(boolean isOn, String name) {
    toggleNavigation(!isOn);
    recordingPathName = name;
    if(isOn && !isRecording) {
      recordingPath = new Stack<VisualPhrase>();
    } else if(!isOn && isRecording){
      if(recordingPath.size() != 0) {
        int formatId = controller.getFormatId(currentTranslationId, recordingPathName);
        if(formatId != -1) {
          boolean processedTop = false;
          while(recordingPath.size() != 0) {
            VisualPhrase vp = recordingPath.pop();
            vp.revertToLastFormat();
            if(processedTop) vp.revertToLastFormat();
            vp.setFormat(previousCell.get(formatId));
            processedTop = true;
          }
        }
      }
      recordingPath = null;
    }
    isRecording = isOn;
  }

  private ClickEventListener clickStreamListener = 
    new ClickEventListener() {
    @Override
    public void handleClickEvent(ClickEvent e) {
      VisualPhrase clickedPhrase = (VisualPhrase) e.getSource();
      if(isRecording) {
        VisualPhrase last = (recordingPath.size() != 0) ? recordingPath.peek() : null;
        if(clickedPhrase == last) {
          last.revertToLastFormat();
          recordingPath.pop();
          if(recordingPath.size() != 0)
            recordingPath.peek().revertToLastFormat();
        } else if(!recordingPath.contains(clickedPhrase)) {
          int formatId = controller.getFormatId(currentTranslationId, recordingPathName);
          if(formatId != -1) {
            if(last != null)
              last.setFormat(previousCell.get(formatId));
            clickedPhrase.setFormat(currentCell.get(formatId));
            recordingPath.push(clickedPhrase);
          }
        }
      }
    }
  };

  private static final long serialVersionUID = 2142404262837351493L;
}
