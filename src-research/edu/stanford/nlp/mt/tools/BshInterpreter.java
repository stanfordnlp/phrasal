package edu.stanford.nlp.mt.tools;

/**
 * Interpreter providing support for BeanShell Scripting Language. This class
 * basically only imports all packages found in the MT project plus common API
 * packages.
 * 
 * @author Michel Galley
 */
public class BshInterpreter extends bsh.Interpreter implements
    edu.stanford.nlp.mt.tools.Interpreter {

  private static final long serialVersionUID = 1L;

  public BshInterpreter() {
    try {
      eval("import java.util.*;" + "import java.util.regex.*;"
          + "import java.io.*;" + "import edu.stanford.nlp.mt.*;"
          + "import edu.stanford.nlp.mt.base.*;"
          + "import edu.stanford.nlp.mt.train.*;"
          + "import edu.stanford.nlp.mt.tools.*;"
          + "import edu.stanford.nlp.mt.decoder.*;"
          + "import edu.stanford.nlp.mt.decoder.feat.*;"
          + "import edu.stanford.nlp.mt.decoder.efeat.*;");
    } catch (bsh.EvalError e) {
      throw new IllegalArgumentException("bsh Interpreter error: " + e);
    }
  }

  @Override
  public Object evalString(String s) {
    try {
      return eval(s);
    } catch (bsh.EvalError e) {
      throw new RuntimeException(e);
    }
  }
}
