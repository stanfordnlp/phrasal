package edu.stanford.nlp.mt.visualize.phrase;

import java.io.File;
import java.util.List;
import java.util.Map;

import javax.swing.event.EventListenerList;

/**
 * 
 * @author Spence Green
 */
public final class PhraseController {

  private static boolean VERBOSE = false;
  private static boolean RIGHT_TO_LEFT = false;
  private static int SCORE_HALF_RANGE = 300;
  private static boolean NORM_SCORES = false;
  private static int NUM_OPTION_ROWS = 10;

  private PhraseModel phraseModel;
  private File sourceFilePath = null;
  private File optsFilePath = null;
  private File pathSchemaFilePath = null;
  private int firstTranslationId = -1;
  private int lastTranslationId = -1;

  private PathModel pathModel = null;

  private boolean pathModelIsBuilt = false;
  private boolean phraseModelIsBuilt = false;

  private static PhraseController thisInstance = null;

  private final EventListenerList listenerList;

  private PhraseController() {
    listenerList = new EventListenerList();
  }

  public static PhraseController getInstance() {
    if (thisInstance == null)
      thisInstance = new PhraseController();
    return thisInstance;
  }

  public void setVerbose(boolean b) {
    if (b)
      System.err.printf("%s: Setting verbose mode to %s\n", this.getClass()
          .getName(), Boolean.toString(b));
    VERBOSE = b;
  }

  public boolean setOptsFile(String path) {
    optsFilePath = new File(path);
    return optsFilePath.exists();
  }

  public boolean setRange(int firstId, int lastId) {
    if (firstId >= 0 && lastId >= 0) {
      firstTranslationId = firstId;
      lastTranslationId = lastId;
      return true;
    }
    return false;
  }

  public boolean setSourceFile(String path) {
    sourceFilePath = new File(path);
    return sourceFilePath.exists();
  }

  public boolean setSchemaFile(String path) {
    pathSchemaFilePath = new File(path);
    return pathSchemaFilePath.exists();
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
    phraseModelIsBuilt = false;
    phraseModel = new PhraseModel(sourceFilePath, optsFilePath);
    phraseModel.setVerbose(VERBOSE);
    phraseModel.normalizePhraseScores(NORM_SCORES);
    phraseModel.setNumberOfOptionRows(NUM_OPTION_ROWS);

    boolean success = phraseModel.load(firstTranslationId, lastTranslationId,
        SCORE_HALF_RANGE);
    if (!success) {
      if (VERBOSE)
        System.err.printf(
            "%s: Failed to load model from source and options files\n", this
                .getClass().getName());
      return success;
    }

    if (pathModel != null)
      pathModel.freeResources();

    pathModel = new PathModel();
    pathModelIsBuilt = success;

    success &= phraseModel.buildLayouts(RIGHT_TO_LEFT);
    if (!success) {
      if (VERBOSE)
        System.err.printf("%s: Failed to construct translation layouts\n", this
            .getClass().getName());
      return success;
    }

    // Set last for thread safety in AnalysisDialog
    phraseModelIsBuilt = success;

    return phraseModelIsBuilt;
  }

  public boolean isFileIOEnabled() {
    return (pathModel != null && pathSchemaFilePath != null);
  }

  public String getTranslationFromPath(int translationId, String name) {
    return (pathModelIsBuilt) ? pathModel.getTranslationFromPath(translationId,
        name) : null;
  }

  public void setTranslationForPath(int translationId, String name,
      String translation) {
    if (pathModelIsBuilt)
      pathModel.setTranslation(translationId, name, translation);
  }

  public List<String> getPathNames(int translationId) {
    return (pathModelIsBuilt) ? pathModel.getPathNames(translationId) : null;
  }

  public Map<String, List<VisualPhrase>> getPaths(int translationId) {
    return (pathModelIsBuilt) ? pathModel.getPaths(translationId) : null;
  }

  public boolean isEnabled(int translationId, String name) {
    return (pathModelIsBuilt) ? pathModel.isEnabled(translationId, name)
        : false;
  }

  public boolean addPath(int translationId, String name) {
    return (pathModelIsBuilt) ? pathModel.addPath(translationId, name) : false;
  }

  public void deletePath(int translationId, String name) {
    if (pathModelIsBuilt)
      pathModel.deletePath(translationId, name);
  }

  public boolean savePaths(File f) {
    return (isFileIOEnabled()) ? pathModel.save(f, pathSchemaFilePath) : false;
  }

  public boolean loadPaths(File f) {
    return (isFileIOEnabled()) ? pathModel.load(f, pathSchemaFilePath) : false;
  }

  public boolean pathModelLoaded() {
    return (pathModelIsBuilt) ? pathModel.isLoaded() : false;
  }

  public int getFormatId(int translationId, String name) {
    return (pathModelIsBuilt) ? pathModel.getFormatId(translationId, name) : -1;
  }

  public void setPathState(boolean isOn, int translationId, String name) {
    if (pathModelIsBuilt)
      pathModel.setPathState(isOn, translationId, name);
  }

  public boolean finishPath(int translationId, String name) {
    return (pathModelIsBuilt) ? pathModel.finishPath(translationId, name)
        : false;
  }

  public int getMaxPaths() {
    return PathModel.MAX_PATHS;
  }

  public boolean modelIsBuilt() {
    return phraseModelIsBuilt;
  }

  public int getNumTranslationLayouts() {
    // Just check to see if phraseModel is non-null
    // So that AnalysisDialog can get interim results
    return (phraseModel != null) ? phraseModel.getNumTranslations() : 0;
  }

  public int getScoreHalfRange() {
    return SCORE_HALF_RANGE;
  }

  public boolean setScoreHalfRange(int range) {
    if (range > 0 && range < 600) {
      SCORE_HALF_RANGE = range;
      return true;
    }
    return false;
  }

  public int getScoreRank(double score) {
    return (phraseModel != null) ? phraseModel.getScoreRank(score) : 0;
  }

  public int getMinTranslationId() {
    return (phraseModel != null) ? phraseModel.getMinTranslationId() : 0;
  }

  public void run() {
    PhraseGUI.show();
  }

  public TranslationLayout getTranslationLayout(int translationId) {
    return (phraseModel != null) ? phraseModel
        .getTranslationLayout(translationId) : null;
  }

  public String getTranslationSource(int translationId) {
    return (phraseModelIsBuilt) ? phraseModel
        .getTranslationSource(translationId) : null;
  }

  public VisualPhrase lookupVisualPhrase(int translationId, Phrase p) {
    if (phraseModelIsBuilt) {
      TranslationLayout layout = phraseModel
          .getTranslationLayout(translationId);
      if (layout != null)
        return layout.lookupVisualPhrase(p);
      else if (VERBOSE)
        System.err.printf("%s: Could not get a layout for translation id %d\n",
            this.getClass().getName(), translationId);
    }
    return null;
  }

  public void normalizePhraseScores(boolean newState) {
    NORM_SCORES = newState;
  }

  public boolean setNumOptionRows(int rows) {
    if (rows > 0 && rows < 40) {
      NUM_OPTION_ROWS = rows;
      return true;
    }
    return false;
  }

  public int getNumOptionRows() {
    return NUM_OPTION_ROWS;
  }

  public void addClickToStream(VisualPhrase vp) {
    notifyClickListeners(vp);
  }

  private void notifyClickListeners(VisualPhrase vp) {
    Object[] listeners = listenerList.getListenerList();
    for (int i = 0; i < listeners.length; i += 2)
      if (listeners[i] == ClickEventListener.class)
        ((ClickEventListener) listeners[i + 1])
            .handleClickEvent(new ClickEvent(vp));
  }

  // No synchronization needed with EventListenerList class
  public void addClickEventListener(ClickEventListener listener) {
    listenerList.add(ClickEventListener.class, listener);
  }

  // No synchronization needed with EventListenerList class
  public void removeClickEventListener(ClickEventListener listener) {
    listenerList.remove(ClickEventListener.class, listener);
  }

}
