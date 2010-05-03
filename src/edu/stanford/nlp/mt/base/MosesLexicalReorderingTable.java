package edu.stanford.nlp.mt.base;

import java.util.*;
import java.util.zip.GZIPInputStream;
import java.io.*;



/**
 * 
 * @author danielcer
 *
 */
public class MosesLexicalReorderingTable {
	final IntegerArrayIndex index = new DynamicIntegerArrayIndex();
	
	/** 
	 * Reordering types
	 * <pre>
	 *	Monotone with Previous      Monotone with Next
	 *
	 *   e:  E_0 E_1 E_2            e:  E_0 E_1 E_2
	 * f:                         f:         
	 *  F_0  PPP                   F_0  
	 *  F_1     PPP                F_1      PPP
	 *  F_2                        F_2         PPP
	 *
	 * 
	 *	Swap with Previous      Swap with Next
	 *
	 *   e:  E_0 E_1 E_2            e:  E_0 E_1 E_2
	 * f:                         f:         
	 *  F_0      PPP               F_0  
	 *  F_1  PPP                   F_1         PPP      
	 *  F_2                        F_2      PPP   
	 *  
	 * Discontinuous with Prev  Discontinuous with Next
	 *
	 *   e:  E_0 E_1 E_2            e:  E_0 E_1 E_2
	 * f:                         f:         
	 *  F_0      PPP               F_0          PPP
	 *  F_1                        F_1               
	 *  F_2  PPP                   F_2      PPP   
	 *  
	 * </pre> 
	 * 
	 *  NonMonotone: Swap <em>or</em> Discontinuous. 
	 *
	 * @author danielcer
	 *
	 */
	
	
	public enum ReorderingTypes {monotoneWithPrevious, swapWithPrevious, discontinuousWithPrevious, nonMonotoneWithPrevious,
		                  monotoneWithNext, swapWithNext, discontinuousWithNext, nonMonotoneWithNext}
	
	enum ConditionTypes {f, e, fe}
	
	static final ReorderingTypes[] msdPositionMapping = {
			ReorderingTypes.monotoneWithPrevious, 
			ReorderingTypes.swapWithPrevious,
			ReorderingTypes.discontinuousWithPrevious};
	
	static final ReorderingTypes[] msdBidirectionalPositionMapping = {
			ReorderingTypes.monotoneWithPrevious, 
            ReorderingTypes.swapWithPrevious,
            ReorderingTypes.discontinuousWithPrevious,
            ReorderingTypes.monotoneWithNext, 
            ReorderingTypes.swapWithNext, 
            ReorderingTypes.discontinuousWithNext
	};

	static final ReorderingTypes[] monotonicityPositionalMapping = {
			ReorderingTypes.monotoneWithPrevious,
			ReorderingTypes.nonMonotoneWithPrevious
	};
	
	static final ReorderingTypes [] monotonicityBidirectionalMapping = {
			ReorderingTypes.monotoneWithPrevious,
			ReorderingTypes.nonMonotoneWithPrevious,
			ReorderingTypes.monotoneWithNext,
			ReorderingTypes.nonMonotoneWithNext
	};
	
	static final Map<String,Object> fileTypeToReorderingType = new HashMap<String,Object>();
	
	static {
		fileTypeToReorderingType.put("msd-fe", msdPositionMapping);
		fileTypeToReorderingType.put("msd-bidirectional-fe", msdBidirectionalPositionMapping);
		fileTypeToReorderingType.put("monotonicity-fe", monotonicityPositionalMapping);
		fileTypeToReorderingType.put("monotonicity-bidirectional-fe", monotonicityBidirectionalMapping);
		fileTypeToReorderingType.put("msd-f", msdPositionMapping);
		fileTypeToReorderingType.put("msd-bidirectional-f", msdBidirectionalPositionMapping);
		fileTypeToReorderingType.put("monotonicity-f", monotonicityPositionalMapping);
		fileTypeToReorderingType.put("monotonicity-bidirectional-f", monotonicityBidirectionalMapping);
	}
	
	private static final IString internalDelim = new IString("|||");
	
	static final Map<String, ConditionTypes> fileTypeToConditionType = new HashMap<String,ConditionTypes>();
	
	static {
		fileTypeToConditionType.put("msd-fe", ConditionTypes.fe);
		fileTypeToConditionType.put("msd-bidirectional-fe", ConditionTypes.fe);
		fileTypeToConditionType.put("monotonicity-fe", ConditionTypes.fe);
		fileTypeToConditionType.put("monotonicity-bidirectional-fe", ConditionTypes.fe);
		fileTypeToConditionType.put("msd-f", ConditionTypes.fe);
		fileTypeToConditionType.put("msd-bidirectional-f", ConditionTypes.fe);
		fileTypeToConditionType.put("monotonicity-f", ConditionTypes.fe);
		fileTypeToConditionType.put("monotonicity-bidirectional-f", ConditionTypes.fe);
	}
	
	final String filetype;
	final ArrayList<Object> reorderingScores = new ArrayList<Object>();
	
	public final ReorderingTypes[] positionalMapping;
	public final ConditionTypes conditionType;
	
	private int[] mergeInts(int[] array1, int[] array2) {
		int[] combinedInts = new int[array1.length+1+array2.length];
    System.arraycopy(array1, 0, combinedInts, 0, array1.length);
		combinedInts[array1.length] = internalDelim.id;
		for (int i = 0, offset = array1.length + 1; i < array2.length; i++) combinedInts[offset+i] = array2[i];
		return combinedInts;
	}
	
	/**
	 * 
	 */
	public double[] getReorderingScores(Sequence<IString> foreign, Sequence<IString> translation) {
		int[] indexInts = null;
		
		if (conditionType == ConditionTypes.f) {
			indexInts = Sequences.toIntArray(foreign);
		} else if (conditionType == ConditionTypes.e) {
			indexInts = Sequences.toIntArray(translation);
		} else if (conditionType == ConditionTypes.fe) {
			int[] fInts = Sequences.toIntArray(foreign);
			int[] tInts = Sequences.toIntArray(translation);
			indexInts = mergeInts(fInts, tInts);
		}
		
		int idx = index.indexOf(indexInts);
		
		if (idx < 0) return null;
		
		return (double[]) reorderingScores.get(idx);
	}
	
	/**
	 * 
	 * @throws IOException
	 */
	public MosesLexicalReorderingTable(String filename) throws IOException {
		String filetype = init(filename, null);
		this.filetype = filetype;  
		this.positionalMapping = (ReorderingTypes[])fileTypeToReorderingType.get(filetype);
		this.conditionType = fileTypeToConditionType.get(filetype);
		
	}
	
	public MosesLexicalReorderingTable(String filename, String desiredFileType) throws IOException {
		String filetype = init(filename, desiredFileType);
		if (!desiredFileType.equals(filetype)) {
			throw new RuntimeException(String.format("Reordering file '%s' of type %s not %s\n", filename, filetype, desiredFileType));
		}
		this.filetype = filetype;  
		this.positionalMapping = (ReorderingTypes[])fileTypeToReorderingType.get(filetype);
		this.conditionType = fileTypeToConditionType.get(filetype);
		
	}
	private String init(String filename, String type) throws IOException {
		Runtime rt = Runtime.getRuntime();
		long preTableLoadMemUsed = rt.totalMemory()-rt.freeMemory();
		long startTimeMillis = System.currentTimeMillis();
		System.err.printf("Loading Moses Lexical Reordering Table: %s\n", filename);
		ReorderingTypes[] positionalMapping = null;
		ConditionTypes conditionType = null;
		String selectedFiletype = null;
		if (type == null) {
		for (String filetype : fileTypeToReorderingType.keySet()) {
			if (filename.contains(filetype)) {
				positionalMapping = (ReorderingTypes[])fileTypeToReorderingType.get(filetype);
				conditionType = fileTypeToConditionType.get(filetype);
				selectedFiletype = filetype;
				break;
			}
		} } else { 
			positionalMapping = (ReorderingTypes[])fileTypeToReorderingType.get(type); 
			conditionType = fileTypeToConditionType.get(type);
			selectedFiletype = type; 
			}
		
		
		if (positionalMapping == null) {
			throw new RuntimeException(String.format("Unable to determine lexical re-ordering file type for: %s\n", filename));
		}
		
		
		LineNumberReader reader;
		if (filename.endsWith(".gz")) {
			System.err.printf("(unzipping %s)", filename);
			reader = new LineNumberReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(filename)), "UTF-8"));
		} else {
			reader = new LineNumberReader(new InputStreamReader(new FileInputStream(filename), "UTF-8"));
		}
		
		
		for (String line = reader.readLine(); line != null; line = reader.readLine()) {
			StringTokenizer toker = new StringTokenizer(line);
			List<String> phrase1TokenList = new LinkedList<String>();
			do {
				String token = toker.nextToken();
				if ("|||".equals(token)) { 
					break;
				}
				phrase1TokenList.add(token);
			} while (toker.hasMoreTokens());
			
			if (!toker.hasMoreTokens()) {
				throw new RuntimeException(String.format("Additional fields expected (line %d)", reader.getLineNumber()));
			}
			
			List<String> phrase2TokenList = new LinkedList<String>();
			
			if (conditionType == ConditionTypes.fe) {
				do {
					String token = toker.nextToken();
					if ("|||".equals(token)) { 
						break;
					}
					phrase2TokenList.add(token);
				} while (toker.hasMoreTokens());
				
				if (!toker.hasMoreTokens()) {
					throw new RuntimeException(String.format("Additional fields expected (line %d)", reader.getLineNumber()));
				}	
			}
			
			List<String> scoreList = new LinkedList<String>();
			do {
				String token = toker.nextToken();
				if (token.startsWith("|||")) {
					scoreList.clear();
					continue;
				}
				scoreList.add(token);
			}  while (toker.hasMoreTokens());
			
			if (scoreList.size() != positionalMapping.length) {
				throw new RuntimeException(String.format("File type '%s' requires that %d scores be provided for each entry, however only %d were found (line %d)", filetype, positionalMapping.length, scoreList.size(), reader.getLineNumber()));
			}

			int[] indexInts; // = null;
			if (conditionType == ConditionTypes.e || conditionType == ConditionTypes.f) {
				IString[] tokens = IStrings.toIStringArray(phrase1TokenList);
				indexInts = IStrings.toIntArray(tokens);
			} else {
				IString[] fTokens = IStrings.toIStringArray(phrase1TokenList);
				int[] fIndexInts = IStrings.toIntArray(fTokens);
				IString[] eTokens = IStrings.toIStringArray(phrase2TokenList);
				int[] eIndexInts = IStrings.toIntArray(eTokens);
				indexInts = mergeInts(fIndexInts, eIndexInts);
			} 
			
			double[] scores = new double[scoreList.size()];
			int scoreId = 0;
			for (String score : scoreList) {
				try {
					scores[scoreId++] = Math.log(Double.parseDouble(score));
				} catch (NumberFormatException e) {
					throw new RuntimeException(String.format("Can't parse %s as a number (line %d)\n", score, reader.getLineNumber()));
				}
			}
			
			int idx = index.indexOf(indexInts, true);
			if (idx != reorderingScores.size()) {
				throw new RuntimeException(String.format("Somehow indicies have outpaced/underpaced the size of the reorderingScore list (%d != %d).\n", idx, reorderingScores.size()));
			}
			
			reorderingScores.add(scores);
		}
		long postTableLoadMemUsed = rt.totalMemory() - rt.freeMemory();
		long loadTimeMillis = System.currentTimeMillis() - startTimeMillis;
		System.err.printf("Done loading reordering table: %s (mem used: %d MiB time: %.3f s)\n", filename, 
				(postTableLoadMemUsed - preTableLoadMemUsed)/(1024*1024), loadTimeMillis/1000.0);

		System.err.printf("Done loading %s\n", filename);
		
		return selectedFiletype;
	}
	
	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			System.err.printf("Usage:\n\tjava MosesLexicalReorderingTable (lexical reordering filename)\n");
			System.exit(-1);
		}
		
		MosesLexicalReorderingTable mlrt = new MosesLexicalReorderingTable(args[0]);
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		System.out.print("\n>");
		for (String query = reader.readLine(); query != null; query = reader.readLine()) {
			String[] fields = query.split("\\s*\\|\\|\\|\\s*");
			Sequence<IString> foreign = new RawSequence<IString>(IStrings.toIStringArray(fields[0].split("\\s+")));
			Sequence<IString> translation = new RawSequence<IString>(IStrings.toIStringArray(fields[1].split("\\s+")));
			double[] scores = mlrt.getReorderingScores(foreign, translation);
			for (int i = 0; i < scores.length; i++) {
				System.out.printf("%s: %e\n", mlrt.positionalMapping[i], scores[i]);
			}
			System.out.print("\n>");
		}
	}
	
}
