package mt.tools.turk;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.Random;


/**
 * Command line utility to randomly select a subset of lines
 * from an input stream
 *
 * @author Daniel Cer
 */

public class SelectSubset {
  static public void main(String argv[]) throws IOException {
    if (argv.length != 1) {
      System.out.println(
        "Usage:\n\tjava ...SelectSubset (lines to select) < input\n");
      System.exit(-1);
    }
    int idsToSelect = Integer.parseInt(argv[0]);
    List<String> lines = new ArrayList<String>();
    BufferedReader reader = new BufferedReader(
      new InputStreamReader(System.in));
    for (String line = reader.readLine(); line != null; 
      line = reader.readLine()) {
      lines.add(line);
    }
    reader.close();

    Random r = new Random(1); // make runs repeatable
    TreeSet<Integer> selectedIds = new TreeSet<Integer>();
    List<Integer> idPool = new ArrayList<Integer>(lines.size());
    for (int i = 0; i < lines.size(); i++) idPool.add(i);
    while (selectedIds.size() < idsToSelect && idPool.size() != 0) {
      int candIdx = r.nextInt(idPool.size());
      selectedIds.add(idPool.get(candIdx));
      idPool.remove(candIdx);
    }
    for (Integer id : selectedIds) {
      System.out.println(lines.get(id));
    }
  }
}
