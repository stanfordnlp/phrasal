/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/**
 @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package edu.stanford.nlp.mt.syntax.mst.rmcd;

import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;

import java.io.*;

public class Alphabet implements Serializable {
  
  private Index<Object> index;
  private boolean growthStopped = false;

  public Alphabet(int capacity) {
    this.index = new HashIndex<Object>(capacity);
  }

  public Alphabet() {
    this(10000);
  }

  // Returns -1 if entry isn't present.
  public int lookupIndex(Object entry) {
    if (entry == null) {
      throw new IllegalArgumentException("Can't lookup \"null\" in an Alphabet.");
    }
    return index.indexOf(entry, !growthStopped);
  }

  public Object get(int idx) {
    return index.get(idx);
  }

  public Object[] toArray() {
    return index.objectsList().toArray();
  }

  public boolean contains(Object entry) {
    return index.contains(entry);
  }

  public int size() {
    return index.size();
  }

  public void stopGrowth() {
    growthStopped = true;
  }

  public void allowGrowth() {
    growthStopped = false;
  }

  public boolean growthStopped() {
    return growthStopped;
  }

  // Serialization

  private static final long serialVersionUID = 1;
  private static final int CURRENT_SERIAL_VERSION = 1;

  private void writeObject(ObjectOutputStream out) throws IOException {
    out.writeInt(CURRENT_SERIAL_VERSION);
    out.writeObject(index);
    out.writeBoolean(growthStopped);
  }

  @SuppressWarnings("unchecked")
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    int serialVersion = in.readInt();
    if(serialVersion != CURRENT_SERIAL_VERSION)
      throw new RuntimeException(String.format("Wrong version of alphabet: %d != %d\n",
         serialVersion, CURRENT_SERIAL_VERSION));
    index = (Index<Object>) in.readObject();
    growthStopped = in.readBoolean();
  }

}
