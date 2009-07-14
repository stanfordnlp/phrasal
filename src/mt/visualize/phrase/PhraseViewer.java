package mt.visualize.phrase;

import joptsimple.*;
import java.util.List;

public final class PhraseViewer {

  private PhraseViewer() {}
  
  private static String usage() {
    String cmdLineUsage = String.format("java PhraseViewer [OPTS]\n");
    StringBuilder sb = new StringBuilder(cmdLineUsage);
    
    sb.append(" -v    : Verbose output to console\n");
    
    return sb.toString();
  }
  
  private final static OptionParser op = new OptionParser("v");
  private final static int MIN_ARGS = 0;
  
  private static boolean VERBOSE = false;
  
  private static boolean validateCommandLine(String[] args) {
    //Command line parsing
    OptionSet opts = null;
    List<String> parsedArgs = null;
    try {
      opts = op.parse(args);

      parsedArgs = opts.nonOptionArguments();

      if(parsedArgs.size() < MIN_ARGS)
        return false;

    } catch (OptionException e) {
      System.err.println(e.toString());
      return false;
    }

    VERBOSE = opts.has("v");

    return true;
  }

  
  public static void main(String[] args) {
    if(!validateCommandLine(args)) {
      System.err.println(usage());
      System.exit(-1);
    }
  
    PhraseController pc = PhraseController.getInstance();
    
    //Set options
    pc.setVerbose(VERBOSE);
    
    pc.run();
  } 
}
