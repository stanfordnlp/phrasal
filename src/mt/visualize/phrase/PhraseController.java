package mt.visualize.phrase;

import java.io.File;

import javax.swing.JPanel;

public class PhraseController {

  private PhraseModel model;
  private boolean VERBOSE = false;
  private boolean RIGHT_TO_LEFT = false;
  private File sourceFilePath;
  private File optsFilePath;
  private File modelFilePath;
  
  private static PhraseController thisInstance = null;
  
  private PhraseController() {}
  
  public static PhraseController getInstance() {
    if(thisInstance == null)
      thisInstance = new PhraseController();
    return thisInstance;
  }
  
  public void setVerbose(boolean b) {
    System.err.printf("%s: Setting verbose mode to %s\n", this.getClass().getName(), Boolean.toString(b));
    VERBOSE = b;
  }
  
  public boolean getVerbose() {
    return VERBOSE;
  }
  
  public void setRightToLeft(boolean b) {
    RIGHT_TO_LEFT = b;
  }
  
  public boolean buildModel() {
    model = new PhraseModel(sourceFilePath, optsFilePath, modelFilePath);
    model.setVerbose(VERBOSE);
    model.setRightToLeft(RIGHT_TO_LEFT);
    
    if(model.load())
      if(model.build())
        return true;

    return false;
  }
  
  public void setSourceFilePath(File f) {
    sourceFilePath = f;
  }
  
  public void setOptsFilePath(File f) {
    optsFilePath = f;
  }
  
  public void setModelFilePath(File f) {
    modelFilePath = f;
  }
  
  public int numTranslations() {
    return (model != null) ? model.getNumTranslations() : 0;
  }
  
  /**
   * Launch the interface
   */
  public void run() {
    PhraseGUI.show();
  }

  public TranslationLayout getTranslation(int i) {
    return (model != null) ? model.getTranslation(i) : null;
  }
}
