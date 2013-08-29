package edu.stanford.nlp.mt.base;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import static java.lang.System.*;

/**
 * Easy random read-only access to a corpus by line number
 * 
 * @author daniel cer (http://dmcer.net)
 *
 */
public class LineIndexedCorpus extends AbstractList<String> {
   final File fh;
   final RandomAccessFile rfh;
   final List<Long> lineIndex;
   public static boolean VERBOSE = true; // Thang Aug13
   
   public LineIndexedCorpus(String filename) throws IOException {
      fh = new File(filename);
      lineIndex = new ArrayList<Long>();      
      lineIndex.add(0L);
      // there has to be an easier way to find the actual byte locations of line terminators
      if (filename.endsWith("gz")) {
        throw new RuntimeException("Cannot open gzip'd files.");
      }
      InputStreamReader fin = new InputStreamReader(new FileInputStream(fh), "UTF-8");
      long pos = 0;
      char[] charArray = new char[2]; 
      boolean lastPosWasEOL = false;
      int numLines = 0; // Thang Aug13: num lines
      for (int charPoint = fin.read(); charPoint != -1; charPoint = fin.read()) {
         if (lastPosWasEOL) {
            lineIndex.add(pos);
            lastPosWasEOL = false;
            
            if(VERBOSE){ // Thang Aug13
              numLines++;
              if(numLines%100000==0){
                err.print(" (" + numLines/1000 + "K) ");
              }
            }
         }         
         charArray[0] = (char)charPoint;
         String s;
         if (Character.isHighSurrogate(charArray[0])) {
            charArray[1] = (char)fin.read();
            s = new String(charArray, 0, 2);
         } else {
            s = new String(charArray, 0, 1);  
         }
          
         
         if ("\n".equals(s)) {
            lastPosWasEOL = true;            
         }
         byte[] bytes = s.getBytes("UTF-8");
         pos += bytes.length;                  
      }
      lineIndex.add(pos); // position of eof;
      fin.close();
      rfh = new RandomAccessFile(fh, "r");
      
      if(VERBOSE){ // Thang Aug13
        err.println("Done! Num lines = " + numLines);
      }
   }
      
   @Override
   public String get(int index) {
      long startPos = lineIndex.get(index);
      long endPos = lineIndex.get(index+1);
      byte[] rawBytes = new byte[(int)(endPos-startPos)];
      try {
         rfh.seek(startPos);
         rfh.read(rawBytes);      
         return StringUtils.chomp(new String(rawBytes, "UTF-8"));
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public int size() {
      return lineIndex.size()-1;
   }
   
   static public void main(String[] args) throws IOException {
      if (args.length != 1) {
         err.println("Usage:\n\tjava ...LineIndexedCorpus (filename)");
         exit(-1);
      }
      long startTime = System.currentTimeMillis();
      List<String> lic = new LineIndexedCorpus(args[0]);
      long indexTime = System.currentTimeMillis() - startTime;
      err.printf("File size: %d Indexing Time: %.3f s\n", lic.size(), indexTime/1000.0);
      for (int i = 0; i < lic.size(); i++) {
         System.out.printf("%d\t%s\n", i, lic.get(i));
      }
   }
}
