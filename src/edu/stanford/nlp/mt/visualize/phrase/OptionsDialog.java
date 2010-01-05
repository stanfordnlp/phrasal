package edu.stanford.nlp.mt.visualize.phrase;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * 
 * @author Spence Green
 */
public class OptionsDialog extends JFrame {

  private static final int DEFAULT_WIDTH = 300;
  private static final int DEFAULT_HEIGHT = 250;
  private static final int H_TEXTBOX = 15;
  private static final int W_TEXTBOX = 40;

  private final PhraseController controller;
  private int curHeatOptValue = 0;
  private int curRowsOptValue = 0;

  private JPanel panel = null;

  private GroupLayout layout = null;

  private JTextField heatOptTextField = null;

  private JLabel heatOptLabel = null;

  private JTextField rowsOptTextField = null;

  private JLabel rowsOptLabel = null;

  private JButton closeButton = null;

  public OptionsDialog() {
    super();

    controller = PhraseController.getInstance();

    this.setSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
    this.setTitle("Options");
    this.setContentPane(getPanel());
  }

  private JPanel getPanel() {
    if(panel == null) {
      panel = new JPanel();
      if(layout == null)
        layout = new GroupLayout(panel);
      panel.setLayout(layout);

      layout.setAutoCreateGaps(true);
      layout.setAutoCreateContainerGaps(true);
      layout.setHorizontalGroup(layout.createSequentialGroup()
          .addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
              .addComponent(getHeatOptTextField())
              .addComponent(getRowsOptTextField()))
              .addGroup(layout.createParallelGroup()
                  .addComponent(getHeatOptLabel())
                  .addComponent(getRowsOptLabel())
                  .addComponent(getCloseButton())));
      layout.setVerticalGroup(layout.createSequentialGroup()
          .addGroup(layout.createParallelGroup()
              .addComponent(getHeatOptTextField())
              .addComponent(getHeatOptLabel()))
              .addGroup(layout.createParallelGroup()
                  .addComponent(getRowsOptTextField())
                  .addComponent(getRowsOptLabel()))
                  .addComponent(getCloseButton()));
    }
    return panel;
  }

  private JTextField getHeatOptTextField() {
    if(heatOptTextField == null) {
      curHeatOptValue = controller.getScoreHalfRange();
      heatOptTextField = new JTextField(Integer.toString(curHeatOptValue));
      heatOptTextField.setMaximumSize(new Dimension(W_TEXTBOX,H_TEXTBOX));
      heatOptTextField.setHorizontalAlignment(JTextField.CENTER);
      heatOptTextField.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent arg0) {
          updateHeatOptTextField();
        }
      });
      heatOptTextField.addFocusListener(new FocusListener() {
        @Override
        public void focusGained(FocusEvent arg0) {}
        @Override
        public void focusLost(FocusEvent arg0) {
          updateHeatOptTextField();
        }
      });
    }
    return heatOptTextField;
  }

  private void updateHeatOptTextField() {
    String newString = getHeatOptTextField().getText().trim();
    if(newString.matches("\\d+")) {
      int newValue = Integer.parseInt(newString);
      if(controller.setScoreHalfRange(newValue)) {
        getHeatOptTextField().setText(newString);
        curHeatOptValue = newValue;
        return;
      }
    }
    getHeatOptTextField().setText(Integer.toString(curHeatOptValue));
  }

  private JLabel getHeatOptLabel() {
    if(heatOptLabel == null) {
      heatOptLabel = new JLabel("Heat Map Spread");
    }
    return heatOptLabel;
  }

  private JTextField getRowsOptTextField() {
    if(rowsOptTextField == null) {
      curRowsOptValue = controller.getNumOptionRows();
      rowsOptTextField = new JTextField(Integer.toString(curRowsOptValue));
      rowsOptTextField.setMaximumSize(new Dimension(W_TEXTBOX,H_TEXTBOX));
      rowsOptTextField.setHorizontalAlignment(JTextField.CENTER);
      rowsOptTextField.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent arg0) {
          updateRowsOptTextField();
        }
      });
      rowsOptTextField.addFocusListener(new FocusListener() {
        @Override
        public void focusGained(FocusEvent arg0) {}
        @Override
        public void focusLost(FocusEvent arg0) {
          updateRowsOptTextField();
        }
      });
    }
    return rowsOptTextField;
  }

  private void updateRowsOptTextField() {
    String newString = getRowsOptTextField().getText().trim();
    if(newString.matches("\\d+")) {
      int newValue = Integer.parseInt(newString);
      if(controller.setNumOptionRows(newValue)) {
        getRowsOptTextField().setText(newString);
        curRowsOptValue = newValue;
        return;
      }
    }
    getRowsOptTextField().setText(Integer.toString(curRowsOptValue));
  }

  private JLabel getRowsOptLabel() {
    if(rowsOptLabel == null) {
      rowsOptLabel = new JLabel("# of translation option rows");
    }
    return rowsOptLabel;
  }

  private JButton getCloseButton() {
    if(closeButton == null) {
      closeButton = new JButton("Close");
      closeButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          setVisible(false);
        }
      });
    }
    return closeButton;
  }


  private static final long serialVersionUID = -5757122871551438975L;
}
