package mt.reranker;

import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.AbstractIterator;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.Iterator;


public class TreeIterator extends AbstractIterator {

  private BufferedReader in;
  private Object nextToken;
  private Function op;
  private boolean inTree = false;

  public TreeIterator(Reader in) {
    this(in, new TreeParser());
  }

  public TreeIterator(Reader in, Function op) {
    this.in = new BufferedReader(in);
    this.op = op;
    setNext();
  }

  public boolean hasNext() {
    return nextToken != null;
  }

  public Object parseString(String s) {
    return op.apply(s);
  }

  public void setNext() {
    String s = getNext();
    nextToken = parseString(s);
  }

  public Object next() {
    Object token = nextToken;
    setNext();
    return token;
  }

  public Object peek() {
    return nextToken;
  }

  private String getNext() {
    StringBuilder tree = new StringBuilder();
    String line = null;
    try {
      while((line = in.readLine()) != null) {
        if (line.matches("\\s*<tree style=\"penn\">\\s*")) {
          inTree = true;
        } else if (line.matches("\\s*</tree>\\s*")) {
          inTree = false;
          return tree.toString();
        } else if (inTree) {
          line = line.replaceAll("=H","");
          tree.append(line);
          tree.append("\n");
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public static IteratorFromReaderFactory getFactory(Function op) {
    return new TreeIterator.TreeIteratorFactory(op);
  }

  public static IteratorFromReaderFactory getFactory() {
    return new TreeIterator.TreeIteratorFactory(new TreeParser());
  }


  
  static class TreeIteratorFactory implements IteratorFromReaderFactory {
    private Function op;


    public TreeIteratorFactory(Function op) {
      this.op = op;
    }

    public Iterator getIterator(Reader r) {
      return new TreeIterator(r, op);
    }
  }

}
