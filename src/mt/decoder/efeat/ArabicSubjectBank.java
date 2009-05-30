package mt.decoder.efeat;

import java.io.*;
import java.util.*;

import mt.base.Sequence;
import mt.base.SimpleSequence;
import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;
import edu.stanford.nlp.util.Pair;

public class ArabicSubjectBank {
	private static ArabicSubjectBank thisInstance = null;
	private static Map subjectBank = null;
	private static boolean isLoaded = false;
	private static final String stopSymbol = "O";
	private static final int tokensPerInputLine = 3;
	public static final int maxSubjectLength = 5;
	
	private static final String SUBJECT_START = "NP_SUBJ_START";
	private static final String SUBJECT_INSIDE = "NP_SUBJ_IN";
	private static final String SUBJECT_END = "NP_SUBJ_END";
	private static final String SINGLETON = "NP_SUBJ_SINGLE";
	
	protected ArabicSubjectBank() {}
	
	public static ArabicSubjectBank getInstance() {
		if(thisInstance == null)
			thisInstance = new ArabicSubjectBank();
		return thisInstance;
	}
	
	public void load(String rawFile, String crfFile) {
		if(isLoaded) return;
		try {
			BufferedReader rawReader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(rawFile))));
			BufferedReader crfReader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(crfFile))));
			subjectBank = new HashMap<Sequence<IString>,List<Pair<Integer,Integer>>>();
			
			//Re-factor using Pairs
			List<Pair<Integer,Integer>> sentenceSubjects = null;
			int sentIdx = 0;
			int subjStartIdx = -1;
			for(int lineId = 1; crfReader.ready(); lineId++) {
				String line = crfReader.readLine();
				StringTokenizer st = new StringTokenizer(line);
				if(st.countTokens() == 0)
					continue;
				else if(st.countTokens() != tokensPerInputLine) {
					System.err.printf("*!arabicsubjectbank: File format problem at line %d\n",lineId);
					break;
				}
				
				String word = st.nextToken();
				if(word.equals(stopSymbol)) {
					if(sentenceSubjects != null) {
						if(rawReader.ready()) {
							String key = rawReader.readLine();
              String[] tokens = key.split("\\s+");
              Sequence<IString> foreign = new SimpleSequence<IString>(true, IStrings
                  .toIStringArray(tokens));
							subjectBank.put(foreign, sentenceSubjects);
						} else {
							throw new RuntimeException("*!arabicsubjectbank: Mismatch between raw file and crf file");
						}
					}
					sentenceSubjects = new ArrayList<Pair<Integer,Integer>>();
					sentIdx = 0;
					subjStartIdx = -1;
					continue;
				}
				
				String correctClass = st.nextToken(); //Discard the 'answer' class

				String predClass = st.nextToken();
				if(predClass.equals(SINGLETON)) {
					sentenceSubjects.add(new Pair<Integer,Integer>(sentIdx,sentIdx));
				} else if(predClass.equals(SUBJECT_START)) {
					subjStartIdx = sentIdx;
				} else if(predClass.equals(SUBJECT_INSIDE)) {
					//continue
				} else if(predClass.equals(SUBJECT_END)) {
					if(subjStartIdx != -1 && (sentIdx - subjStartIdx) < maxSubjectLength) {
						sentenceSubjects.add(new Pair<Integer,Integer>(subjStartIdx,sentIdx));
					}
					subjStartIdx = -1;
				}
				sentIdx++;
			}
			
			if(sentenceSubjects != null) {
				if(rawReader.ready()) {
          String key = rawReader.readLine();
          String[] tokens = key.split("\\s+");
          Sequence<IString> foreign = new SimpleSequence<IString>(true, IStrings
              .toIStringArray(tokens));
          subjectBank.put(foreign, sentenceSubjects);
				} else {
					throw new RuntimeException("*!arabicsubjectbank: Mismatch between raw file and crf file");
				}
			}
			
			crfReader.close();
			rawReader.close();
			isLoaded = true;
		} catch (FileNotFoundException e) {
			System.err.printf("*!arabicsubjectbank: Could not load %s\n", crfFile);
		} catch (IOException e) {
			System.err.println("*!arabicsubjectbank: Failed to read file\n");
		}
	}
	
	public List<Pair<Integer,Integer>> subjectsForSentence(Sequence<IString> foreign) {
		if(subjectBank == null)
			throw new RuntimeException("*!arabicsubjectbank: Subject bank not initialized");

		return (List<Pair<Integer,Integer>>) subjectBank.get(foreign);
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		ArabicSubjectBank asb = ArabicSubjectBank.getInstance();
		String rawFile = "/home/rayder441/sandbox/test.raw";
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(rawFile))));
			asb.load(rawFile,"/home/rayder441/sandbox/test.crf");
			
			while(br.ready()) {
				String sent = br.readLine();
        String[] tokens = sent.split("\\s+");
        Sequence<IString> foreign = new SimpleSequence<IString>(true, IStrings
            .toIStringArray(tokens));
				List<Pair<Integer,Integer>> subjs = asb.subjectsForSentence(foreign);
				
				System.out.printf("Sentence(%s) -- (%d) subjects\n", sent,subjs.size());
				Iterator<Pair<Integer,Integer>> itr = subjs.iterator();
				while(itr.hasNext()) {
					Pair<Integer,Integer> subj = itr.next();
					int start = subj.first();
					int stop = subj.second();
					
					System.out.printf(" subj: %d to %d\n",start,stop);
				}
			}
			
			br.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
