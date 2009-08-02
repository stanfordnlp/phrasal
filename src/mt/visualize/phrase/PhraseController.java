package mt.visualize.phrase;

import java.io.File;

public class PhraseController {

  private static boolean VERBOSE = false;
  private static boolean RIGHT_TO_LEFT = false;
  private static int SCORE_HALF_RANGE = 300;
  private static boolean NORM_SCORES = false;
  private static int NUM_OPTION_ROWS = 10;

  private PhraseModel model;
  private File sourceFilePath = null;
  private File optsFilePath = null;
//  private File modelFilePath;
  
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
  
  public boolean setOptsFile(String path) {
    optsFilePath = new File(path);
    return optsFilePath.exists();
  }
  
  public boolean setSourceFile(String path) {
    sourceFilePath = new File(path);    
    return sourceFilePath.exists();
  }
  
  public String getSourceFilePath() {
    return (sourceFilePath != null) ? sourceFilePath.getPath() : null;
  }
  
  public String getOptsFilePath() {
    return (optsFilePath != null) ? optsFilePath.getPath() : null;
  }
  
  public boolean getVerbose() {
    return VERBOSE;
  }
  
  public void setRightToLeft(boolean b) {
    RIGHT_TO_LEFT = b;
  }
  
  public boolean buildModel() {
    model = new PhraseModel(sourceFilePath, optsFilePath);
    model.setVerbose(VERBOSE);
    model.normalizePhraseScores(NORM_SCORES);
    model.setNumberOfOptionRows(NUM_OPTION_ROWS);
    
    if(model.load(SCORE_HALF_RANGE))
      if(model.buildLayouts(RIGHT_TO_LEFT))
        return true;

    return false;
  }
    
  public boolean modelIsBuilt() {
    return (model != null) ? model.isBuilt() : false;
  }
  
  public int getNumTranslations() {
    return (model != null) ? model.getNumTranslations() : 0;
  }
  
  public int getScoreHalfRange() {
    return SCORE_HALF_RANGE;
  }
  
  public boolean setScoreHalfRange(int range) {
    if(range > 0 && range < 600) {
      SCORE_HALF_RANGE = range;
      return true;
    }
    return false;
  }

  public int getScoreRank(double score) {
    return (model != null) ? model.getScoreRank(score) : 0;
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

  public void normalizePhraseScores(boolean newState) {
    NORM_SCORES = newState;
  }
  
  public boolean setNumOptionRows(int rows) {
    if(rows > 0 && rows < 40) {
    NUM_OPTION_ROWS = rows;
    return true;
    }
    return false;
  }
  
  public int getNumOptionRows() {
    return NUM_OPTION_ROWS;
  }

}
