package mt.reranker;

import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.AbstractIterator;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.Iterator;
import edu.stanford.nlp.trees.Tree;

public class TreeIterator extends AbstractIterator<Tree> {

  private BufferedReader in;
  private Tree nextToken;
  private Function<String,Tree> op;
  private boolean inTree = false;

  public TreeIterator(Reader in) {
    this(in, new TreeParser());
  }

  public TreeIterator(Reader in, Function<String,Tree> op) {
    this.in = new BufferedReader(in);
    this.op = op;
    setNext();
  }

  public boolean hasNext() {
    return nextToken != null;
  }

  public Tree parseString(String s) {
    return op.apply(s);
  }

  public void setNext() {
    String s = getNext();
    nextToken = parseString(s);
  }

  public Tree next() {
    Tree token = nextToken;
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

  public static IteratorFromReaderFactory<Tree> getFactory(Function<String,Tree> op) {
    return new TreeIterator.TreeIteratorFactory(op);
  }

  public static IteratorFromReaderFactory<Tree> getFactory() {
    return new TreeIterator.TreeIteratorFactory(new TreeParser());
  }


  
  static class TreeIteratorFactory implements IteratorFromReaderFactory<Tree> {
    private Function<String,Tree> op;


    public TreeIteratorFactory(Function<String,Tree> op) {
      this.op = op;
    }

    public Iterator<Tree> getIterator(Reader r) {
      return new TreeIterator(r, op);
    }
  }

}
