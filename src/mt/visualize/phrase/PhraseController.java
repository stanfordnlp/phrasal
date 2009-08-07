package mt.visualize.phrase;

import java.io.File;
import java.util.List;
import java.util.Map;

public class PhraseController {

  private static boolean VERBOSE = false;
  private static boolean RIGHT_TO_LEFT = false;
  private static int SCORE_HALF_RANGE = 300;
  private static boolean NORM_SCORES = false;
  private static int NUM_OPTION_ROWS = 10;

  private PhraseModel phraseModel;
  private File sourceFilePath = null;
  private File optsFilePath = null;

  private PathModel pathModel = null;
  private File oracleFilePath = null;
  private File oneBestFilePath = null;
  private File savedPathsFilePath = null;

  private boolean isBuilt = false;

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
    isBuilt = false;
    phraseModel = new PhraseModel(sourceFilePath, optsFilePath);
    phraseModel.setVerbose(VERBOSE);
    phraseModel.normalizePhraseScores(NORM_SCORES);
    phraseModel.setNumberOfOptionRows(NUM_OPTION_ROWS);

    
    boolean success = phraseModel.load(SCORE_HALF_RANGE);
    if(!success) {
      if(VERBOSE)
        System.err.printf("%s: Failed to load model from source and options files\n", this.getClass().getName());
      return success;
    }

    pathModel = new PathModel(oracleFilePath,oneBestFilePath,savedPathsFilePath);
    success &= pathModel.load();
    if(!success) {
      if(VERBOSE)
        System.err.printf("%s: Unable to load stored path model from oracle and onebest files\n", this.getClass().getName());
      return success;
    }
    
    phraseModel.setPathModel(pathModel);
    
    success &= phraseModel.buildLayouts(RIGHT_TO_LEFT);
    if(!success) {
      if(VERBOSE)
        System.err.printf("%s: Failed to construct translation layouts\n", this.getClass().getName());
      return success;
    }

    isBuilt = success;
    
    return isBuilt;
  }
  
  public String getTranslation(int translationId, String name) {
    return (isBuilt) ? pathModel.getTranslation(translationId, name) : null;
  }
  
  public void setTranslation(int translationId, String name, String translation) {
    if(isBuilt)
      pathModel.setTranslation(translationId, name, translation);
  }
  
  public List<String> getPathNames(int translationId) {
    return (isBuilt) ? pathModel.getPathNames(translationId) : null;
  }
  
  public Map<String,List<VisualPhrase>> getPaths(int translationId) {
    return (isBuilt) ? pathModel.getPaths(translationId) : null;
  }
  
  public boolean isEnabled(int translationId, String name) {
    return (isBuilt) ? pathModel.isEnabled(translationId, name) : false;
  }
  
  public boolean addPath(int translationId, String name) {
    return (isBuilt) ? pathModel.addPath(translationId,name) : false;
  }
  
  public void deletePath(int translationId, String name) {
    if(isBuilt)
      pathModel.deletePath(translationId, name);
  }
  
  public int getFormatId(int translationId, String name) {
    return (isBuilt) ? pathModel.getFormatId(translationId, name) : -1;
  }
  
  public void setPathState(boolean isOn, int translationId, String name) {
    if(isBuilt)
      pathModel.setPathState(isOn,translationId,name);
  }
  
  public boolean finishPath(int translationId, String name) {
    return (isBuilt) ? pathModel.finishPath(translationId, name) : false;
  }
  
  public void addClickStreamListener(ClickEventListener e) {
    pathModel.addClickEventListener(e);
  }
  
  public void removeClickStreamListener(ClickEventListener e) {
    pathModel.removeClickEventListener(e);
  }
  
  public int getMaxPaths() {
    return PathModel.MAX_PATHS;
  }
  
  //WSGDEBUG end path building stuff

  public boolean modelIsBuilt() {
    return isBuilt;
  }

  public int getNumTranslations() {
    return (isBuilt) ? phraseModel.getNumTranslations() : 0;
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
    return (isBuilt) ? phraseModel.getScoreRank(score) : 0;
  }

  public void run() {
    PhraseGUI.show();
  }

  public TranslationLayout getTranslationLayout(int translationId) {
    return (isBuilt) ? phraseModel.getTranslationLayout(translationId) : null;
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
