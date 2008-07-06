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

  public static final String DEBUG_PROPERTY = "DebugWordAlignment";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  public static void main(String[] args) {
    if(args.length != 6) {
      System.err.println("Usage: AER <fCorpus> <eCorpus> <refAlignment> <refUCB> <hypAlignment> <hypUCB>");
      System.err.println("where:");
      System.err.println("  fCorpus: source-language text");
      System.err.println("  eCorpus: target-language text");
      System.err.println("  refAlignment: reference word alignment");
      System.err.println("  refUCB: set to true if refAlignment in UCB alignment format (default: Moses format)");
      System.err.println("  hypAlignment: system word alignment");
      System.err.println("  hypUCB: set to true if hypAlignment in UCB alignment format (default: Moses format)");
      System.err.println("Wrong number of args: "+args.length);
      System.exit(1);
    }
    boolean r_s2t = true, r_zeroBased = false, h_s2t = true, h_zeroBased = false;
    if(Boolean.parseBoolean(args[3])) {
      r_s2t = false; r_zeroBased = true;
    } 
    if(Boolean.parseBoolean(args[5])) {
      h_s2t = false; h_zeroBased = true;
    }
    Iterator<String>
         fCorpus = new FileLines(args[0]).iterator(),
         eCorpus = new FileLines(args[1]).iterator(),
         rCorpus = new FileLines(args[2]).iterator(),
         hCorpus = new FileLines(args[4]).iterator();
    List<SymmetricalWordAlignment> rAlign = new ArrayList<SymmetricalWordAlignment>();
    List<SymmetricalWordAlignment> hAlign = new ArrayList<SymmetricalWordAlignment>();
    int lineNb = 0;
    while(fCorpus.hasNext()) {
      ++lineNb;
      System.err.println("line : "+lineNb);
      String fLine = fCorpus.next();
      String eLine = eCorpus.next();
      String rLine = rCorpus.next();
      String hLine = hCorpus.next();
      assert(eLine != null && rLine != null && hLine != null);
      boolean isHyp=false;
      try {
        rAlign.add(new SymmetricalWordAlignment(fLine,eLine,rLine,r_s2t,r_zeroBased));
        isHyp=true;
        hAlign.add(new SymmetricalWordAlignment(fLine,eLine,hLine,h_s2t,h_zeroBased));
      } catch(IOException ioe) {
        System.err.printf("Error at line: %d\n",lineNb);
        System.err.printf("ref: %s\neline: %s\nfline: %s\naline: %s\n",!isHyp,fLine,eLine, (isHyp ? hLine : rLine));
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
