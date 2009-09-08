package mt.tools.turk;

import static java.lang.System.*;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;


/**
 * Randomly mix together lines from different
 * reference files
 *
 * @author Daniel Cer
 */
public class MixReferences {
  static public void main(String argv[]) throws IOException { 
    if (argv.length == 0) {
      out.println("Usage:\n\tjava ...MixReferences (ref0) (ref1) ....\n");
      exit(-1);
    }
    List<List<String>> refs = new ArrayList<List<String>>();
    for (String filename  : argv) {
      List<String> reflines = new ArrayList<String>();
      refs.add(reflines);
      BufferedReader reader = new BufferedReader(new FileReader(filename));
      for (String line = reader.readLine(); line != null;
          line = reader.readLine()) {
        reflines.add(line);
      }
    }
    for (int i = 0; i < refs.size()-1; i++) {
      if (refs.get(i).size() != refs.get(i+1).size()) {
        throw new RuntimeException(
          String.format("ref file size mismatch %s(%d) vs. %s(%d)\n", 
          argv[i], refs.get(i).size(), argv[i+1], refs.get(i+1).size()));
      }
    } 
    Random r = new Random(1); // make results repeatable
    for (int i = 0; i < refs.get(0).size(); i++) {
      int selectId = r.nextInt(refs.size());
      //out.println(selectId + ": "+refs.get(selectId).get(i));
      out.println(refs.get(selectId).get(i));
    }
  }
}
