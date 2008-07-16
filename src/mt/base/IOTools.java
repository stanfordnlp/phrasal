package mt.base;

import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


/**
 * 
 * @author danielcer
 *
 */
public class IOTools {
	public static List<Sequence<IString>> slurpIStringSequences(String filename) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(filename));
		List<Sequence<IString>> sequences = new ArrayList<Sequence<IString>>();
		
		for (String inline; (inline = reader.readLine()) != null; ) {
			inline = inline.replaceAll("\\s+$", "");
			inline = inline.replaceAll("^\\s+", "");
			Sequence<IString> seq = new RawSequence<IString>(IStrings.toIStringArray(inline.split("\\s+")));
			sequences.add(seq);
		}
		reader.close();
		return sequences;
	}

	public static Set<IString> slurpIStringSet(String filename) throws IOException {
    Set<IString> set = new HashSet<IString>();
    if(filename == null) {
      System.err.println("IOTooks: slurpIStringSet: Warning, no file.");
      return set;
    }
		BufferedReader reader = new BufferedReader(new FileReader(filename));
		
		for (String inline; (inline = reader.readLine()) != null; ) {
			inline = inline.replaceAll("\\s+$", "");
			inline = inline.replaceAll("^\\s+", "");
			IString w = new IString(inline);
			set.add(w);
		}
		reader.close();
		return set;
	}

  public static LineNumberReader getReaderFromFile(String fileName) {
    LineNumberReader reader = null;
    File f = new File(fileName);
    try {
      if (f.getAbsolutePath().endsWith(".gz")) {
        reader = new LineNumberReader
              (new InputStreamReader(new GZIPInputStream(new FileInputStream(f))));
      } else {
         reader = new LineNumberReader(new FileReader(f));
      }
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
    }
    return reader;
  }

  public static PrintStream getWriterFromFile(String outputFile) {
    PrintStream output = null;
    try {
      if(outputFile != null) {
        System.err.println("output file: "+outputFile);
        if(outputFile.endsWith(".gz")) {
          output = new PrintStream(new GZIPOutputStream(new FileOutputStream(outputFile)));
        } else {
          output = new PrintStream(new FileOutputStream(outputFile));
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
    }
    return output;
  }

	public static void addConfigFileProperties(Properties prop, String filename) throws IOException {
		LineNumberReader reader = new LineNumberReader(new FileReader(filename));
    String linePattern = "(\\S+)\\s+=\\s+(\\S+)";
    Pattern pattern = Pattern.compile(linePattern);
		for (String line; (line = reader.readLine()) != null;) {
			if (line.matches("^\\s*$"))
				continue;
			if (line.charAt(0) == '#')
				continue;
			line = line.replaceAll("#.*$", "");
      Matcher matcher = pattern.matcher(line);
      if(matcher.find()) {
        assert(matcher.groupCount()==2);
        prop.setProperty(matcher.group(0), matcher.group(1));
      }
		}
	}
}
