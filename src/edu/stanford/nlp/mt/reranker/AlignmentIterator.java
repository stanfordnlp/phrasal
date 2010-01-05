package mt.reranker;

import edu.stanford.nlp.util.AbstractIterator;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.*;


public class AlignmentIterator extends AbstractIterator<LegacyAlignment> {

  private BufferedReader in;
  private LegacyAlignment nextToken;

  public AlignmentIterator(Reader in) {
    this.in = new BufferedReader(in);
    setNext();
  }

  @Override
	public boolean hasNext() {
    return nextToken != null;
  }

  public void setNext() {
    //String s = getNext();
    //nextToken = parseString(s);
    nextToken = getNext();
  }

  @Override
	public LegacyAlignment next() {
  	LegacyAlignment token = nextToken;
    setNext();
    return token;
  }

  public Object peek() {
    return nextToken;
  }

  private LegacyAlignment getNext() {
    String line = null;
    Pattern p = Pattern.compile("([^\\s+]) \\(\\{\\s*(.*?)\\s*\\}\\)");
    try {
      if((line = in.readLine()) != null) {
        if (line.matches("^#.*")) {
          line = in.readLine();
        }
        line = in.readLine();
        Matcher m = p.matcher(line);
        LegacyAlignment sentA = new LegacyAlignment();
        while(m.find()) {
          // m.group(1) : the English word
          // m.group(2) : the alignment to Chinese word
          String alignment = m.group(2);
          String[] aligns = alignment.split(" ");
          List<Integer> ints = new ArrayList<Integer>();
          for (String a : aligns) {
            if (a.length()> 0) 
              ints.add(Integer.parseInt(a));
          }
          sentA.add(ints);
        }
        return sentA;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public static IteratorFromReaderFactory<LegacyAlignment> getFactory() {
    //return new AlignmentIterator.AlignmentIteratorFactory(new AlignmentParser());
    return new AlignmentIterator.AlignmentIteratorFactory();
  }


  
  static class AlignmentIteratorFactory implements IteratorFromReaderFactory<LegacyAlignment> {

    public AlignmentIteratorFactory() {
    }

    public Iterator<LegacyAlignment> getIterator(Reader r) {
      return new AlignmentIterator(r);
    }
  }

}
