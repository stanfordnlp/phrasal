package edu.stanford.nlp.mt.util;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.ArrayList;

public class ProbingIntegerArrayIndex implements IntegerArrayIndex {
  private ProbingIntegerArrayRawIndex backing;
  private ArrayList<int []> keys;
  private ReentrantReadWriteLock lock;
  private boolean mutable;

  public ProbingIntegerArrayIndex() {
    backing = new ProbingIntegerArrayRawIndex();
    keys = new ArrayList<int[]>();
    lock = new ReentrantReadWriteLock();
    mutable = true;
  }

  public int size() {
    if (!mutable) return backing.size();
    lock.readLock().lock();
    try {
      return backing.size();
    } finally { lock.readLock().unlock(); }
  }

  public int[] get(int index) {
    if (!mutable) return keys.get(index);
    lock.readLock().lock();
    try {
      return keys.get(index);
    } finally { lock.readLock().unlock(); }
  }

  public int indexOf(int[] key) {
    if (!mutable) return backing.find(key);
    lock.readLock().lock();
    try {
      return backing.find(key);
    } finally { lock.readLock().unlock(); }
  }

  public int indexOf(int[] key, boolean add) {
    // Bizarre sematics: ignore add when "locked?"
    if (!add || !mutable) return indexOf(key);
    // Speculative read-only attempt.
    int tried = indexOf(key);
    if (tried != -1) return tried;
    lock.writeLock().lock();
    try {
      int got = backing.findOrInsert(key);
      // Check that we actually got the insert instead of a parallel thread.
      if (got == backing.size() - 1) {
        keys.add(key);
      }
      return got;
    } finally { lock.writeLock().unlock(); }
  }

  // This actually means set immutable.
  public void lock() {
    mutable = false;
  }
}
