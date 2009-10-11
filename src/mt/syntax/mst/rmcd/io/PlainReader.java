package mt.syntax.mst.rmcd.io;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.ling.TaggedWord;

import mt.syntax.mst.rmcd.*;

public class PlainReader extends DependencyReader {

  public static final boolean VERBOSE = false;
  
  protected boolean discourseMode = false;

  protected final boolean tagged;

  public PlainReader(DependencyPipe pipe, ParserOptions opts, boolean tagged) throws Exception {
    super(pipe, opts, !tagged);
    this.discourseMode = (opts != null) && opts.discourseMode;
    this.tagged = tagged;
  }

  public DependencyInstance getNext() throws IOException {
    String line = inputReader.readLine();
    if (line == null) {
      inputReader.close();
      return null;
    }
    return readNext(line);
  }

  public DependencyInstance readNext(String line) throws IOException {

    //System.err.printf("Reading line: {{{%s}}})\n", line);
    line = StringUtils.chomp(line);
    Sentence<Word> sent = Sentence.toSentence(line.split("\\s+"));
    int length = sent.size();
    
    List<TaggedWord> taggedSent = null;
    if(!tagged) {
      taggedSent = ts.tagSentence(sent);
      assert(length == taggedSent.size());
    }

    String[] forms = new String[length + 1];
    String[] lemmas = new String[length + 1];
    String[] cpos = new String[length + 1];
    String[] pos = new String[length + 1];
    String[][] feats = new String[length + 1][];

    forms[0] = "<root>";
    lemmas[0] = "<root-LEMMA>";
    cpos[0] = ROOT_CPOS;
    pos[0] = ROOT_POS;

    for (int i = 1; i <= length; i++) {
      String formStr, posStr;
      if(tagged) {
        String[] toks = sent.get(i-1).word().split("/", 2);
        formStr = toks[0];
        posStr = toks[1];
      } else {
        formStr = sent.get(i-1).word().trim();
        posStr = taggedSent.get(i-1).tag().trim();
      }
      
      forms[i] = numberClassing(normalize(formStr)).intern();
      pos[i] = normalize(posStr).intern();
      feats[i] = new String[0];

      String lemmaStr = forms[i].length() > 4 ? formStr.substring(0,4) : formStr;
      lemmas[i] = numberClassing(normalize(lemmaStr)).intern();

      String cposStr = posStr.length() > 2 ? posStr.substring(0,2) : posStr;
      cpos[i] = normalize(cposStr).intern();
    }

    feats[0] = new String[feats[1].length];
    for (int i = 0; i < feats[1].length; i++)
      feats[0][i] = "<root-feat>" + i;

    ArrayList<RelationalFeature> rfeats;
    rfeats = new ArrayList<RelationalFeature>();

    RelationalFeature[] rfeatsList = new RelationalFeature[rfeats.size()];
    rfeats.toArray(rfeatsList);

    // End of discourse stuff.

    DependencyInstance in = new DependencyInstance(pipe, forms, lemmas, cpos, pos);
    if(VERBOSE)
      System.err.println("returning instance: "+ Util.dump(in));
    return in;
  }

  protected boolean fileContainsLabels(String file) throws IOException {
    return true;
  }

}
