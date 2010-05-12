///////////////////////////////////////////////////////////////////////////////
// Copyright (C) 2007 University of Texas at Austin and (C) 2005
// University of Pennsylvania and Copyright (C) 2002, 2003 University
// of Massachusetts Amherst, Department of Computer Science.
//
// This software is licensed under the terms of the Common Public
// License, Version 1.0 or (at your option) any subsequent version.
// 
// The license is approved by the Open Source Initiative, and is
// available from their website at http://www.opensource.org.
///////////////////////////////////////////////////////////////////////////////

package edu.stanford.nlp.mt.syntax.mst.rmcd.io;

import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.ling.TaggedWord;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.mt.syntax.mst.rmcd.*;

/**
 * A reader for files in CoNLL format.
 * <p/>
 * <p>
 * Created: Sat Nov 10 15:25:10 2001
 * </p>
 *
 * @author Jason Baldridge
 * @version $Id: CONLLReader.java 112 2007-03-23 19:19:28Z jasonbaldridge $
 * @see edu.stanford.nlp.mt.syntax.mst.rmcd.io.DependencyReader
 */
public class CONLLReader extends DependencyReader {

  protected boolean discourseMode = false;
  
  private static final boolean VERBOSE = false;

  public CONLLReader(DependencyPipe pipe, ParserOptions opts, boolean pretag) throws Exception {
    super(pipe, opts, pretag);
    this.discourseMode = (opts != null) && opts.discourseMode;
  }

  @SuppressWarnings("unchecked")
  public DependencyInstance getNext() throws IOException {

    ArrayList<String[]> lineList = new ArrayList<String[]>();

    String line = inputReader.readLine();
    while (line != null && !line.equals("") && !line.startsWith("*")) {
      lineList.add(line.split(" *\t *"));
      line = inputReader.readLine();
    }

    int length = lineList.size();

    if (length == 0) {
      inputReader.close();
      return null;
    }

    String[] forms = new String[length + 1];
    String[] lemmas = new String[length + 1];
    String[] cpos = new String[length + 1];
    String[] pos = new String[length + 1];
    String[][] feats = new String[length + 1][];
    String[][][] pfeats = new String[length + 1][][];
    String[] deprels = new String[length + 1];
    int[] heads = new int[length + 1];

    String[] toTag = new String[length];
    forms[0] = "<root>";
    lemmas[0] = "<root-LEMMA>";
    cpos[0] = ROOT_CPOS;
    pos[0] = ROOT_POS;
    deprels[0] = "<no-type>";
    heads[0] = -1;

    for (int i = 1; i <= length; i++) {
      String[] info = lineList.get(i-1);
      toTag[i-1] = info[1].intern();
      forms[i] = numberClassing(normalize(info[1]).intern());
      lemmas[i] = numberClassing(normalize(info[2]).intern());
      cpos[i] = normalize(info[3]).intern();
      pos[i] = normalize(info[4]).intern();
      String[] fs = (info[5].equals("") || info[5].equals("_")) ? new String[0] : info[5].split("\\|");
      heads[i] = Integer.parseInt(info[6]);
      String type = info[7];
      if(!labeled || "-".equals(type) || "_".equals(type))
        type =  "<no-type>";
      deprels[i] = type;

      // Distinguish pairwise features from standard features:
      Set<String> featsl = new HashSet<String>();
      Set<String>[] pfeatsl = new HashSet[length+1];
      for(String f : fs) {
        int loc = f.indexOf('_');
        if(loc > 0) {
          String fName = f.substring(0,loc);
          int idx = Integer.parseInt(f.substring(loc+1));
          Set<String> pf = pfeatsl[idx];
          if(pf == null) {
            pf = new HashSet<String>();
            pfeatsl[idx] = pf;
          }
          if(!fName.equals("-") && !fName.equals("_"))
            pf.add(fName);
        } else {
          if(!f.equals("-") && !f.equals("_"))
            featsl.add(f);
        }
      }
      feats[i] = featsl.toArray(new String[featsl.size()]);
      pfeats[i] = new String[length+1][];
      for (int j = 1; j <= length; j++) {
        if(pfeatsl[j] != null)
          pfeats[i][j] = pfeatsl[j].toArray(new String[pfeatsl[j].size()]);
      }
    }

    if(pretag) {
      ArrayList<Word> sent = Sentence.toUntaggedList(toTag);
      List<TaggedWord> tagged = ts.tagSentence(sent);
      for(int i=1; i<=tagged.size(); ++i) {
        String tag = tagged.get(i-1).tag();
        pos[i] = tag;
        cpos[i] = tag.substring(0,1);
      }
    }

    feats[0] = new String[feats[1].length];
    for (int i = 0; i < feats[1].length; i++)
      feats[0][i] = "<root-feat>" + i;

    // The following stuff is for discourse and can be safely
    // ignored if you are doing sentential parsing. (In theory it
    // could be useful for sentential parsing.)
    if (discourseMode) {
      String[][] extended_feats = new String[feats[0].length][length + 1];
      for (int i = 0; i < extended_feats.length; i++) {
        for (int j = 0; j < length + 1; j++)
          extended_feats[i][j] = feats[j][i];
      }

      feats = extended_feats;
    }

    ArrayList<RelationalFeature> rfeats =
         new ArrayList<RelationalFeature>();

    while (line != null && !line.equals("")) {
      rfeats.add(new RelationalFeature(length, line, inputReader));
      line = inputReader.readLine();
    }

    RelationalFeature[] rfeatsList = new RelationalFeature[rfeats.size()];
    rfeats.toArray(rfeatsList);

    // End of discourse stuff.

    DependencyInstance in = new DependencyInstance(pipe, forms, lemmas, cpos, pos, feats, pfeats, deprels, heads, rfeatsList);

    // Bilingual features:
    if(sourceReader != null) {
      DependencyInstance fin = sourceReader.getNext();
      assert(fin != null);
      in.setSourceInstance(fin);
      if(alignReader != null) {
        String alStr = alignReader.readLine();
        String[] ff = fin.getForms();
        String[] ef = in.getForms();
        try {
          WordAlignment al = new SymmetricalWordAlignment(ff, ef, alStr);
          in.setWordAlignment(al);
        } catch(IOException e) {
          System.err.printf("source dep (%d):\n%s\n", fin.length(), Arrays.toString(ff));
          System.err.printf("target dep (%d):\n%s\n", in.length(), Arrays.toString(ef));
          System.err.printf("alignment (%d):\n%s\n", alStr.split("\\s+").length, alStr);
          RuntimeException re = new RuntimeException("Alignment line does not match CONLL files.");
          re.initCause(e);
          throw re;
        }
      }
    }
    if(VERBOSE)
      System.err.println("returning instance: "+ Util.dump(in));
    return in;
  }

  public DependencyInstance readNext(String line) {
    throw new UnsupportedOperationException();
  }

  protected boolean fileContainsLabels(String file) throws IOException {
    System.err.println("Checking: "+file);
    BufferedReader in = new BufferedReader(new FileReader(file));
    String line = in.readLine();
    in.close();
    return (line.trim().length() > 0);
  }

}
