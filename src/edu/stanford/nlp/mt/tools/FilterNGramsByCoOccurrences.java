package edu.stanford.nlp.mt.tools;

import edu.stanford.nlp.mt.base.IString;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

import static java.lang.System.*;

/**
 * 
 * @author danielcer
 *
 */
public class FilterNGramsByCoOccurrences {
	public static final boolean DEBUG = false;
	//public static final String ARPA_TYPE = "arpa";
	//public static final String RAW_TYPE = "raw";
	

	public static BitSet intArrayToSet(int[] arr) {		
		BitSet s = new BitSet();
		for (int i : arr) s.set(i);
		return s;
	}
	
	public static boolean canCoOccur(Map<IString, BitSet> wordSents, String[] words) {
		BitSet intersect = null;
		if (DEBUG)  {
			err.printf("canCoOccur(%s)\n", Arrays.toString(words));
		}
		for (String word : words) {
			BitSet sentIds = wordSents.get(new IString(word));
			if (sentIds == null) return false;
			if (intersect == null) intersect = (BitSet)sentIds.clone();
			else intersect.and(sentIds);
			if (intersect.isEmpty()) return false;
		}

    assert(intersect != null);
		return !intersect.isEmpty();
	}
	
	public static void main(String[] args) throws IOException {
		if (args.length < 3) {
			err.println("Usage\n\tjava FilterNGramsByCoOccurrences (type:arpa/raw) CoOccurenceTable file1, file2 ....");
			exit(-1);
		}
		String type = args[0];
		String cotable = args[1];
		
		if (!type.equals("raw") && !type.equals("arpa")) { err.printf("Unsupported type: %s\n", type); exit(-1); }

		Map<IString, BitSet> wordSents = new HashMap<IString, BitSet>();
		LineNumberReader reader = new LineNumberReader(new FileReader(cotable));
		
		for (String line = reader.readLine(); line != null; line = reader.readLine()) {
			String[] tokens = line.split("\\s+");
			IString word = new IString(tokens[0]);
			int[] sentIds = new int[tokens.length-1];
			for (int i = 1; i < tokens.length; i++) {
				int sentId = Integer.parseInt(tokens[i]);				
				sentIds[i-1] = sentId;
			}
			wordSents.put(word, intArrayToSet(sentIds));
		}
		reader.close();
		for (int i = 2; i < args.length; i++) {
			String filename = args[i];
			if (filename.endsWith(".gz")) {
				reader = new LineNumberReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(filename))));
			} else {
				reader = new LineNumberReader(new FileReader(filename));
			}
			int arpaNgramOrder = -1;
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				String[] words = null;
				if (type.equals("raw")) {
					String[] tokens = line.split("\\s+");
					words = new String[tokens.length-1];
          System.arraycopy(tokens, 0, words, 0, tokens.length - 1);
				} else if (type.equals("arpa")) {
					if (line.startsWith("\\") || line.equals("")) {
						if (line.endsWith("-grams:")) {
							String strId = line.substring(1, line.length()-"-grams:".length());
							System.err.printf("Doing: %s grams\n", strId);
							arpaNgramOrder = Integer.parseInt(strId);
						}
						out.println(line); continue; 
					}
					if (arpaNgramOrder == -1) { out.println(line); continue; }
					
					String[] tokens = line.split("\\s+");
					words = new String[arpaNgramOrder];
          System.arraycopy(tokens, 1, words, 0, arpaNgramOrder);
				}
				if (!canCoOccur(wordSents, words)) {
					err.printf("filtering(%d): %s\n", words.length, Arrays.toString(words));
					continue;
				}
				out.println(line);
			}
			reader.close();
		}
		
	}
}
