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

import edu.stanford.nlp.mt.syntax.mst.rmcd.*;

import java.io.*;

/**
 * A class that defines common behavior and abstract methods for writers for
 * different formats.
 * <p/>
 * <p>
 * Created: Sat Nov 10 15:25:10 2001
 * </p>
 * 
 * @author Jason Baldridge
 * @version $Id: DependencyWriter.java 94 2007-01-17 17:05:12Z jasonbaldridge $
 */
public abstract class DependencyWriter {

  protected BufferedWriter writer;

  boolean fileWriter = false;

  public static DependencyWriter createDependencyWriter(String format)
      throws IOException {
    System.err.println("New dependency writer with format: " + format);
    if (format.equalsIgnoreCase("conll")) {
      return new CONLLWriter();
    } else {
      throw new UnsupportedOperationException("Not a supported format: "
          + format);
    }
  }

  public void startWriting(String file) throws IOException {
    writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
        file), "UTF8"));
    fileWriter = true;
  }

  public void finishWriting() throws IOException {
    writer.flush();
    if (fileWriter)
      writer.close();
  }

  public void setStdOutWriter() {
    writer = new BufferedWriter(new OutputStreamWriter(System.out));
    fileWriter = false;
  }

  public void setStdErrWriter() {
    writer = new BufferedWriter(new OutputStreamWriter(System.err));
    fileWriter = false;
  }

  public void setWriter(BufferedWriter w) {
    writer = w;
  }

  public void flush() {
    try {
      writer.flush();
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }
  }

  public abstract void write(DependencyInstance instance) throws IOException;
}
