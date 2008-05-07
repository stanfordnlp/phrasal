package mt.train;

import edu.stanford.nlp.util.FileLines;

import java.io.IOException;
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
    if(args.length != 4) {
      System.err.println("Usage: AER <fCorpus> <eCorpus> <refAlignment> <hypAlignment>");
      System.exit(1);
    }
    FileLines
         fCorpus = new FileLines(args[0]),
         eCorpus = new FileLines(args[1]),
         rCorpus = new FileLines(args[2]),
         hCorpus = new FileLines(args[3]);
    List<SymmetricalWordAlignment> rAlign = new ArrayList<SymmetricalWordAlignment>();
    List<SymmetricalWordAlignment> hAlign = new ArrayList<SymmetricalWordAlignment>();
    for(String fLine : fCorpus) {
      String eLine = eCorpus.iterator().next();
      String rLine = rCorpus.iterator().next();
      String hLine = hCorpus.iterator().next();
      assert(eLine != null && rLine != null && hLine != null);
      try {
        rAlign.add(new SymmetricalWordAlignment(fLine,eLine,rLine));
        hAlign.add(new SymmetricalWordAlignment(fLine,eLine,hLine));
      } catch(IOException ioe) {
        ioe.printStackTrace();
      }
    }
    assert(!eCorpus.iterator().hasNext());
    assert(!rCorpus.iterator().hasNext());
    assert(!hCorpus.iterator().hasNext());
    System.out.println(SymmetricalWordAlignment.computeAER
         (rAlign.toArray(new SymmetricalWordAlignment[rAlign.size()]),
          hAlign.toArray(new SymmetricalWordAlignment[hAlign.size()])));
  }
}
