package edu.stanford.nlp.mt.util;

import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class BasicNBestList implements Iterator<List<BasicNBestEntry> > {
  private LineNumberReader reader;
  private BasicNBestEntry start;

  public BasicNBestList(String filename) throws IOException {
    reader = IOTools.getReaderFromFile(filename);
    String got = reader.readLine();
    if (got != null) {
      start = new BasicNBestEntry(got);
    }
  }

  public boolean hasNext() { return start != null; }

  public List<BasicNBestEntry> next() {
    if (start == null) {
      throw new NoSuchElementException();
    }
    ArrayList<BasicNBestEntry> list = new ArrayList<BasicNBestEntry>();
    list.add(start);
    String got;
    try {
      while ((got = reader.readLine()) != null) {
        BasicNBestEntry entry = new BasicNBestEntry(got);
        if (start.getNumber() != entry.getNumber()) {
          start = entry;
          return list;
        }
        list.add(entry);
      }
      start = null;
    // Silly java requires exception declaration, but does not allow overrides to throw more
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
    }
    return list;
  }

  public void remove() {
    throw new UnsupportedOperationException();
  }
}
