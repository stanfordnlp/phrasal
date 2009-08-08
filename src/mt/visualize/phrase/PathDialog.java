package mt.visualize.phrase;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileFilter;

public class PathDialog extends JFrame {

  private static final int DEFAULT_WIDTH = 170;
  private static final int DEFAULT_HEIGHT = 300;
  private static final int BUTTON_WIDTH = 80;
  private static final int BUTTON_PANEL_HEIGHT = 60;

  //For radio button
  private static final String ACTION_DELIM = "#";
  private static final String ON = "O";
  private static final String OFF = "F";

  private final boolean VERBOSE;
  private final AnalysisDialog parent;
  private final PhraseController controller;
  private int currentTranslationId = 0;
  private int lastPathRow = 0;
  private String recordingPathName;
  private boolean isRecording = false;
  private StringBuilder recordingTranslation = null;
  private Stack<VisualPhrase> recordingPath;
  private Stack<Integer> translationSplits;

  private JSplitPane mainSplitPane = null; 

  private JScrollPane pathsScrollPane = null;

  private JPanel pathsPanel = null;

  private JPanel buttonPanel = null;

  private JButton newPathButton = null;

  private JButton savePathButton = null;

  private JButton loadPathButton = null;

  private JButton finishPathButton = null;

  private JTextField userInputTextField = null;

  private JFileChooser fileChooser = null;

  private final Map<Integer,Map<String,PathComponents>> pathMap;


  //Setup the font for the kill button
  private static final Font killBoxFont;
  static {
    Font f = new Font(null); //Grab the defaults
    Map fontProps = f.getAttributes();
    fontProps.put(TextAttribute.FAMILY, "SansSerif");
    fontProps.put(TextAttribute.SIZE, 14.0);
    fontProps.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
    fontProps.put(TextAttribute.FOREGROUND, Color.RED);
    killBoxFont = new Font(fontProps);
  }

  public PathDialog(AnalysisDialog parent, int currentTranslationId) {
    super();

    this.parent = parent;
    controller = PhraseController.getInstance();
    VERBOSE = controller.getVerbose();

    pathMap = new HashMap<Integer, Map<String,PathComponents>>();

    this.setTitle("Translation Paths");
    this.setSize(new Dimension(DEFAULT_WIDTH,DEFAULT_HEIGHT));
    this.setMinimumSize(new Dimension(DEFAULT_WIDTH,DEFAULT_HEIGHT));
    this.setContentPane(getMainSplitPane());

    this.setCurrentTranslationId(currentTranslationId);

    controller.addClickStreamListener(clickStreamListener);
  }

  public void freeResources() {
    controller.removeClickStreamListener(clickStreamListener);
  }

  private ClickEventListener clickStreamListener = new ClickEventListener() {
    @Override
    public void handleClickEvent(ClickEvent e) {
      VisualPhrase vp = (VisualPhrase) e.getSource();

      if(isRecording && recordingPath.size() == 0) {
        recordingTranslation.append(vp.getText() + ' ');
        parent.addTranslationToLayout(currentTranslationId, recordingPathName);
        parent.updateTranslationOnLayout(currentTranslationId, recordingPathName, recordingTranslation.toString());

        recordingPath.push(vp);
        translationSplits.push(0);

      } else if(isRecording && vp == recordingPath.peek()) {
        recordingPath.pop();
        if(recordingPath.size() == 0) {
          recordingTranslation = new StringBuilder();
          translationSplits.clear();
          parent.removeTranslationFromLayout(recordingPathName);
          return;
        } else {
          recordingTranslation.delete(translationSplits.pop(), recordingTranslation.length());
          parent.updateTranslationOnLayout(currentTranslationId, recordingPathName, recordingTranslation.toString());
        }
      } else if(isRecording && !recordingPath.contains(vp)){
        translationSplits.push(recordingTranslation.length());
        recordingTranslation.append(vp.getText() + ' ');
        recordingPath.push(vp);
        parent.updateTranslationOnLayout(currentTranslationId, recordingPathName, recordingTranslation.toString());
      }
    }
  };

  private class PathComponents {
    public int row = -1;
    public JRadioButton on = null;
    public JRadioButton off = null;
    public NamedLabel kill = null;
    public JLabel label = null;
  }

  private JFileChooser getFileChooser() {
    if(fileChooser == null) {
      fileChooser = new JFileChooser();
      fileChooser.setFileFilter(new XmlFilter());
      fileChooser.setAcceptAllFileFilterUsed(false);
    }
    return fileChooser;
  }

  private class XmlFilter extends FileFilter {
    @Override
    public boolean accept(File f) {
      return (f.isDirectory() || f.getName().matches(".*\\.xml$"));
    }
    @Override
    public String getDescription() {
      return "XML File";
    }
  }

  private JSplitPane getMainSplitPane() {
    if(mainSplitPane == null) {
      mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,getPathsScrollPane(), getButtonPanel());
      mainSplitPane.setDoubleBuffered(true);
      mainSplitPane.setResizeWeight(1.0);
    }
    return mainSplitPane;
  }

  private JScrollPane getPathsScrollPane() {
    if(pathsScrollPane == null) {
      pathsScrollPane = new JScrollPane(getPathsPanel());
      pathsScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    }
    return pathsScrollPane;
  }

  private JPanel getPathsPanel() {
    if(pathsPanel == null) {
      pathsPanel = new JPanel(new GridBagLayout());
    }
    return pathsPanel;
  }

  private JPanel getButtonPanel() {
    if(buttonPanel == null) {
      buttonPanel = new JPanel(new GridBagLayout());
      buttonPanel.setPreferredSize(new Dimension(DEFAULT_WIDTH,BUTTON_PANEL_HEIGHT));
      GridBagConstraints c = new GridBagConstraints();
      c.fill = GridBagConstraints.HORIZONTAL;
      c.insets = new Insets(2,2,2,2);
      c.gridx = 0;
      c.gridy = 0;
      c.gridwidth = 2;
      buttonPanel.add(getUserInputTextField(),c);
      c.gridwidth = 1;
      c.gridy = 1;
      buttonPanel.add(getNewPathButton(),c);
      c.gridx = 1;
      buttonPanel.add(getFinishPathButton(),c);
      c.gridx = 0;
      c.gridy = 2;
      buttonPanel.add(getSavePathButton(),c);
      c.gridx = 1;
      buttonPanel.add(getLoadPathButton(),c);
    }
    return buttonPanel;
  }

  private JButton getNewPathButton() {
    if(newPathButton == null) {
      newPathButton = new JButton("New");
      newPathButton.setMinimumSize(new Dimension(BUTTON_WIDTH, newPathButton.getHeight()));
      newPathButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          toggleInputField();
        }
      });
    }
    return newPathButton;
  }

  private boolean inputFieldIsVisible = false;

  private void toggleInputField() {
    int dividerLoc = getMainSplitPane().getDividerLocation();

    getUserInputTextField().setVisible(!inputFieldIsVisible);

    if(!isRecording) {
      getNewPathButton().setEnabled(inputFieldIsVisible);
      getSavePathButton().setEnabled(inputFieldIsVisible);
      getLoadPathButton().setEnabled(inputFieldIsVisible);
      getFinishPathButton().setEnabled(inputFieldIsVisible);
    }

    if(inputFieldIsVisible) {
      getUserInputTextField().setText("");
      dividerLoc += 30;
      getMainSplitPane().setDividerLocation(dividerLoc);
    } else {
      dividerLoc -= 30;
      getMainSplitPane().setDividerLocation(dividerLoc);
      userInputTextField.setEditable(true);
      getUserInputTextField().getCaret().setVisible(true);
      getUserInputTextField().requestFocusInWindow();
    }

    inputFieldIsVisible = !inputFieldIsVisible;    
  }

  private JButton getSavePathButton() {
    if(savePathButton == null) {
      savePathButton = new JButton("Save");
      savePathButton.setEnabled(controller.isFileIOEnabled());
      savePathButton.setMinimumSize(new Dimension(BUTTON_WIDTH, savePathButton.getHeight()));
      savePathButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          int returned = getFileChooser().showSaveDialog(PathDialog.this);
          if(returned == JFileChooser.APPROVE_OPTION) {
            File f = getFileChooser().getSelectedFile();
            if(!controller.savePaths(f)) {
              PhraseGUI gui = PhraseGUI.getInstance();
              gui.setStatusMessage("Failure saving paths to file");
            }
          }
        }
      });
    }
    return savePathButton;
  }

  private JButton getLoadPathButton() {
    if(loadPathButton == null) {
      loadPathButton = new JButton("Load");
      loadPathButton.setEnabled(controller.isFileIOEnabled());
      loadPathButton.setMinimumSize(new Dimension(BUTTON_WIDTH, loadPathButton.getHeight()));
      loadPathButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          int returned = getFileChooser().showOpenDialog(PathDialog.this);
          if(returned == JFileChooser.APPROVE_OPTION) {
            File f = getFileChooser().getSelectedFile();
            if(controller.loadPaths(f))
              setCurrentTranslationId(currentTranslationId);
            else {
              PhraseGUI gui = PhraseGUI.getInstance();
              gui.setStatusMessage("Could not load paths from file");
            }
          }
        }
      });
    }
    return loadPathButton;
  }

  private JButton getFinishPathButton() {
    if(finishPathButton == null) {
      finishPathButton = new JButton("Finish");
      finishPathButton.setEnabled(false);
      finishPathButton.setMinimumSize(new Dimension(BUTTON_WIDTH, finishPathButton.getHeight()));
      finishPathButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          toggleRecording(false,recordingPathName);
        }
      });
    }
    return finishPathButton;
  }

  private JTextField getUserInputTextField() {
    if(userInputTextField == null) {
      userInputTextField = new JTextField();
      userInputTextField.setVisible(false);
      userInputTextField.setMinimumSize(new Dimension(DEFAULT_WIDTH - 50, 30));
      userInputTextField.setMaximumSize(new Dimension(DEFAULT_WIDTH - 50, 30));
      userInputTextField.addKeyListener(new UserEnterKeyListener());
    }
    return userInputTextField;
  }

  private class UserEnterKeyListener extends KeyAdapter {
    @Override
    public void keyPressed(KeyEvent e) {
      if(e.getKeyCode() == KeyEvent.VK_ENTER) {
        String newName = getUserInputTextField().getText().trim();
        if(!newName.isEmpty())
          createNewPath(newName);
        toggleInputField();
      }
    }
  }

  private void createNewPath(String name) {
    if(controller.addPath(currentTranslationId, name)) {
      addPathToPanel(name,false);
      toggleRecording(true, name);
      return;
    }

    if(VERBOSE)
      System.err.printf("%s: Unable to create path (%s). Does the path name already exist?\n", this.getClass().getName(), name);
  }

  private void toggleRecording(boolean isOn, String name) {    
    if(isOn) {
      recordingTranslation = new StringBuilder();
      recordingPath = new Stack<VisualPhrase>();
      translationSplits = new Stack<Integer>();
    } else {
      controller.finishPath(currentTranslationId, name);
      controller.setTranslationForPath(currentTranslationId, name, recordingTranslation.toString());
    }

    parent.toggleRecording(isOn,name);

    getNewPathButton().setEnabled(!isOn);
    getFinishPathButton().setEnabled(isOn);
    if(controller.isFileIOEnabled()) {
      getSavePathButton().setEnabled(!isOn);
      getLoadPathButton().setEnabled(!isOn);
    }

    PathComponents comps = pathMap.get(currentTranslationId).get(name);
    if(comps != null) {
      comps.off.setEnabled(!isOn);
      comps.off.setSelected(isOn);
      comps.on.setEnabled(!isOn);
      comps.on.setSelected(!isOn);
    }

    recordingPathName = (isOn) ? name : null;
    isRecording = isOn;
  }

  private void togglePath(String name, String action) {
    if(pathMap.get(currentTranslationId) != null) {
      PathComponents comps = pathMap.get(currentTranslationId).get(name);
      if(comps != null) {
        boolean isOn = (action.equals(ON));
        comps.on.setSelected(isOn);
        comps.off.setSelected(!isOn);
        parent.togglePath(isOn, name);
        controller.setPathState(isOn, currentTranslationId, name);
        return;
      }
    } 
    if(VERBOSE)
      System.err.printf("%s: Unable to toggle path (%s,%s)\n", this.getClass().getName(), name, action);
  }

  private boolean addPathToPanel(String name, boolean isEnabled) {
    PathComponents comps = (pathMap.get(currentTranslationId) != null && pathMap.get(currentTranslationId).containsKey(name)) ? 
        pathMap.get(currentTranslationId).get(name) : new PathComponents();

        comps.row = lastPathRow;

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(1,1,1,1);
        c.gridy = lastPathRow++;

        name = name.intern();

        c.gridx = 0;
        comps.on = new JRadioButton();
        comps.on.setActionCommand(name + ACTION_DELIM + ON);
        comps.on.setSelected(isEnabled);
        comps.on.setToolTipText("<html>Enable " + name + "</html>");
        comps.on.addActionListener(new RadioButtonListener());
        getPathsPanel().add(comps.on,c);

        (c.gridx)++;
        comps.off = new JRadioButton();
        comps.off.setActionCommand(name + ACTION_DELIM + OFF);
        comps.off.setSelected(!isEnabled);
        comps.off.setToolTipText("<html>Disable " + name + "</html>");
        comps.off.addActionListener(new RadioButtonListener());
        getPathsPanel().add(comps.off,c);

        (c.gridx)++;
        comps.kill = new NamedLabel(name);
        comps.kill.setFont(killBoxFont);
        comps.kill.setText("X");
        comps.kill.setBorder(new LineBorder(Color.LIGHT_GRAY,1));
        comps.kill.setToolTipText("<html>Delete this path</html>");
        comps.kill.addMouseListener(new KillButtonListener());
        getPathsPanel().add(comps.kill,c);

        (c.gridx)++;
        c.insets = new Insets(1,4,1,1);
        comps.label = new JLabel(name);
        getPathsPanel().add(comps.label,c);

        if(pathMap.get(currentTranslationId) == null) {
          Map<String,PathComponents> compsMap = new HashMap<String,PathComponents>();
          compsMap.put(name,comps);
          pathMap.put(currentTranslationId, compsMap);
        } else {
          Map<String,PathComponents> compsMap = pathMap.get(currentTranslationId);
          compsMap.put(name,comps);
        }

        return true;
  }

  private class RadioButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      String[] toks = e.getActionCommand().split(ACTION_DELIM);
      if(toks.length != 2 && VERBOSE)
        System.err.printf("%s: Bad radio button command received %s\n", this.getClass().getName(), e.getActionCommand());
      String name = toks[0].trim();
      String cmd = toks[1].trim();
      togglePath(name, cmd);
    }
  }

  private class KillButtonListener extends MouseAdapter {
    @Override
    public void mouseClicked(MouseEvent e) {
      NamedLabel l = (NamedLabel) e.getComponent();
      String pathName = l.getId();
      deletePath(pathName);
    }
    @Override
    public void mouseEntered(MouseEvent e) {
      NamedLabel l = (NamedLabel) e.getComponent();
      l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    @Override 
    public void mouseExited(MouseEvent e) {
      NamedLabel l = (NamedLabel) e.getComponent();
      l.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }
  }

  private void deletePath(String name) {
    togglePath(name, OFF);
    parent.removeTranslationFromLayout(name);
    controller.deletePath(currentTranslationId, name);
    if(pathMap.get(currentTranslationId) != null)
      pathMap.get(currentTranslationId).remove(name);

    setCurrentTranslationId(currentTranslationId);
  }

  public void setCurrentTranslationId(int id) {
    currentTranslationId = id;

    pathsPanel = new JPanel(new GridBagLayout());
    lastPathRow = 0;

    if(controller.getPathNames(id) != null)
      for(String name : controller.getPathNames(id))
        addPathToPanel(name, controller.isEnabled(id, name));

    getPathsScrollPane().setViewportView(this.getPathsPanel());
  }


  private static final long serialVersionUID = 6906498324826864423L;
}
