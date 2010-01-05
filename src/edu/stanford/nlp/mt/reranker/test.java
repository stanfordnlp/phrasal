package mt.reranker;

import java.io.*;

public class test {
  public static void main(String args[]) throws IOException{
    BufferedReader br = new BufferedReader(new FileReader(args[0]));
    BufferedReader[] refbr = new BufferedReader[4];
    refbr[0] = new BufferedReader(new FileReader(args[1]));
    refbr[1] = new BufferedReader(new FileReader(args[2]));
    refbr[2] = new BufferedReader(new FileReader(args[3]));
    refbr[3] = new BufferedReader(new FileReader(args[4]));
    
    Bleu bleu = new Bleu();
    String line;
    while((line=br.readLine())!=null) {
      String[] sentence = line.split("\\s+");
      String[] refS = getRef(refbr);
      String[][] refs = new String[refS.length][];
      for (int refI = 0; refI < refS.length; refI++) {
        refs[refI] = refS[refI].split("\\s+");
      }
      SegStats s = new SegStats(sentence, refs);
      bleu.add(s);
    }
    System.err.println(bleu.score());
  }
  
  static String[] getRef(BufferedReader[] refs) throws IOException {
    String[] lines = new String[refs.length];
    for (int i = 0; i < refs.length; i++) {
      lines[i] = refs[i].readLine();
    }
    return lines;
  }
}
