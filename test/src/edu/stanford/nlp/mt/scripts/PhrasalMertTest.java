package edu.stanford.nlp.mt.scripts;

import junit.framework.TestCase;

import java.io.*;
import java.util.*;

public class PhrasalMertTest extends TestCase {
  public final String iniText = 
    "#This is a test\n" +
    "#This is another comment\n" +
    "[section1]\n" +
    "foo\n" +
    "\n" +
    "[section2]\n" +
    "bar\n";

  public final String updatedText = 
    "#This is a test\n" +
    "#This is another comment\n" +
    "[section1]\n" +
    "foo\n" +
    "\n" +
    "[section2]\n" +
    "asdf\n";

  public final String extraSectionText = 
    "#This is a test\n" +
    "#This is another comment\n" +
    "[section1]\n" +
    "foo\n" +
    "\n" +
    "[section2]\n" +
    "asdf\n" +
    "[section3]\n" + 
    "baz\n";

  public File createTempFile() 
    throws IOException
  {
    File tempFile = File.createTempFile("PhrasalMertTest.", ".ini");
    FileWriter fout = new FileWriter(tempFile);
    BufferedWriter bout = new BufferedWriter(fout);
    bout.write(iniText);
    bout.close();
    fout.close();
    return tempFile;
  }

  public void compareFiles(File first, File second) 
    throws IOException
  {
    FileReader fin1 = new FileReader(first);
    BufferedReader bin1 = new BufferedReader(fin1);
    FileReader fin2 = new FileReader(second);
    BufferedReader bin2 = new BufferedReader(fin2);

    while (true) {
      String line1 = bin1.readLine();
      String line2 = bin2.readLine();
      if (line1 == null && line2 == null) 
        break;
      assertTrue(line1 != null);
      assertTrue(line2 != null);
      assertEquals(line1, line2);
    }
  }

  public void compareFileContents(File file, String expectedContents) 
    throws IOException
  {
    StringBuilder contents = new StringBuilder();
    BufferedReader bin = new BufferedReader(new FileReader(file));
    String line;
    while ((line = bin.readLine()) != null) {
      contents.append(line);
      contents.append("\n");
    }

    assertEquals(expectedContents.trim(), contents.toString().trim());
  }

  public void testConnectStreams() 
    throws IOException
  {
    String text = "asdfasdfasdffoo.";
    ByteArrayOutputStream newTextStream = new ByteArrayOutputStream();
    ByteArrayInputStream textInputStream = 
      new ByteArrayInputStream(text.getBytes());
    PhrasalMert.connectStreams(textInputStream, newTextStream);
    String newText = newTextStream.toString();
    assertEquals(text, newText);
  }

  public void testRunCommand() 
    throws IOException, InterruptedException
  {
    File iniFile = createTempFile();
    String command = "cat " + iniFile.getAbsolutePath();
    System.out.println(command);
    PhrasalMert.runCommand(command, null, null, null, false);

    // test stdout
    File copyFile = File.createTempFile("PhrasalMertTest.", ".ini");
    PhrasalMert.runCommand(command, null, copyFile.getAbsolutePath(), 
                           null, false);
    System.out.println("File copied to: " + copyFile);
    compareFiles(iniFile, copyFile);

    // test both stdout and stderr
    File outFile = File.createTempFile("PhrassalMertTest.", ".ini");
    File errFile = File.createTempFile("PhrassalMertTest.", ".ini");
    copyFile.delete();
    command = ("ls " + iniFile.getAbsolutePath() + " " +
               copyFile.getAbsolutePath());
    System.out.println(command);
    PhrasalMert.runCommand(command, null, outFile.getAbsolutePath(),
                           errFile.getAbsolutePath(), false);
    String expectedError = ("ls: " + copyFile.getAbsolutePath() + 
                            ": No such file or directory");
    compareFileContents(outFile, iniFile.getAbsolutePath());
    compareFileContents(errFile, expectedError);

    PhrasalMert.runCommand(command, null, outFile.getAbsolutePath(),
                           errFile.getAbsolutePath(), true);
    compareFileContents(outFile, (expectedError + "\n" + 
                                  iniFile.getAbsolutePath()));
    compareFileContents(errFile, "");


    // test piping in a file
    // test overwriting of output files
    // also, test what happens on an empty stderr
    command = "grep comment";
    PhrasalMert.runCommand(command, iniFile.getAbsolutePath(),
                           outFile.getAbsolutePath(), 
                           errFile.getAbsolutePath(), false);
    compareFileContents(outFile, "#This is another comment");
    compareFileContents(errFile, "");
  }

  public void testConfigFiles() 
    throws IOException
  {
    StringReader sin = new StringReader(iniText);
    PhrasalMert.ConfigFile config = PhrasalMert.readConfigFile(sin);
    StringWriter sout = new StringWriter();
    config.outputFile(sout);
    assertEquals(iniText.trim(), sout.toString().trim());

    // replace the text of a section with new text
    config.updateSection("section2", "asdf");
    sout = new StringWriter();
    config.outputFile(sout);
    assertEquals(updatedText.trim(), sout.toString().trim());

    // add an extra section
    config.updateSection("section3", "baz");
    sout = new StringWriter();
    config.outputFile(sout);
    assertEquals(extraSectionText.trim(), sout.toString().trim());
  }
}
