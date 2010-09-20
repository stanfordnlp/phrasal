package edu.stanford.nlp.mt.tools;

import java.io.File;
import java.io.FilenameFilter;
import java.io.LineNumberReader;
import java.io.IOException;
import java.util.EnumSet;

import edu.stanford.nlp.mt.base.IOTools;

/**
 * @author Michel Galley
 */
public class MatchedFilesReader {

  protected LineNumberReader[] contents;

  protected String[] ids, lines;

  protected MatchedFilesReader() {
  }

  public static MatchedFilesReader getMatchedFilesByRootName(String rootName) {

    final String rootName2 = rootName.endsWith(".") ? rootName : rootName + ".";
    File directory = new File(rootName2).getParentFile();

    FilenameFilter filter = new FilenameFilter() {
      public boolean accept(File dir, String name) {
        if (!name.startsWith(rootName2))
          return false;
        name = name.replaceFirst(rootName2, "");
        return !name.contains(".");
      }
    };

    File[] files = directory.listFiles(filter);
    return getMatchedFiles(files);
  }

  public static MatchedFilesReader getMatchedFilesByEnum(String rootName,
      EnumSet<?> es) {

    String rootName2 = rootName.endsWith(".") ? rootName : rootName + ".";

    File[] files = new File[es.size()];
    int i = -1;
    for (Object e : es)
      files[++i] = new File(rootName2 + e.toString());

    return getMatchedFiles(files);
  }

  public static MatchedFilesReader getMatchedFiles(String[] fileNames) {
    File[] files = new File[fileNames.length];
    for (int i = 0; i < fileNames.length; i++) {
      files[i] = new File(fileNames[i]);
    }
    return getMatchedFiles(files);
  }

  public static MatchedFilesReader getMatchedFiles(File[] files) {
    MatchedFilesReader mf = new MatchedFilesReader();
    mf.contents = new LineNumberReader[files.length];

    for (int i = 0; i < files.length; i++)
      mf.contents[i] = IOTools.getReaderFromFile(files[i].getName());

    return mf;
  }

  public boolean nextLine() {

    boolean done = true;

    for (int i = 0; i < contents.length; i++) {
      try {
        if ((lines[i] = contents[i].readLine()) != null)
          done = false;
      } catch (IOException ioe) {
        return false;
      }
    }

    return done;
  }

  public String getLine(Enum<?> e) {
    return lines[e.ordinal()];
  }

  public String getLine(int idx) {
    return lines[idx];
  }
}
