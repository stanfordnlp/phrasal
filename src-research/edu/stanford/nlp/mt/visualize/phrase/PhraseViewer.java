package edu.stanford.nlp.mt.visualize.phrase;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Phrase table visualization.
 * 
 * Written during the summer of 2009 when I was a young man....
 * 
 * @author Spence Green
 *
 */
public final class PhraseViewer {

  private PhraseViewer() {
  }

  private static String usage() {
    String cmdLineUsage = String.format("Usage: java PhraseViewer [OPTS]\n");
    StringBuilder sb = new StringBuilder(cmdLineUsage);

    sb.append(" -v    : Verbose output to console\n");
    sb.append(" -s    : Source file\n");
    sb.append(" -o    : Options file\n");
    sb.append(" -x    : Path to translation path schema file (required for loading and saving)\n");
    sb.append(" -f    : First translation id to read\n");
    sb.append(" -l    : Last translation id to read\n");

    return sb.toString();
  }

  private static Map<String,Integer> argDefs() {
    Map<String,Integer> argDefs = new HashMap<String,Integer>();
    argDefs.put("v", 0);
    argDefs.put("s", 1);
    argDefs.put("o", 1);
    argDefs.put("x", 1);
    argDefs.put("f", 1);
    argDefs.put("l", 1);
    return argDefs;
  }

  private static boolean VERBOSE = false;
  private static int FIRST_ID = Integer.MIN_VALUE;
  private static int LAST_ID = Integer.MAX_VALUE;
  private static String SRC_FILE = null;
  private static String OPTS_FILE = null;
  private static String XSD_FILE = null;

  private static boolean validateCommandLine(String[] args) {
    // Command line parsing
    Properties options = StringUtils.argsToProperties(args, argDefs());

    VERBOSE = options.containsKey("v");
    SRC_FILE = options.getProperty("s", null);
    OPTS_FILE = options.getProperty("o", null);
    XSD_FILE = options.getProperty("x", null);
    FIRST_ID = PropertiesUtils.getInt(options, "f", Integer.MIN_VALUE);
    LAST_ID = PropertiesUtils.getInt(options,"l",Integer.MAX_VALUE);

    return true;
  }

  public static void main(String[] args) {
    if (!validateCommandLine(args)) {
      System.err.println(usage());
      System.exit(-1);
    }

    PhraseController pc = PhraseController.getInstance();

    pc.setVerbose(VERBOSE);

    if (!pc.setRange(FIRST_ID, LAST_ID)) {
      System.err.printf("ERROR: Invalid range specified {%d,%d}\n", FIRST_ID,
          LAST_ID);
      System.exit(-1);
    }
    if (SRC_FILE != null && !pc.setSourceFile(SRC_FILE)) {
      System.err.printf("ERROR: %s does not exist!\n", SRC_FILE);
      System.exit(-1);
    }
    if (OPTS_FILE != null && !pc.setOptsFile(OPTS_FILE)) {
      System.err.printf("ERROR: %s does not exist!\n", OPTS_FILE);
      System.exit(-1);
    }
    if (XSD_FILE != null && !pc.setSchemaFile(XSD_FILE)) {
      System.err.printf("ERROR: %s does not exist!\n", XSD_FILE);
      System.exit(-1);
    }

    pc.run();

  }
}
