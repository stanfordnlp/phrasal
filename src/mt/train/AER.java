package mt.train;

import edu.stanford.nlp.util.FileLines;

import java.io.IOException;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;

/**
 * Compute AER from four files: two corpus files, one reference alignment, and
 * one machine alignment. Note that the two corpora are only needed to determine
 * sentence lengths.
 *
 * @author Michel Galley
 */
public class AER {
  public static void main(String[] args) {
    if(args.length != 6) {
      System.err.println("Usage: AER <s2t> <zero> <fCorpus> <eCorpus> <refAlignment> <hypAlignment>");
      System.exit(1);
    }
    boolean s2t = Boolean.parseBoolean(args[0]);
    boolean zeroBased = Boolean.parseBoolean(args[1]);
    Iterator<String>
         fCorpus = new FileLines(args[2]).iterator(),
         eCorpus = new FileLines(args[3]).iterator(),
         rCorpus = new FileLines(args[4]).iterator(),
         hCorpus = new FileLines(args[5]).iterator();
    List<SymmetricalWordAlignment> rAlign = new ArrayList<SymmetricalWordAlignment>();
    List<SymmetricalWordAlignment> hAlign = new ArrayList<SymmetricalWordAlignment>();
    int lineNb = 0;
    while(fCorpus.hasNext()) {
      ++lineNb;
      String fLine = fCorpus.next();
      String eLine = eCorpus.next();
      String rLine = rCorpus.next();
      String hLine = hCorpus.next();
      assert(eLine != null && rLine != null && hLine != null);
      try {
        rAlign.add(new SymmetricalWordAlignment(fLine,eLine,rLine,s2t,zeroBased));
        hAlign.add(new SymmetricalWordAlignment(fLine,eLine,hLine,s2t,zeroBased));
      } catch(IOException ioe) {
        System.err.printf("Error at line: %d\n",lineNb);
        ioe.printStackTrace();
        System.exit(1);
      }
    }
    assert(!eCorpus.hasNext());
    assert(!rCorpus.hasNext());
    assert(!hCorpus.hasNext());
    System.out.println(SymmetricalWordAlignment.computeAER
         (rAlign.toArray(new SymmetricalWordAlignment[rAlign.size()]),
          hAlign.toArray(new SymmetricalWordAlignment[hAlign.size()])));
  }
}
