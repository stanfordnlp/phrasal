package edu.stanford.nlp.mt.base;

import java.util.*;
import java.util.zip.GZIPInputStream;
import java.io.*;

import edu.stanford.nlp.mt.decoder.feat.IsolatedPhraseFeaturizer;
import edu.stanford.nlp.mt.decoder.util.Scorer;


/**
 * 
 * @author Daniel Cer
 */
public class MosesPhraseTable<FV> extends AbstractPhraseGenerator<IString,FV> implements PhraseTable<IString> {

  public static final String FIVESCORE_PHI_t_f = "phi(t|f)";
	public static final String FIVESCORE_LEX_t_f = "lex(t|f)";
	public static final String FIVESCORE_PHI_f_t = "phi(f|t)";
	public static final String FIVESCORE_LEX_f_t = "lex(f|t)";
	public static final String ONESCORE_P_t_f = "p(t|f)";
	public static final String FIVESCORE_PHRASE_PENALTY = "phrasePenalty";
	public static final String[] CANONICAL_FIVESCORE_SCORE_TYPES = { 
		FIVESCORE_PHI_t_f, FIVESCORE_LEX_t_f, FIVESCORE_PHI_f_t, FIVESCORE_LEX_f_t, FIVESCORE_PHRASE_PENALTY };
	public static final String[] CANONICAL_ONESCORE_SCORE_TYPES = {ONESCORE_P_t_f};

  public static final String TRIE_INDEX_PROPERTY = "TriePhraseTable";
  public static final boolean TRIE_INDEX = Boolean.parseBoolean(System.getProperty(TRIE_INDEX_PROPERTY, "false"));

  public static final String DISABLED_SCORES_PROPERTY = "disableScores";
  public static final String DISABLED_SCORES = System.getProperty(DISABLED_SCORES_PROPERTY);

  public static final String CUSTOM_SCORES_PROPERTY = "customScores";
  public static final String CUSTOM_SCORES = System.getProperty(CUSTOM_SCORES_PROPERTY);

  static IntegerArrayIndex foreignIndex, translationIndex;

  static String[] customScores;

  final String[] scoreNames;
	String name;

  static {
    List<String> l = new ArrayList<String>();
    // Custom score names:
    if(CUSTOM_SCORES != null) {
      for(String el : CUSTOM_SCORES.split(",")) {
        if(el.equals("phi_tf")) { l.add(FIVESCORE_PHI_t_f); } else
        if(el.equals("phi_ft")) { l.add(FIVESCORE_PHI_f_t); } else
        if(el.equals("lex_tf")) { l.add(FIVESCORE_LEX_t_f); } else
        if(el.equals("lex_ft")) { l.add(FIVESCORE_LEX_f_t); } else
        if(el.equals("p_tf")) { l.add(ONESCORE_P_t_f); } else
        l.add(el);
      }
      customScores = l.toArray(new String[l.size()]);
    }
		createIndex(false);
  }

  // Originally, PharaohPhraseTables were backed by a nice simple
	// HashMap from a foreign sequence to a list of translations.
	//	
	// However, this resulted in a phrase table that only 
	// occupies 113 MiB on disk requiring 1304 MiB of RAM, ugh.
	//
	// So...we now have the slightly more evil, but hopefully not
	// too evil, implementation below.
	//
	// To compare, for the phrase table huge/filtered-mt03/phrase-table.0-0:
	//
	// with hash maps: 
	//
	// java 6 32-bit 862 MiB 
	// java 6 64-bit 1304 MiB
	//
	// with new implementation:
	//
	// java 6 32-bit  160 MiB
	// java 6 64-bit  254 MiB
	/////////////////////////////////////////////////////////////////
	
	int longestForeignPhrase;
	
	protected static class IntArrayTranslationOption implements Comparable<IntArrayTranslationOption> {
		final int[] translation;
		final float[] scores;
		final PhraseAlignment alignment;
    final int id;
		
		public IntArrayTranslationOption(int id, int[] translation, float[] scores, PhraseAlignment alignment) {
      this.id = id;
			this.translation = translation;
			this.scores = scores;
			this.alignment = alignment;
		}
		
		@Override
		public int compareTo(IntArrayTranslationOption o) {
			return (int) Math.signum(o.scores[0] - scores[0]);
		}
	}
	
	final ArrayList<List<IntArrayTranslationOption>> translations;
	
	private float[] stringProbListToFloatLogProbArray(List<String> sList) {
		float[] fArray = new float[sList.size()];
		int i = 0;
		for (String s : sList) {
			float f = (float)Math.log(Float.parseFloat(s));
			if (f != f) {
				throw new RuntimeException(String.format("Bad phrase table. %s parses as (float) %f", s, f));
			}
			fArray[i++] = f;
		}
		return fArray;
	}
		
	protected void addEntry(Sequence<IString> foreignSequence, Sequence<IString> translationSequence, PhraseAlignment alignment, 
			float[] scores) {
		int[] foreignInts = Sequences.toIntArray(foreignSequence);
		int[] translationInts = Sequences.toIntArray(translationSequence);
		int fIndex = foreignIndex.indexOf(foreignInts, true);
    int eIndex = translationIndex.indexOf(translationInts, true);
    int id = translationIndex.indexOf(new int[] {fIndex, eIndex}, true);
		/*System.err.printf("foreign ints: %s translation ints: %s\n", Arrays.toString(foreignInts),
				Arrays.toString(translationInts));
		System.err.printf("fIndex: %d\n", fIndex); */
		if (translations.size() <= fIndex) {
			translations.ensureCapacity(fIndex+1);
			while (translations.size() <= fIndex) translations.add(null); 			
		}
		List<IntArrayTranslationOption> intTransOpts = translations.get(fIndex);
		if (intTransOpts == null) {
			intTransOpts = new LinkedList<IntArrayTranslationOption>();
			translations.set(fIndex, intTransOpts);
		}
		intTransOpts.add(new IntArrayTranslationOption(id, translationIndex.get(eIndex), scores, alignment));
	}

  /**
	 * 
	 * @throws IOException
	 */
	public MosesPhraseTable(IsolatedPhraseFeaturizer<IString, FV> phraseFeaturizer, Scorer<FV> scorer, String filename) throws IOException {
		super(phraseFeaturizer, scorer);
    File f = new File(filename);
		name = String.format("Pharaoh(%s)", f.getName());
		//arrayIndex = trieIndex ? new TrieIntegerArrayIndex() : new DynamicIntegerArrayIndex();
		translations = new ArrayList<List<IntArrayTranslationOption>>();
		int countScores = init(f);
		scoreNames = getScoreNames(countScores);
	}
	
	private String[] getScoreNames(int countScores) {
		String[] scoreNames;
		if (customScores != null) {
			scoreNames = customScores;
		} else if (countScores == 5) {
			scoreNames = CANONICAL_FIVESCORE_SCORE_TYPES;
		} else if (countScores == 1) {
			scoreNames = CANONICAL_ONESCORE_SCORE_TYPES;
		} else {
			scoreNames = new String[countScores];
			for (int i = 0; i < countScores; i++) {
				scoreNames[i] = String.format("%d.UnkTScore", i);
			}
		}

    System.err.println("Disabled scores: "+DISABLED_SCORES);
    if(DISABLED_SCORES != null)
      for(String istr : DISABLED_SCORES.split(",")) {
        int i = Integer.parseInt(istr);
        if(i < scoreNames.length) {
          System.err.printf("Feature %s disabled.\n", scoreNames[i]);
          scoreNames[i] = null;
        }
      }

    return scoreNames;
	}
	
	private int init(File f) throws IOException {
    System.gc();
		Runtime rt = Runtime.getRuntime();
		long prePhraseTableLoadMemUsed = rt.totalMemory()-rt.freeMemory();
		long startTimeMillis = System.currentTimeMillis();
				
		LineNumberReader reader;
		if (f.getAbsolutePath().endsWith(".gz")) {
			reader = new LineNumberReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(f)), "UTF-8"));
		} else {
			reader = new LineNumberReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
		}
		int countScores = -1;
		for (String line; (line = reader.readLine()) != null; ) {
      //System.err.println("line: "+line);
      StringTokenizer toker = new StringTokenizer(line);
			List<String> foreignTokenList = new LinkedList<String>();
			do {
				String token = toker.nextToken();
				if ("|||".equals(token)) { 
					break;
				}
				foreignTokenList.add(token);
			} while (toker.hasMoreTokens());
			
			if (!toker.hasMoreTokens()) {
				throw new RuntimeException(String.format("Additional fields expected (line %d)", reader.getLineNumber()));
			}
			
			List<String> translationTokenList = new LinkedList<String>();
			
			do {
				String token = toker.nextToken();
				if ("|||".equals(token)) {
					break;
				}
				translationTokenList.add(token);
			}  while (toker.hasMoreTokens());
			
			if (!toker.hasMoreTokens()) {
				throw new RuntimeException(String.format("Additional fields expected (line %d)", reader.getLineNumber()));
			}
			List<String> constilationList = new LinkedList<String>();
			List<String> scoreList = new LinkedList<String>();
      boolean first=true;
      do {
				String token = toker.nextToken();
				if (token.startsWith("|||")) {
					constilationList.addAll(scoreList);
					scoreList = new LinkedList<String>();
          first = false;
          continue;
				}
        if(!first)
          scoreList.add(token);
			}  while (toker.hasMoreTokens());
			
			
			
			IString[] foreignTokens = IStrings.toIStringArray(foreignTokenList);
			IString[] translationTokens = IStrings.toIStringArray(translationTokenList);
			
			if (countScores == -1) {
				countScores = scoreList.size();
			} else if (countScores != scoreList.size()) {
				throw new RuntimeException(String.format(
						"Error (line %d): Each entry must have exactly the same number of translation\n"+
						"scores per line. Prior entries had %d, while the current entry has %d:", reader.getLineNumber(), countScores, scoreList.size()));
			}
			Sequence<IString> foreign = new SimpleSequence<IString>(true, foreignTokens);
			Sequence<IString> translation = new SimpleSequence<IString>(true, translationTokens);
			float[] scores; //= null;
			try {
				scores = stringProbListToFloatLogProbArray(scoreList);
			} catch (NumberFormatException e) {
				throw new RuntimeException(String.format(
						"Error on line %d: '%s' not a list of numbers", reader.getLineNumber(),
						scoreList));
			}
			
			StringBuilder constilationB = new StringBuilder();
			{ int idx = -1; for (String t : constilationList) { idx++;
				if (idx > 0) constilationB.append(";");
				constilationB.append(t);
			} }
		
			String constilationBStr = constilationB.toString();
			if (constilationBStr.equals("")) {
      	addEntry(foreign, translation, null, scores);
			} else {
      	addEntry(foreign, translation, PhraseAlignment.getPhraseAlignment(constilationBStr), scores);
			}

			if (foreign.size() > longestForeignPhrase) {
				longestForeignPhrase = foreign.size();
			}
			/*
			if (reader.getLineNumber() % 10000 == 0) {
				System.out.printf("linenumber: %d\n", reader.getLineNumber());
				long memUsed = rt.totalMemory() - rt.freeMemory();
				System.out.printf("mem used: %d\n", memUsed/(1024*1024));
			} */
			//System.out.printf("foreign: '%s' english: '%s' scores: %s\n",
			//		foreign, translation, Arrays.toString(scores));
		}
		
		reader.close();
		
		System.gc();
		
		// print some status information
		long postPhraseTableLoadMemUsed = rt.totalMemory() - rt.freeMemory();
		long loadTimeMillis = System.currentTimeMillis() - startTimeMillis;
		System.err.printf("Done loading pharoah phrase table: %s (mem used: %d MiB time: %.3f s)\n", f.getAbsolutePath(), 
				(postPhraseTableLoadMemUsed - prePhraseTableLoadMemUsed)/(1024*1024), loadTimeMillis/1000.0);
		return countScores;
	}
	
	@Override
	public int longestForeignPhrase() {
		return longestForeignPhrase;
	}
	
	@Override
	public List<TranslationOption<IString>> getTranslationOptions(Sequence<IString> foreignSequence) {
		RawSequence<IString> rawForeign = new RawSequence<IString>(foreignSequence);
		int[] foreignInts = Sequences.toIntArray(foreignSequence, IString.identityIndex());		
		int fIndex = foreignIndex.indexOf(foreignInts);
		if (fIndex == -1) return null;
		List<IntArrayTranslationOption> intTransOpts = translations.get(fIndex);
		List<TranslationOption<IString>> transOpts = new ArrayList<TranslationOption<IString>>(intTransOpts.size());
		//int intTransOptsSize = intTransOpts.size();
		//for (int i = 0; i < intTransOptsSize; i++) {
		for (IntArrayTranslationOption intTransOpt : intTransOpts) {
			//IntArrayTranslationOption intTransOpt = intTransOpts.get(i);
			//System.out.printf("%d:%f\n", i, intTransOpt.scores[0]);
			RawSequence<IString> translation = new RawSequence<IString>(intTransOpt.translation, 
					IString.identityIndex());
			transOpts.add( 
					new TranslationOption<IString>(intTransOpt.id, intTransOpt.scores, scoreNames,
							translation,
							rawForeign, intTransOpt.alignment));
    }
		return transOpts;
	}
	
	static public void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.out.println("Usage:\n\tjava ...MosesPhraseTable (phrasetable file) (entry to look up)");
			System.exit(-1);
		}

    String model = args[0]; String phrase = args[1];
    long startTimeMillis = System.currentTimeMillis();
		System.out.printf("Loading phrase table: %s\n", model);
		MosesPhraseTable<String> ppt = new MosesPhraseTable<String>(null, null, model);
    long totalMemory = Runtime.getRuntime().totalMemory()/(1<<20);
    long freeMemory = Runtime.getRuntime().freeMemory()/(1<<20);
    double totalSecs = (System.currentTimeMillis() - startTimeMillis)/1000.0;
    System.err.printf("size = %d, secs = %.3f, totalmem = %dm, freemem = %dm\n", 
      foreignIndex.size(), totalSecs, totalMemory, freeMemory);

		List<TranslationOption<IString>> translationOptions = 
			ppt.getTranslationOptions(new SimpleSequence<IString>(IStrings.toIStringArray(phrase.split("\\s+"))));
		
		System.out.printf("Phrase: %s\n", phrase);
		
		if (translationOptions == null) {
			System.out.printf("No translation options found.");
			System.exit(-1);
		}
		
		System.out.printf("Options:\n");
		for (TranslationOption<IString> opt : translationOptions) {
			System.out.printf("\t%s : %s\n", opt.translation, Arrays.toString(opt.scores));
		}		
	}


	@Override
	public String getName() {
		return name;
	}
	
	@Override
	public String toString() {
		return getName();
	}
	
	@Override
	public void setCurrentSequence(Sequence<IString> foreign,
			List<Sequence<IString>> tranList) {
		// no op
  }

  public static void createIndex(boolean withGaps) {
    foreignIndex = (withGaps || TRIE_INDEX) ? new TrieIntegerArrayIndex() : new DynamicIntegerArrayIndex();
    translationIndex = new DynamicIntegerArrayIndex();
  }

}
