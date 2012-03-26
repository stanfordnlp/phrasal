package edu.stanford.nlp.mt.base;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

public class MemoryMappedMassiveIntegerArrayTest {

  @Test
  public void testSmallMap() throws IOException {
    File f = new File("small.map");
    MemoryMappedMassiveIntegerArray mmmia = new MemoryMappedMassiveIntegerArray(f.getAbsolutePath(), 1000);
    mmmia.set(10, 100);
    mmmia.set(11, 1024);
    mmmia.set(0, 5);
    mmmia.set(999, 15);
    assertTrue(mmmia.get(10) == 100);
    assertTrue(mmmia.get(11) == 1024);
    assertTrue(mmmia.get(0) == 5);
    assertTrue(mmmia.get(999) == 15);
    mmmia.close();
    f.delete();
  }

  @Test
  public void test10gMap() throws IOException {
    File f = new File("big.map");
    MemoryMappedMassiveIntegerArray mmmia = new MemoryMappedMassiveIntegerArray(f.getAbsolutePath(), 
        MemoryMappedMassiveIntegerArray.CHUNK_SIZE+1);
    mmmia.set(10, 100);
    mmmia.set(11, 1024);
    mmmia.set(0, 5);
    mmmia.set(MemoryMappedMassiveIntegerArray.CHUNK_SIZE, 15);
    assertTrue(mmmia.get(10) == 100);
    assertTrue(mmmia.get(11) == 1024);
    assertTrue(mmmia.get(0) == 5);
    assertTrue(mmmia.get(MemoryMappedMassiveIntegerArray.CHUNK_SIZE) == 15);
    mmmia.close();
    f.delete();
  }
}
