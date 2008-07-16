package mt.train;

import java.util.*;
import java.io.*;
import java.util.zip.GZIPInputStream;

import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;
import mt.base.Sequence;
import mt.base.SimpleSequence;

/**
  * Sentence pair with GIZA word alignment, with GIZA alignment probability for 
  * each direction (not well tested).
  * 
  * @author Michel Galley
  */

public class GIZAWordAlignment extends AbstractWordAlignment {

  public static final String DEBUG_PROPERTY = "DebugGIZAWordAlignment";
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));

  double p_f2e = 0.0;
  double p_e2f = 0.0;

  GIZAWordAlignment() {}

  /**
   * Init sentence pair from giza alignment.
   */
  public GIZAWordAlignment
    (String f2e_line1, String f2e_line2, String f2e_line3, String e2f_line1, String e2f_line2, String e2f_line3) 
      throws IOException {
    init(f2e_line1, f2e_line2, f2e_line3, e2f_line1, e2f_line2, e2f_line3);
  }
 
  @SuppressWarnings("unchecked")
  public void init(String f2e_line1, String f2e_line2, String f2e_line3, String e2f_line1, String e2f_line2, String e2f_line3) 
      throws IOException {
    // Read GIZA prob from comments:
    String[] comment_f2e = f2e_line1.split("\\s+");
    String[] comment_e2f = e2f_line1.split("\\s+");
    assert(comment_f2e[0].equals("#"));
    assert(comment_e2f[0].equals("#"));
    p_f2e = Double.parseDouble(comment_f2e[comment_f2e.length-1]);
    p_e2f = Double.parseDouble(comment_f2e[comment_e2f.length-1]);
    // Read target strings:
    f = new SimpleSequence<IString>(true, IStrings.toIStringArray(preproc(f2e_line2.split("\\s+"))));
    e = new SimpleSequence<IString>(true, IStrings.toIStringArray(preproc(e2f_line2.split("\\s+"))));
    // Read alignments:
    f2e = new TreeSet[f.size()];
    e2f = new TreeSet[e.size()];
    initAlign(f2e_line3, f2e, e);
    initAlign(e2f_line3, e2f, f);
  }

  @SuppressWarnings("unchecked")
  public void init(String f2e_line1, String f2e_line2, String f2e_line3)
      throws IOException {
    // Read GIZA prob from comments:
    String[] comment_f2e = f2e_line1.split("\\s+");
    assert(comment_f2e[0].equals("#"));
    p_f2e = Double.parseDouble(comment_f2e[comment_f2e.length-1]);
    // Read target strings:
    f = new SimpleSequence<IString>(true, IStrings.toIStringArray(preproc(f2e_line2.split("\\s+"))));
    e = new SimpleSequence<IString>(true, IStrings.toIStringArray(extractWordsFromAlignment(f2e_line3)));
    // Read alignments:
    f2e = new TreeSet[f.size()];
    e2f = new TreeSet[e.size()];
    initAlign(f2e_line3, f2e, e);
    reverseAlignment(f2e,e2f);
  }

  private static void reverseAlignment(Set<Integer>[] direct, Set<Integer>[] reverse) {
    for(int i=0; i<reverse.length; ++i) {
      reverse[i] = new TreeSet<Integer>(); 
    }
    for(int i=0; i<direct.length; ++i) {
      for(int di : (Collection<Integer>) direct[i]) {
        assert(di < reverse.length);
        reverse[di].add(i);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static List<String> extractWordsFromAlignment(String alignStr) throws IOException {
    String[] tokens = alignStr.split("\\s+");
    List<String> words = new ArrayList<String>();
    assert(tokens[0].equals("NULL"));
    // Read alignmen from string:
    int pos = -1;
    int wpos = -1;
    while(pos+1 < tokens.length) {
      String w = new String(tokens[++pos]);
      if(pos > 1)
        words.add(w);
      //System.err.println("adding: "+w);
      if(!tokens[++pos].equals("({"))
        throw new IOException("Bad GIZA file format at position "+pos+": "+tokens[pos]);
      while(!tokens[++pos].equals("})"))
        ;
      ++wpos;
    }
    return words;
  }

  @SuppressWarnings("unchecked")
  private static void initAlign(String alignStr, Set<Integer>[] align, Sequence<IString> target) throws IOException {
    // Init alignment:
    for(int i=0; i<align.length; ++i)
      align[i] = new TreeSet(); 
    String[] tokens = alignStr.split("\\s+");
    assert(tokens[0].equals("NULL"));
    // Read alignmen from string:
    int pos = -1;
    int wpos = -1;
    while(pos+1 < tokens.length) {
      IString w = new IString(tokens[++pos]);
      if(wpos >= 0 && !target.get(wpos).equals(w))
        throw new IOException("Words not matching at word position "+wpos+", token position "+pos+": "+target.get(wpos)+ " != "+w);
      if(!tokens[++pos].equals("({"))
        throw new IOException("Bad GIZA file format at position "+pos+": "+tokens[pos]);
      while(!tokens[++pos].equals("})")) {
        String curTok = tokens[pos];
        if(curTok.equals("/") || curTok.startsWith("p"))
          continue;
        int wpos2 = Integer.parseInt(curTok)-1;
        if(wpos >= 0) {
          //System.err.println("align: "+wpos2+" -> "+wpos+" "+align.length);
          assert(align[wpos2].size() == 0);
          align[wpos2].add(wpos);
        }
      }
      ++wpos;
    }
  }

  /**
   * Returns a string reresenting the word alignment.
   * It prints many-to-one alignments, unless inverse is true
   * (in which case it prints one-to-many).
   */
  public String toString(boolean inverse) {
    return toString(inverse ? e2f : f2e);
  }

  public static void main(String[] args) {
    if(args.length == 1)
      readUnidirecionalAlignment(args[0]);
    else if(args.length == 2)
      readBidirecionalAlignment(args[0],args[1]);
  }

  @SuppressWarnings("unchecked")
  public static void readUnidirecionalAlignment(String feAlign) { 
    LineNumberReader feReader = null;
    GIZAWordAlignment sent = new GIZAWordAlignment();
    try {
      feReader = new LineNumberReader
            (new InputStreamReader(new GZIPInputStream(new FileInputStream(feAlign))));
      String feLine1, feLine2, feLine3;
      while((feLine1 = feReader.readLine()) != null) {
        feLine2 = feReader.readLine();
        feLine3 = feReader.readLine();
        sent.init(feLine1, feLine2, feLine3);
        System.out.println(sent.f());
        System.out.println(sent.e());
        System.out.println(sent.toString(false));
      }
      feReader.close();
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  public static void readBidirecionalAlignment(String feAlign, String efAlign) { 
    LineNumberReader feReader = null, efReader = null;
    GIZAWordAlignment sent = new GIZAWordAlignment();
    try {
      feReader = new LineNumberReader
            (new InputStreamReader(new GZIPInputStream(new FileInputStream(feAlign))));
      efReader = new LineNumberReader
            (new InputStreamReader(new GZIPInputStream(new FileInputStream(efAlign))));
      String feLine1, feLine2, feLine3, efLine1, efLine2, efLine3;
      for(;;) {
        feLine1 = feReader.readLine(); efLine1 = efReader.readLine();
        if(feLine1 == null || efLine1 == null) {
          if(feLine1 != null || efLine1 != null)
            throw new IOException("Not same number of lines!");
          break;
        }
        feLine2 = feReader.readLine(); efLine2 = efReader.readLine();
        feLine3 = feReader.readLine(); efLine3 = efReader.readLine();
        sent.init(feLine1, feLine2, feLine3, efLine1, efLine2, efLine3);
        System.out.println("fe: "+feLine3);
        System.out.println("ef: "+efLine3);
        System.out.println(sent.toString(false));
        System.out.println(sent.toString(true));
      }
      feReader.close();
      efReader.close();
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
