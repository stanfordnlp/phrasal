package mt.train;

import bsh.Interpreter;


/**
 * Interpreter providing support for BeanShell Scripting Language.
 * This class basically only imports all packages found in the
 * MT project plus common API packages.
 *
 * @author Michel Galley
 */
public class BshInterpreter extends bsh.Interpreter
{
  private static final long serialVersionUID = 1L;
	
	Interpreter interpreter;

  public BshInterpreter()
  {
    try {
      eval (
        "import java.util.*;"+
        "import java.util.regex.*;"+
        "import java.io.*;"+
        "import mt.*;"+
        "import mt.base.*;"+
        "import mt.train.*;"+
        "import mt.tools.*;"+
        "import mt.ExperimentalFeaturizers.*;");
    } catch (bsh.EvalError e) {
      throw new IllegalArgumentException ("bsh Interpreter error: "+e);
    }
  }
}
