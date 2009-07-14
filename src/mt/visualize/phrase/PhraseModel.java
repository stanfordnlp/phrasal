package mt.visualize.phrase;

import java.io.*;
import java.util.List;
import java.util.ArrayList;

import javax.swing.JPanel;

public class PhraseModel {

  private static final int OPT_TOKS_PER_LINE = 5;
  private static boolean RIGHT_TO_LEFT = false;

  //TODO Maybe make this configurable
  private static final int MAX_OPTS = 20; //How many options to read for each translation
  private static final int NUM_VISUAL_OPTS = 10;
  
  private boolean VERBOSE = false;
  private final File sourceFile;
  private final File optsFile;
//  private final File modelFile;
  private final List<Translation> translations;
  private final List<TranslationLayout> layouts;

  public PhraseModel(File source, File opts, File model) {
    sourceFile = source;
    optsFile = opts;
//    modelFile = model;

    translations = new ArrayList<Translation>();
    layouts = new ArrayList<TranslationLayout>();

    if(!sourceFile.exists())
      throw new RuntimeException(String.format("%s: %s does not exist",this.getClass().getName(),sourceFile.getPath()));
    if(!optsFile.exists())
      throw new RuntimeException(String.format("%s: %s does not exist",this.getClass().getName(),sourceFile.getPath()));
//WSGDEBUG May add the model later
//    if(!modelFile.exists())
//      throw new RuntimeException(String.format("%s: %s does not exist",this.getClass().getName(),sourceFile.getPath()));
  }

  public void setVerbose(boolean verbose) {
    VERBOSE = verbose;
  }

  public boolean load() {
    try {
      LineNumberReader sourceReader = new LineNumberReader(new FileReader(sourceFile));
      LineNumberReader optsReader = new LineNumberReader(new FileReader(optsFile));

      String[] lastOpt = null;
      int transId;
      for(transId = 0; sourceReader.ready(); transId++) {
        String source = sourceReader.readLine();
        Translation translation = new Translation(transId,source);

        if(lastOpt != null && Integer.parseInt(lastOpt[0]) == transId)
          translation.addPhrase(Math.exp(Double.parseDouble(lastOpt[3])), 
                                lastOpt[2], 
                                lastOpt[4]);
        lastOpt = null;

        for(int optsRead = 0; optsReader.ready(); optsRead++) {
          String transOpt = optsReader.readLine();
          String[] optToks = transOpt.split("\\s*\\|\\|\\|\\s*");
          assert optToks.length == OPT_TOKS_PER_LINE;

          int id = Integer.parseInt(optToks[0]);
          if(id != transId) {
            lastOpt = optToks;
            break;
          }

          double score = Math.exp(Double.parseDouble(optToks[3]));
          String english = optToks[2];
          String coverage = optToks[4];

          translation.addPhrase(score, english, coverage);
        }
        translations.add(translation);
      }

      if(VERBOSE)
        System.err.printf("%s: Read %d source sentences from %s\n",this.getClass().getName(), transId,sourceFile.getPath());

      sourceReader.close();
      optsReader.close();

    } catch (FileNotFoundException e) {
      System.err.printf("%s: Could not open file\n%s\n", this.getClass().getName(), e.toString());
      return false;
    } catch (IOException e) {
      System.err.printf("%s: Error while reading file\n%s\n",this.getClass().getName(), e.toString());
      return false;
    }

    return true;
  }

  public boolean build() {
    for(Translation translation : translations) {
      TranslationLayout layout = new TranslationLayout(translation,RIGHT_TO_LEFT);
      layout.doLayout(NUM_VISUAL_OPTS);
      layouts.add(layout);
    }

    return true;
  }

  public int getNumTranslations() {
    return (translations != null) ? translations.size() : 0;
  }

  //TODO Should return a deep copy, not a reference
  public TranslationLayout getTranslation(int i) {
    return (layouts != null && i > 0 && i <= getNumTranslations()) ? layouts.get(i - 1) : null;
  }

  public void setRightToLeft(boolean b) {
    RIGHT_TO_LEFT = b;
  }

}
