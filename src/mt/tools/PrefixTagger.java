package mt.tools;

import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.maxent.iis.LambdaSolve;
import edu.stanford.nlp.tagger.maxent.TestSentence;
import edu.stanford.nlp.tagger.maxent.GlobalHolder;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;

import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;
import java.io.File;

import mt.base.IString;
import mt.base.IStrings;

/**
 * Greedy prefix tagger. Its search is exact iff the tagging model is non-sequential
 * (i.e., does not condition on contextual class labels) and only looks at the current
 * word and a left context of <i>prefixSize</i> words to determine the best tag.
 *
 * @author Michel Galley
 */
public class PrefixTagger extends TestSentence {

  public static final boolean CACHE_POS = System.getProperty("cachePOS") != null;

  static {
    System.err.println("cache POS: "+CACHE_POS);
  }

  private final Map<IStringArrayWrapper, Pair<IString,Float>> cache
    = new HashMap<IStringArrayWrapper,Pair<IString,Float>>();
  private final int offset;
  // How many words of left context for POS tagging:
  private int leftWindow = 3;
  private int rightWindow = 1;
  private int len;

  /**
   * Creates a new PrefixTagger. Since PrefixTagger can't determine how many
   * words of context are needed by the tagging model, <i>leftWindow</i> must
   * be manually specified.
   * @param prob probability model
   * @param leftWindow How many words to the left determine the current tag.
   */
  public PrefixTagger(LambdaSolve prob, int leftWindow, int rightWindow) {
    super(prob);
    if(leftWindow < 0 || rightWindow < 0)
      throw new UnsupportedOperationException();
    this.leftWindow = leftWindow;
    this.rightWindow = rightWindow;
    this.offset = -rightWindow;
  }

  private void init(IString[] s) {
    size = s.length;
    this.sent = new ArrayList<String>(size);
    for (int j = 0; j < size; j++)
      this.sent.add(s[j].word());
    localContextScores = new double[size][];
    len = size + leftWindow();
  }

  public void release() {
    cache.clear();
  }


  public int getOrder() {
    return leftWindow;
  }

  public Pair<IString,Float> getBestTag(IString[] s) {
    return getBestTag(s, this.offset);
  }

  /**
   * Determine best tag based on curent word and its immediate predecessors.
   * @param s <i>leftWindow</i> plus one words
   * @param o Offset with respect to last position.
   * @return Best tag and its probability.
   */
  public Pair<IString,Float> getBestTag(IString[] s, int o) {
    int loc = s.length-1+o;

    IStringArrayWrapper aw=null;
    Pair<IString,Float> tag;

    if(CACHE_POS) {
      aw = new IStringArrayWrapper(s);
      tag = cache.get(aw);
      if(tag != null)
      return tag;
    }

    init(s);

    int[] bestTags = new int[len];
    int[] vals = getPossibleValues(loc);
    bestTags[loc] = vals[0];
    this.initializeScorer(sent);
    double[] scores = scoresOf(bestTags, loc);

    int am = ArrayMath.argmax(scores);

    bestTags[loc] = vals[am];
    cleanUpScorer();

    tag = new Pair<IString,Float>(new IString(GlobalHolder.getTags().getTag(bestTags[loc])),(float)scores[am]);
    if(CACHE_POS)
      cache.put(aw,tag);
    return tag;
  }

  /**
   * Tag text file using PrefixTagger.
   * @param textFile File to tag
   */
  public void tagFile(String textFile) {

    for(String line : ObjectBank.getLineIterator(new File(textFile))) {

      line = line.replaceAll("$"," EOS");
      IString[] in = IStrings.toIStringArray(line.split("\\s+"));

      //System.err.println("sent: "+Arrays.toString(in));
      for(int i=0; i<in.length-1; ++i) {
        int from = Math.max(0,i-leftWindow);
        int to = Math.min(i+1+rightWindow,in.length);
        int offset = -rightWindow;
        IString[] seq = new IString[to-from];
        System.arraycopy(in, from, seq, 0, seq.length);
        //System.err.printf("tagging(%d,%d,%d): %s\n",from,to,offset,Arrays.toString(seq));
        Pair<IString,Float> tag = getBestTag(seq);
        if(i>0)
          System.out.print(" ");
        int loc = seq.length-1+offset;
        //System.err.printf("tagging(%d,%d,%d,%s): %s\n",from,to,offset,tag.first.word(),Arrays.toString(seq));
        System.out.print(seq[loc]);
        System.out.print("/");
        System.out.print(tag.first.word());
      }
      System.out.print("\n");
    }
  }

  class IStringArrayWrapper {

    IString[] arr;

    public IStringArrayWrapper(IString ... arr) {
      this.arr = arr;
    }

    public boolean equals(Object o) {
      assert(o instanceof IStringArrayWrapper);
      IStringArrayWrapper aw = (IStringArrayWrapper) o;
      return Arrays.equals(arr,aw.arr);
    }

    public int hashCode() {
      int result = 1;
      for (IString s : arr)
          result = 31 * result + s.id;
      return result;
    }
  }

  public static void main(String[] args) throws Exception {

    if(args.length != 4) {
      System.err.println("Usage: java edu.stanford.nlp.tagger.maxent.PrefixTagger (model) (input-file) (left-window) (right-window)");
      System.exit(1);
    }

    String modelFile = args[0];
    String inputFile = args[1];
    int leftWindow = Integer.parseInt(args[2]);
    int rightWindow = Integer.parseInt(args[3]);

    MaxentTagger tagger = new MaxentTagger(modelFile);
    PrefixTagger ts = new PrefixTagger(GlobalHolder.getLambdaSolve(), leftWindow, rightWindow);
    ts.tagFile(inputFile);
 }
}
