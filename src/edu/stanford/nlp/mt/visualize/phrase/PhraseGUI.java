package mt.visualize.phrase;

import java.io.File;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.WindowEvent;
import javax.swing.SwingUtilities;
import java.awt.Point;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JMenuItem;
import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.JFrame;
import javax.swing.JButton;
import javax.swing.GroupLayout;
import javax.swing.JFileChooser;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JSeparator;

import java.awt.Dimension;
import javax.swing.JTextField;

import java.awt.event.WindowAdapter;

/**
 * 
 * @author Spence Green
 */
public final class PhraseGUI {

  private JFrame mainFrame = null;

  private JPanel mainPanel = null;

  private JMenuBar mainMenuBar = null;

  private JMenu fileMenu = null;

  private JMenu optionsMenu = null;

  private JMenuItem exitMenuItem = null;

  private JMenuItem openOptionsDialogMenuItem = null;

  private JCheckBoxMenuItem rightLeftMenuItem = null;

  private JCheckBoxMenuItem normScoresMenuItem = null;

  private GroupLayout mainLayout = null;

  private JTextField sourceFileTextField = null;

  private JTextField optsFileTextField = null;

  private JButton sourceFileButton = null;

  private JButton optsFileButton = null;

  private JButton loadButton = null;

  private JLabel sourceLabel = null;

  private JLabel optsLabel = null;

  private JSeparator statusBarSeparator = null;

  private JLabel statusBar = null;

  private JFileChooser fileChooser = null;

  private AnalysisDialog analysisDialog = null;

  private OptionsDialog optionsDialog = null;

  //Application members
  private final PhraseController controller;
  private static PhraseGUI thisInstance = null;
  private static final int DEFAULT_WIDTH = 400;
  private static final int DEFAULT_HEIGHT = 500;

  public static PhraseGUI getInstance() {
    if(thisInstance == null)
      thisInstance = new PhraseGUI();
    return thisInstance;
  }

  private PhraseGUI() {
    controller = PhraseController.getInstance();    
  }

  private JFrame getMainFrame() {
    if (mainFrame == null) {
      mainFrame = new JFrame();
      mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      mainFrame.setJMenuBar(getMainMenuBar());
      mainFrame.setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
      mainFrame.setResizable(false);
      mainFrame.setContentPane(getMainPanel());
      mainFrame.setTitle("Phrase Viewer");
    }
    return mainFrame;
  }

  private JPanel getMainPanel() {
    if (mainPanel == null) {
      mainPanel = new JPanel();
      if(mainLayout == null)
        mainLayout = new GroupLayout(mainPanel);
      mainPanel.setLayout(mainLayout);

      mainLayout.setAutoCreateGaps(true);
      mainLayout.setAutoCreateContainerGaps(true);
      mainLayout.setHorizontalGroup(mainLayout.createSequentialGroup()
          .addGroup(mainLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
              .addComponent(this.getSourceLabel())
              .addComponent(this.getOptsLabel())
              .addComponent(this.getStatusBarSeparator())
              .addComponent(this.getStatusBar())
          )
          .addGroup(mainLayout.createParallelGroup()
              .addComponent(this.getSourceFileTextField())
              .addComponent(this.getOptsFileTextField())
          )
          .addGroup(mainLayout.createParallelGroup()
              .addComponent(this.getSourceFileButton())
              .addComponent(this.getOptsFileButton())
              .addComponent(this.getLoadButton())
          )
      );
      mainLayout.setVerticalGroup(mainLayout.createSequentialGroup()
          .addGroup(mainLayout.createParallelGroup()
              .addComponent(this.getSourceLabel())
              .addComponent(this.getSourceFileTextField())
              .addComponent(this.getSourceFileButton())
          )
          .addGroup(mainLayout.createParallelGroup()
              .addComponent(this.getOptsLabel())
              .addComponent(this.getOptsFileTextField())
              .addComponent(this.getOptsFileButton())
          )
          .addComponent(this.getStatusBarSeparator())
          .addGroup(mainLayout.createParallelGroup()
              .addComponent(this.getStatusBar())
              .addComponent(this.getLoadButton())
          )
      );
    }
    return mainPanel;
  }

  private JLabel getSourceLabel() {
    if(sourceLabel == null) {
      sourceLabel = new JLabel("Source");
    }
    return sourceLabel;
  }

  private JLabel getOptsLabel() {
    if(optsLabel == null) {
      optsLabel = new JLabel("Options");
    }
    return optsLabel;
  }

  private JMenuBar getMainMenuBar() {
    if (mainMenuBar == null) {
      mainMenuBar = new JMenuBar();
      mainMenuBar.add(getFileMenu());
      mainMenuBar.add(getOptionsMenu());
    }
    return mainMenuBar;
  }

  private JMenu getFileMenu() {
    if (fileMenu == null) {
      fileMenu = new JMenu();
      fileMenu.setText("File");
      fileMenu.add(getExitMenuItem());
    }
    return fileMenu;
  }

  private JMenu getOptionsMenu() {
    if (optionsMenu == null) {
      optionsMenu = new JMenu();
      optionsMenu.setText("Options");
      optionsMenu.add(getRightLeftMenuItem());
      optionsMenu.add(getNormScoresMenuItem());
      optionsMenu.add(new JSeparator());
      optionsMenu.add(getOpenOptionsDialogMenuItem());
    }
    return optionsMenu;
  }

  private JMenuItem getExitMenuItem() {
    if (exitMenuItem == null) {
      exitMenuItem = new JMenuItem();
      exitMenuItem.setText("Exit");
      exitMenuItem.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          System.exit(0);
        }
      });
    }
    return exitMenuItem;
  }

  private JCheckBoxMenuItem getRightLeftMenuItem() {
    if (rightLeftMenuItem == null) {
      rightLeftMenuItem = new JCheckBoxMenuItem("Right-To-Left Source");
      rightLeftMenuItem.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          boolean newState = getRightLeftMenuItem().isSelected();
          controller.setRightToLeft(newState);
          toggleLoadButton();
        }
      });
    }
    return rightLeftMenuItem;
  }

  private JCheckBoxMenuItem getNormScoresMenuItem() {
    if (normScoresMenuItem == null) {
      normScoresMenuItem = new JCheckBoxMenuItem("Normalize Phrase Scores");
      normScoresMenuItem.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          boolean newState = getNormScoresMenuItem().isSelected();
          controller.normalizePhraseScores(newState);
          toggleLoadButton();
        }
      });
    }
    return normScoresMenuItem;
  }

  private JMenuItem getOpenOptionsDialogMenuItem() {
    if(openOptionsDialogMenuItem == null) {
      openOptionsDialogMenuItem = new JMenuItem("Other Options");
      openOptionsDialogMenuItem.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          OptionsDialog dialog = getOptionsDialog();
          dialog.pack();
          Point loc = getMainFrame().getLocation();
          loc.translate(20, 20);
          dialog.setLocation(loc);
          dialog.setVisible(true);
        }
      });
    }
    return openOptionsDialogMenuItem;
  }


  public static void show() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        PhraseGUI application = PhraseGUI.getInstance();
        application.getMainFrame().pack();
        application.getMainFrame().setVisible(true);
      }
    });

  }

  private JTextField getSourceFileTextField() {
    if (sourceFileTextField == null) {
      sourceFileTextField  = (controller.getSourceFilePath() != null) ? 
          new JTextField(controller.getSourceFilePath()) : new JTextField();
          updateSourceFile();
          sourceFileTextField.setPreferredSize(new Dimension(350, 15));
          sourceFileTextField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
              updateSourceFile();
            }
          });
          sourceFileTextField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent arg0) {}
            @Override
            public void focusLost(FocusEvent arg0) {
              updateSourceFile();          
            }
          });
    }
    return sourceFileTextField;
  }

  private void updateSourceFile() {
    if(getSourceFileTextField().getText() == "")
      return;

    if(controller.setSourceFile(getSourceFileTextField().getText())) {
      setStatusMessage("Loaded source file");
      toggleLoadButton();
    }
    else
      setStatusMessage("Source file does not exist!");
  }

  private JTextField getOptsFileTextField() {
    if (optsFileTextField == null) {
      optsFileTextField = (controller.getOptsFilePath() != null) ?
          new JTextField(controller.getOptsFilePath()) : new JTextField();
          updateOptsFile();
          optsFileTextField.setPreferredSize(new Dimension(350, 15));
          optsFileTextField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
              updateOptsFile();
            }
          });
          optsFileTextField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {}
            @Override
            public void focusLost(FocusEvent e) {
              updateOptsFile();
            }
          });
    }
    return optsFileTextField;
  }

  private void updateOptsFile() {
    if(getOptsFileTextField().getText() == "")
      return;

    if(controller.setOptsFile(getOptsFileTextField().getText())) {
      setStatusMessage("Loaded options file");
      toggleLoadButton();
    }
    else
      setStatusMessage("Options file does not exist!");
  }


  private JButton getSourceFileButton() {
    if (sourceFileButton == null) {
      sourceFileButton = new JButton();
      sourceFileButton.setPreferredSize(new Dimension(15,20));
      sourceFileButton.setText("Browse...");
      sourceFileButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          Point loc = getMainFrame().getLocation();
          loc.translate(20, 20);
          getFileChooser().setLocation(loc);
          getFileChooser().showOpenDialog(getMainFrame());
          File selection = getFileChooser().getSelectedFile();
          if(selection != null) {
            getSourceFileTextField().setText(selection.getPath());
            updateSourceFile();
          }
        }
      });
    }
    return sourceFileButton;
  }

  private JButton getOptsFileButton() {
    if (optsFileButton == null) {
      optsFileButton = new JButton();
      optsFileButton.setPreferredSize(new Dimension(15,20));
      optsFileButton.setText("Browse...");
      optsFileButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          Point loc = getMainFrame().getLocation();
          loc.translate(20, 20);
          getFileChooser().setLocation(loc);
          getFileChooser().showOpenDialog(getMainFrame());
          File selection = getFileChooser().getSelectedFile();
          if(selection != null) {
            getOptsFileTextField().setText(selection.getPath());
            updateOptsFile();
          }
        }
      });
    }
    return optsFileButton;
  }

  private JLabel getStatusBar() {
    if(statusBar == null) {
      statusBar = new JLabel("Ready");
      statusBar.setPreferredSize(new Dimension(DEFAULT_WIDTH,20));
    }
    return statusBar;
  }

  public void setStatusMessage(String msg) {
    getStatusBar().setText(msg);
  }

  private JSeparator getStatusBarSeparator() {
    if(statusBarSeparator == null) {
      statusBarSeparator = new JSeparator();
    }
    return statusBarSeparator;
  }

  private JButton getLoadButton() {
    if (loadButton == null) {
      loadButton = new JButton();
      loadButton.setEnabled(false);
      loadButton.setPreferredSize(new Dimension(15,20));
      loadButton.setText("Load");
      loadButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          JFrame dialog = getAnalysisDialog();
          Point loc = getMainFrame().getLocation();
          loc.translate(20, 20);
          dialog.setLocation(loc);
          dialog.pack();
          dialog.setVisible(true);
          toggleLoadButton();
        }
      });
    }
    return loadButton;
  }

  public void toggleLoadButton() {
    if(getSourceFileTextField().getText().isEmpty() ||
        getOptsFileTextField().getText().isEmpty() || 
        (analysisDialog != null && analysisDialog.isVisible()))
      getLoadButton().setEnabled(false);
    else
      getLoadButton().setEnabled(true);
  }

  private JFileChooser getFileChooser() {
    if (fileChooser == null)
      fileChooser = new JFileChooser();
    return fileChooser;
  }

  private AnalysisDialog getAnalysisDialog() {
    if(analysisDialog == null) {
      analysisDialog = new AnalysisDialog();
      analysisDialog.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
      analysisDialog.addWindowListener(new AnalysisDialogHandler());
    }
    return analysisDialog;
  }

  private class AnalysisDialogHandler extends WindowAdapter {
    @Override
    public void windowClosing(WindowEvent e) {
      analysisDialog.freeResources();
      analysisDialog.dispose();
      analysisDialog = null;
      toggleLoadButton();
    }
  }

  private OptionsDialog getOptionsDialog() {
    if(optionsDialog == null) {
      optionsDialog = new OptionsDialog();
      optionsDialog.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
      optionsDialog.addWindowListener(new OptionsDialogHandler());
      optionsDialog.setResizable(false);
    }
    return optionsDialog;
  }

  private class OptionsDialogHandler extends WindowAdapter {
    public void windowClosing(WindowEvent e) {
      getOptionsDialog().setVisible(false);
      toggleLoadButton();
    }
  }

}
