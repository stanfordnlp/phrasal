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

import java.io.*;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.mt.syntax.mst.rmcd.*;

/**
 * A writer to create files in CONLL format.
 * <p/>
 * <p>
 * Created: Sat Nov 10 15:25:10 2001
 * </p>
 *
 * @author Jason Baldridge
 * @version $Id: CONLLWriter.java 94 2007-01-17 17:05:12Z jasonbaldridge $
 * @see edu.stanford.nlp.mt.syntax.mst.rmcd.io.DependencyWriter
 */
public class CONLLWriter extends DependencyWriter {

  private static boolean skipRoot = true;

  public static void skipRoot(boolean s) {
    skipRoot = s;
  }

  public void write(DependencyInstance instance) throws IOException {

    int i = skipRoot ? 1 : 0;
    for (; i < instance.length(); i++) {
      int pi = skipRoot ? i : i+1;
      writer.write(Integer.toString(pi));
      writer.write('\t');
      writer.write(instance.getForm(i).trim());
      writer.write('\t');
      String lemma = instance.getLemma(i).trim();
      writer.write(lemma != null ? lemma : instance.getForm(i).trim());
      writer.write('\t');
      String pos = instance.getPOSTag(i).trim();
      String cpos = instance.getCPOSTag(i).trim();
      writer.write(cpos != null ? cpos : pos);
      writer.write('\t');
      writer.write(pos);
      writer.write('\t');
      String feats = StringUtils.join(instance.getFeats(i),"|");
      if(feats.equals("")) feats = "-";
      writer.write(feats);
      writer.write('\t');
      writer.write(Integer.toString(instance.getHead(i)));
      writer.write('\t');
      writer.write(instance.getDepRel(i));
      writer.write('\t');
      writer.write("-\t-");
      writer.newLine();
    }
    writer.newLine();
    //writer.flush();
  }
}
