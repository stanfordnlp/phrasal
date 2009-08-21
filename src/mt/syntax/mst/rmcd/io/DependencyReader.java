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

package mt.syntax.mst.rmcd.io;

import edu.stanford.nlp.tagger.maxent.TaggerConfig;
import edu.stanford.nlp.tagger.maxent.TestSentence;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.tagger.maxent.GlobalHolder;

import java.io.*;

import mt.syntax.mst.rmcd.*;

/**
 * A class that defines common behavior and abstract methods for
 * readers for different formats.
 * <p/>
 * <p>
 * Created: Sat Nov 10 15:25:10 2001
 * </p>
 *
 * @author Jason Baldridge
 * @version $Id: DependencyReader.java 112 2007-03-23 19:19:28Z jasonbaldridge $
 */
public abstract class DependencyReader {

  protected static ParserOptions parserOpts;

  protected static String serializedTaggerFile;

  protected static String ROOT_POS = "<root-POS>";
  protected static String ROOT_CPOS = "<root-CPOS>";

  protected DependencyPipe pipe;

  protected BufferedReader inputReader;
  protected DependencyReader sourceReader;
  protected BufferedReader alignReader;

  protected TestSentence ts;
  protected boolean pretag = false;
  protected boolean labeled = true;
  protected boolean trim = false;

  public DependencyReader(DependencyPipe pipe, ParserOptions opts, boolean pretag) throws Exception {
    this.pretag = pretag;
    this.pipe = pipe;
    if(opts != null && opts.trim)
      trim = true;
    System.err.printf("Dependency reader(trim=%s): %s\n", trim, this);

    if(pretag) {
      System.err.println("Loading model: "+serializedTaggerFile);
      TaggerConfig config = new TaggerConfig(new String[] {"-model", serializedTaggerFile});
      MaxentTagger.init(config.getModel(),config);
      ts = new TestSentence(GlobalHolder.getLambdaSolve());
    }
    System.err.println("Pre-tagging: "+pretag);
  }

  public static DependencyReader createDependencyReader(DependencyPipe pipe, String format, ParserOptions opts)
       throws Exception {
    System.err.println("New dependency reader with format: "+format);
    parserOpts = opts;
    boolean pretag = (opts != null) && opts.pretag;

    if (format.equalsIgnoreCase("conll")) {
      return new CONLLReader(pipe, opts, pretag);
    } else if (format.equalsIgnoreCase("plain")) {
      return new PlainReader(pipe, opts, false);
    } else if (format.equalsIgnoreCase("tagged")) {
      return new PlainReader(pipe, opts, true);
    } else {
      throw new UnsupportedOperationException("Not a supported format: " + format);
    }
  }

  public static DependencyReader createDependencyReader(DependencyPipe pipe, String format)
       throws Exception {
    return createDependencyReader(pipe, format, null);
  }

  public boolean startReading(String file, String sourceFile, String alignFile) throws IOException {
    System.err.printf("Start reading: [e=%s] [f=%s] [a=%s]\n", file, sourceFile, alignFile);
    labeled = fileContainsLabels(file);
    inputReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF8"));
    if(sourceFile != null) {
      try {
        sourceReader = createDependencyReader(pipe, "conll", parserOpts);
      } catch(Exception e) {
        RuntimeException re = new RuntimeException();
        re.initCause(e);
        throw re;
      }
      sourceReader.startReading(sourceFile, null, null);
    }
    if(alignFile != null)
      alignReader = new BufferedReader(new InputStreamReader(new FileInputStream(alignFile)));
    return labeled;
  }

  public static void setSerializedTaggerModel(String t) {
    System.err.println("Setting tagger: "+t);
    serializedTaggerFile = t;
  }
  
  protected String numberClassing(String s) {
    //if (s.matches("[0-9]+|[0-9]+\\.[0-9]+|[0-9]+[0-9,]+ *"))
    //  return "<num>";
    return s;
  }

  public String normalize(String s) {
    s = s.trim();
    if(!trim) s = s+" ";
    return s;
  }

  public void setReader(BufferedReader r) {
    inputReader = r;
  }

  public abstract DependencyInstance getNext() throws IOException;
  
  public abstract DependencyInstance readNext(String line) throws IOException;

  protected abstract boolean fileContainsLabels(String filename) throws IOException;
}
