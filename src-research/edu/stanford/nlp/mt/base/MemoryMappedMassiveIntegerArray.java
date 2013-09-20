package edu.stanford.nlp.mt.base;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * 
 * @author daniel cer
 */
public class MemoryMappedMassiveIntegerArray {
   static final int CHUNK_SIZE = 1 + (Integer.MAX_VALUE>>1)/(Long.SIZE>>3);
   static final int CHUNK_SHIFT = Integer.numberOfTrailingZeros(CHUNK_SIZE);
   static final int CHUNK_POS_MASK = CHUNK_SIZE-1;
   static final int LONG_SIZE_BYTES = Long.SIZE>>3;
   
   protected final RandomAccessFile fh;
   protected MappedByteBuffer[] buffers;
   
   public MemoryMappedMassiveIntegerArray(String filename, long size) throws IOException {
      fh = new RandomAccessFile(filename, "rw"); 
      int chunks = (int)(size >> CHUNK_SHIFT)+1;
      buffers = new MappedByteBuffer[chunks];
      FileChannel fc = fh.getChannel();
      for (int i = 0; i < buffers.length; i++) {
        if (i + 1 == buffers.length) {
           buffers[i] = fc.map(FileChannel.MapMode.READ_WRITE, (i*1L) * CHUNK_SIZE * LONG_SIZE_BYTES, 
               (size & CHUNK_POS_MASK) * LONG_SIZE_BYTES);
        } else {
          buffers[i] = fc.map(FileChannel.MapMode.READ_WRITE, (i*1L) * CHUNK_SIZE * LONG_SIZE_BYTES, 
              ((long)CHUNK_SIZE) * LONG_SIZE_BYTES);
        }
     }
   }
   
   public void set(long position, long value) {
     int chunk = (int)(position>>CHUNK_SHIFT);
     int chunkpos = (int)(position & CHUNK_POS_MASK) * LONG_SIZE_BYTES;
     buffers[chunk].putLong(chunkpos, value);
   }
   
   public long get(long position) {
     int chunk = (int)(position>>CHUNK_SHIFT);
     int chunkpos = (int)(position & CHUNK_POS_MASK) * LONG_SIZE_BYTES;
     return buffers[chunk].getLong(chunkpos);  
   }
   
   public void close() throws IOException {
     fh.close();
   }
   
   public static void main(String[] args) throws IOException {
     
     File f = new File("5g.map");
     MemoryMappedMassiveIntegerArray mmmia = new MemoryMappedMassiveIntegerArray(f.getAbsolutePath(), CHUNK_SIZE+1);
     mmmia.set(10, 100);
     mmmia.set(11, 1024);
     mmmia.set(0, 5);
     mmmia.set(CHUNK_SIZE, 15);
     assertTrue(mmmia.get(10) == 100);
     assertTrue(mmmia.get(11) == 1024);
     assertTrue(mmmia.get(0) == 5);
     assertTrue(mmmia.get(CHUNK_SIZE) == 15);
     f.delete();
   }
}
