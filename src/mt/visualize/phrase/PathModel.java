package mt.visualize.phrase;

import java.io.File;


//can be created from file
//should handle XML reading and writing

public class PathModel {
  private File oracleFilePath;
  private File oneBestFilePath;
  private File savedPathsFilePath;
  
  public PathModel(File oracle, File oneBest, File savedPaths) {
    //TODO These can be null if the user does not specify them
    oracleFilePath = oracle;
    oneBestFilePath = oneBest;
    savedPathsFilePath = savedPaths;
  }
  
  public boolean load() {
    return true;
  }
}
