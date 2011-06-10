package edu.stanford.nlp.mt.scripts;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PhrasalMert {
  static public class ConfigSection {
    public ConfigSection(String sectionName, List<String> lines) {
      this.sectionName = sectionName;
      this.lines = lines;
    }

    public void output(BufferedWriter output) 
      throws IOException
    {
      if (sectionName != null && !sectionName.equals("")) {
        output.write("[" + sectionName + "]");
        output.newLine();
      }
      for (String line : lines) {
        output.write(line);
        output.newLine();
      }
    }

    public void update(List<String> lines) {
      this.lines = lines;
    }

    final String sectionName;
    public String getSectionName() { return sectionName; }

    List<String> lines;
  }

  static public class ConfigFile {
    public ConfigFile(List<ConfigSection> sections) {
      this.sections = sections;
    }

    public void outputFile(String filename) 
      throws IOException
    {
      FileWriter fout = new FileWriter(filename);
      outputFile(fout);
      fout.close();
    }

    public void outputFile(Writer output) 
      throws IOException
    {
      BufferedWriter buffered = new BufferedWriter(output);
      outputFile(buffered);
      buffered.flush();
    }

    public void outputFile(BufferedWriter output)
      throws IOException
    {
      for (ConfigSection section : sections) {
        section.output(output);
      }
    }

    public void updateSection(String sectionName, String ... lines) {
      updateSection(sectionName, Arrays.asList(lines));
    }

    public void updateSection(String sectionName, List<String> lines) {
      for (ConfigSection section : sections) {
        if (sectionName.equals(section.getSectionName())) {
          section.update(lines);
          return;
        }
      }
      sections.add(new ConfigSection(sectionName, lines));
    }

    List<ConfigSection> sections;
  }

  public static ConfigFile readConfigFile(String filename) 
    throws IOException
  {
    return readConfigFile(new FileReader(filename));
  }

  public static ConfigFile readConfigFile(Reader input) 
    throws IOException
  {
    return readConfigFile(new BufferedReader(input));
  }

  public static ConfigFile readConfigFile(BufferedReader input) 
    throws IOException
  {
    List<ConfigSection> sections = new ArrayList<ConfigSection>();
    
    String lastKey = "";
    List<String> lines = new ArrayList<String>();
    
    String line;
    while ((line = input.readLine()) != null) {
      String trimmed = line.trim();
      if (trimmed.length() == 0 || line.charAt(0) == '#') {
        lines.add(line);
        continue;
      }
      if (trimmed.charAt(0) == '[' && 
          trimmed.charAt(trimmed.length() - 1) == ']') {
        sections.add(new ConfigSection(lastKey, lines));
        lastKey = line.substring(1, line.length() - 1);
        lines = new ArrayList<String>();
      } else {
        lines.add(line);
      }
    }
    sections.add(new ConfigSection(lastKey, lines));
    return new ConfigFile(sections);
  }

  public static final String PHRASAL_CLASS = "edu.stanford.nlp.mt.Phrasal";

  /**
   * Takes all of the data available in input and redirects it to output.
   */
  public static void connectStreams(InputStream input,
                                    OutputStream output) 
    throws IOException
  {
    BufferedInputStream bufInput = new BufferedInputStream(input);
    BufferedOutputStream bufOutput = new BufferedOutputStream(output);
    byte[] buffer = new byte[1024];
    int bytesRead;
    while ((bytesRead = bufInput.read(buffer)) != -1) {
      bufOutput.write(buffer, 0, bytesRead);
    }
    bufOutput.flush();
  }

  /**
   * Runs the given command.  For each of the filenames stdin, stdout,
   * and stderr that are specified, pipes that file to or from the
   * process.  A null argument for a filename means to not use that pipe.
   */
  public static void runCommand(String command, String stdinFile, 
                                String stdoutFile, String stderrFile) 
    throws IOException, InterruptedException
  {
    Process proc = Runtime.getRuntime().exec(command);

    if (stdinFile != null) {
      OutputStream procStdin = proc.getOutputStream();
      FileInputStream fin = new FileInputStream(stdinFile);
      connectStreams(fin, procStdin);
      procStdin.close();
    }

    proc.waitFor();

    if (stdoutFile != null) {
      InputStream procStdout = proc.getInputStream();
      FileOutputStream fout = new FileOutputStream(stdoutFile);
      connectStreams(procStdout, fout);
      fout.close();
    }

    if (stderrFile != null) {
      InputStream procStderr = proc.getErrorStream();
      FileOutputStream fout = new FileOutputStream(stderrFile);
      connectStreams(procStderr, fout);
      fout.close();
    }
  }

  // Implements the most basic form of PhrasalMert
  // goal: reproduce the effects of 
  // ../scripts/phrasal-mert.pl 4g data/dev/nc-dev2007.tok.fr 
  //   data/dev/nc-dev2007.tok.en bleu phrasal.conf
  public static void main(String[] args) 
    throws IOException, InterruptedException
  {
    if (args.length != 5) {
      System.err.println("Expected args in the format:");
      System.err.println("  memory input reference metric config");
      System.exit(2);
    }
    
    String memory = args[0];
    String inputFile = args[1];
    String referenceFile = args[2];
    String metric = args[3];
    String phrasalConfigFilename = args[4];
    ConfigFile configFile = readConfigFile(phrasalConfigFilename);

    int iteration = 0;
    while (true) {
      String baseName  = "phrasal." + iteration;
      String configName = baseName + ".ini";
      configFile.outputFile(configName);
      String transName = baseName + ".trans";
      String dlogName = baseName + ".dlog";

      StringBuilder phrasalCommand = new StringBuilder();
      phrasalCommand.append("java -mx" + memory + " ");
      phrasalCommand.append(PHRASAL_CLASS + " ");
      phrasalCommand.append("-configFile " + configFile);
      runCommand(phrasalCommand.toString(), inputFile, transName, dlogName);

      // TODO: build combined nbest list here

      // TODO: run mert here

      // TODO: test for convergence here

      ++iteration;
    }
  }
}
