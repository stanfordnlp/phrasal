package edu.stanford.nlp.mt.process.fr;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.StringUtils;


/**
 * A sentence parsed by Bonsai.  Concretely, a BonsaiEntry for each word.
 * 
 * @author kevinreschke
 *
 */
public class BonsaiParse {

	private List<BonsaiEntry> entries;
	
	/**
	 * Generate parse from list of entry strings.  The list come from a block of lines
	 *  from the output of the Bonzai parser.
	 *  
	 * E.g.:
	 *  1 trinijove cln CL  CLS s=suj 3 suj _ _
	 *	2 a avoir V V m=ind|n=s|p=3|t=pst 3 aux_tps _ _
     *  3 aidé  aider V VPP g=m|m=part|n=s|t=past 0 root  _ _
     *  4 6 6 D DET s=card  3 obj _ _
     *  5 000 000 D DET s=card  6 det _ _
     *  6 jeunes  jeune N NC  n=p|s=c 3 mod _ _
     *  7 à à P P _ 6 dep _ _
     *  8 risque  risque  N NC  g=m|n=s|s=c 7 obj _ _
	 *  
	 * @param entryStrings
	 */
	public BonsaiParse(List<String> entryStrings) {
		entries = new ArrayList<BonsaiEntry>();
		for(String entryString : entryStrings) {
			entries.add(BonsaiEntry.fromLine(entryString));
		}
	}
	
	
	/** access entry via 1-based index, 
	 *    return null for root entry (index == 0) */
	public BonsaiEntry getEntry(int index) {
		if(index == 0) return null; //root entry
		else
			return entries.get(index - 1);
	}
	
	public List<Correction> getAdjCorrections() {
		List<Correction> corrs = new ArrayList<Correction>();
		
		for(BonsaiEntry entry : entries) {
			if(entry.isAdj()) {
				Correction corr = new Correction(entry);
				BonsaiEntry noun = getNounForAdj(entry);
				if(noun == null) {
					//no-op: corr has no changes
				}
				else {
					//
					//correct gender and number.
					//
					//Note: if feature is null, we don't correct it.
					//        This is because some adjs don't have different
					//        forms, thus they are listed without these features
					if(entry.gender != null && !entry.gender.equals(noun.gender)) {
						corr.correctedGender = noun.gender;
					}
					if(entry.number != null && !entry.number.equals(noun.number)) {
						corr.correctedNumber = noun.number;
					}
				}
				corrs.add(corr);
			}
		}
		
		return corrs;
	}
	
	//follow dependencies; retrieve noun for this adj
	// return null if none found
	private BonsaiEntry getNounForAdj(BonsaiEntry adj) {
		final Set<String> followable = new HashSet<String>();
		followable.add("mod");
		followable.add("dep-coord");
		followable.add("coord");
		
		BonsaiEntry cur = adj;
		while(true) {
			if(cur == null) {
				return null;
			}
			else if(cur.isNoun()) {
				//found the noun, return it
				return cur;
			}
			else if("être".equals(cur.lemma)) {
				//found etre, return the subj noun
				return getSubjForVerb(cur);
			}
			else if(followable.contains(cur.relation)) {
				//follow the dependency
				cur = getEntry(cur.govIndex);
			} else {
				return null;
			}
		}
	}
	
	//follow dependencies; retrieve noun subject of this verb
	// return null in none found
	private BonsaiEntry getSubjForVerb(BonsaiEntry verb) {
		for(BonsaiEntry entry : entries) {
			if(entry.isNoun() && "suj".equals(entry.relation) && entry.govIndex == verb.index) {
				return entry;
			}
		}
		return null;
	}
	
	//follow dependencies; retrieve noun subject of this verb
	// return null in none found
	private BonsaiEntry getObjForVerb(BonsaiEntry verb) {
		for(BonsaiEntry entry : entries) {
			if(entry.isNoun() && "obj".equals(entry.relation) && entry.govIndex == verb.index) {
				return entry;
			}
		}
		return null;
	}

	
	/**
	 * Note (kevin): We found that participle corrections are not very useful for two reasons:
	 *  1) In the simple verbal case (past tense or passive), the output is correct already
	 *  2) In the adjectival case, finding the dependent noun is unreliable, leading to nearly random precision.
	 * Consequently, we don't use participle corrections.
	 */
	private List<Correction> getParticipleCorrections() {
		List<Correction> corrs = new ArrayList<Correction>();
				
		for(BonsaiEntry entry : entries) {
			if(entry.isParticiple()) {
				Correction corr = new Correction(entry);
                BonsaiEntry noun = null;
								
				//CASE 1: participle modifies noun directly
				//  E.g. 
				//		10  un  un  D DET g=m|n=s|s=ind 11  det _ _
				//		11  rapport rapport N NC  g=m|n=s|s=c 9 obj _ _
				//		12  détaillé  détailler V VPP g=m|m=part|n=s|t=past 11  mod _ _
				//  In this case, participle agrees with noun.
				noun = getNounModifiedByParticiple(entry);
								
				//CASE 2: participle follow etre past tense auxiliary
				//  In this case, participle agrees with subject
				if(noun == null && participleWithPastTenseEtre(entry)) {
					noun = getSubjForVerb(entry);					
				}
				
				//CASE 3: participle follow etre passive auxiliary
				//  In this case, participle agrees with object
				if(noun == null && participleWithPassiveEtre(entry)) {
					noun = getObjForVerb(entry);
				}
												
				if(noun != null) {
					//
					//correct gender and number.
					//
					//Note: if feature is null, we don't correct it.
					//        This is because some participles don't have different
					//        forms, thus they are listed without these features
					if(entry.gender != null && !entry.gender.equals(noun.gender)) {
						corr.correctedGender = noun.gender;
					}
					if(entry.number != null && !entry.number.equals(noun.number)) {
						corr.correctedNumber = noun.number;
					}
				}
				
				corrs.add(corr);
			}
		}
		
		return corrs;		
	}
	
	//follow dependencies; retrieve noun modified by this participle
	//
	//This only handles modify case, not constructions with etre
	//
	//Crucially, if the participle is in a past tense of passive construction,
	//  then it doesn't count as modifying a noun direction, and we return null.
	//
	// return null if noun not found
	private BonsaiEntry getNounModifiedByParticiple(BonsaiEntry participle) {
		//check that no past-tense or passive auxiliaries modifiy this participle
		for(BonsaiEntry entry : entries) {
			if(entry.govIndex == participle.index &&
				("aux_tps".equals(entry.relation) || "aux_pass".equals(entry.relation))) { 
				
				return null;
			}
		}
		
		final Set<String> followable = new HashSet<String>();
		followable.add("mod");
		followable.add("dep-coord");
		followable.add("coord");

		BonsaiEntry cur = participle;
		while(true) {
			if(cur == null) {
				return null;
			}
			else if(cur.isNoun()) {
				//found the noun, return it
				return cur;
			}
			else if(followable.contains(cur.relation)) {
				//follow the dependency
				cur = getEntry(cur.govIndex);
			} else {
				return null;
			}
		}
	}
	
	
	private boolean participleWithPastTenseEtre(BonsaiEntry participle) {
		for(BonsaiEntry entry : entries) {
			if("être".equals(entry.lemma)
					&& entry.govIndex == participle.index
					&& "aux_tps".equals(entry.relation)) {
				
				return true;
			}
		}
		return false;
	}
	
	private boolean participleWithPassiveEtre(BonsaiEntry participle) {
		for(BonsaiEntry entry : entries) {
			if("être".equals(entry.lemma)
					&& entry.govIndex == participle.index
					&& "aux_pass".equals(entry.relation)) {
				
				return true;
			}
		}
		return false;
	}

	private static int totalCorrections;
	private static int totalActualCorrections;
	private void process(int lineNo, String frenchLine, Writer out) {
		try {
			List<Correction> adjCorrections = getAdjCorrections();

			String [] tokens = frenchLine.split(" ");

			for(Correction corr : adjCorrections) {
				totalCorrections++;
				if(!corr.alreadyCorrect()) {
					totalActualCorrections++;

					String correctAdj = corr.getCorrectedAdjString();
					tokens[corr.entry.index - 1] = correctAdj;
				}
			}

			//Note (kevin): we found participle corrections to not be useful and with too low precision
			//		List<Correction> participleCorrections = getParticipleCorrections();
			//		for(Correction corr : participleCorrections) {
			//			totalCorrections++;
			//			if(!corr.alreadyCorrect()) {
			//				totalActualCorrections++;
			//
			//				String correctParticiple = corr.getCorrectedParticipleString();
			//				tokens[corr.entry.index - 1] = correctParticiple;
			//			}
			//		}

			frenchLine = StringUtils.join(tokens);
			frenchLine += " "; //add space to maintain original format

		} catch(Exception e) {
			System.err.println("ERROR: Exception processing.  lineNo="+lineNo+
					" line="+frenchLine);
		} finally {
			try{
				out.write(frenchLine);
				out.write("\n");
			} catch(IOException e) {
				System.err.println("FATAL ERROR writing french line to destination." +
						"\n\tline="+frenchLine+
						"\n\tlineNo="+lineNo+
						"\n\tException message: "+e.getMessage());
				System.exit(1);
			}
		}
	}
	
	
	/**
	 * This main method handles agreement correction on french output for the
	 *  WMT2013 fr-en translation task.
	 * 
	 * @param args			args[0]	french output path
	 * 						args[1]	Bonzai parser output path
	 * 						args[2]	lefff extension lexicon path
	 * 						args[3] destination path
	 * @throws Exception
	 */
	public static void main(String args[]) throws Exception {
		System.err.println("french path:" + args[0]);
		BufferedReader br = new BufferedReader(new FileReader(args[0]));

		System.err.println("args[1]: "+args[1]);
		
		System.err.println("args[3]: "+ args[3]);
		BufferedWriter bw = new BufferedWriter(new FileWriter(args[3]));
		
		System.err.println("args[2]: " + args[2]);
		LefffLexicon.load(new File(args[2]));
		
		List<String> parseLines = new ArrayList<String>();
		int parseNo = 0;
		for(String line : ObjectBank.getLineIterator(args[1])) {
			if(line.isEmpty() && !parseLines.isEmpty()) {
				BonsaiParse bp = new BonsaiParse(parseLines);
				String frenchLine = br.readLine();
				bp.process(parseNo, frenchLine, bw);
				parseLines = new ArrayList<String>();
				parseNo++;
			}
			else {
				parseLines.add(line);
			}
		}
		
		System.out.println("actual/total corrections: "+totalActualCorrections +
				" / " + totalCorrections);
		
		bw.close();
		br.close();
		System.err.println("[BonsaiParse] Done.");
	}
	
	/**
	 * A BonzaiEntry and any necessary
	 *  feature corrections
	 * 
	 * @author kevinreschke
	 *
	 */
	public static class Correction {
		public BonsaiEntry entry;
		
		public Correction(BonsaiEntry entry) {
			this.entry = entry;
		}
		
		public boolean alreadyCorrect() {
			return correctedGender == null
					&& correctedNumber == null
					&& correctedPerson == null;
		}
			
		//These are NULL if the original is correct
		public String correctedGender;
		public String correctedNumber;
		public String correctedPerson;

		@Override
		public String toString() {
			return "Correction [entry=" + entry + ", correctedGender="
					+ correctedGender + ", correctedNumber=" + correctedNumber
					+ ", correctedPerson=" + correctedPerson + "]";
		}
		
		public String getCorrectedAdjString() {
			return getCorrectedString("adj", null);
		}
		
		public String getCorrectedParticipleString() {
			return getCorrectedString("v", "K");
		}
		
		/** Uses lefff lexicon to lookup to correct inflection based on the gender and number
		 *    corrections for this entry */
		public String getCorrectedString(String lefffPos, String lefffVerbCode) {
			if(alreadyCorrect()) throw new RuntimeException ("can't get corrected string... already correct.");
			
			LefffEntry queryForBaseTerm = new LefffEntry();
			queryForBaseTerm.word = entry.word;
			queryForBaseTerm.pos = lefffPos;
			queryForBaseTerm.verbCode = lefffVerbCode;
			
			List<LefffEntry> resultsForBaseTerm = LefffLexicon.lookup(queryForBaseTerm);

			Set<String> baseTerms = new HashSet<String>();
			for(LefffEntry le : resultsForBaseTerm) {
				if(le.baseTerm != null) baseTerms.add(le.baseTerm);
			}
			
			if(baseTerms.isEmpty()) {
				//word not found, return adj unchanged
				return entry.word;
			}
			else if(baseTerms.size() > 1) {
				//if we get multiple lexicon hits, ignore this correction because it may be unreliable
				System.err.println("[BonsaiParse.Correction.getCorrectedAdjString] "+
						"WARNING: multiple lefff results for query (word="+entry.word +
						") ... aborting correction");
				return entry.word;
			}
			
			LefffEntry queryForCorrectedWord = new LefffEntry();
			queryForCorrectedWord.baseTerm = baseTerms.iterator().next();
			queryForCorrectedWord.pos = "adj";
			
			if(correctedGender != null) queryForCorrectedWord.gender = correctedGender;
			else queryForCorrectedWord.gender = entry.gender;
			
			if(correctedNumber != null) queryForCorrectedWord.number = correctedNumber;
			else queryForCorrectedWord.number = entry.number;
			
			List<LefffEntry> resultsForCorrectedWord = LefffLexicon.lookup(queryForCorrectedWord);
			
			Set<String> correctedWords = new HashSet<String>();
			for(LefffEntry le : resultsForCorrectedWord) {
				if(le.word != null) correctedWords.add(le.word);
			}
			
			if(correctedWords.isEmpty()) {
				//word not found, return adj unchanged
				return entry.word;
			}
			else if(correctedWords.size() > 1) {
				//if we get multiple lexicon hits, ignore this correction because it may be unreliable
				System.err.println("[BonsaiParse.Correction.getCorrectedAdjString] "+
						"WARNING: multiple lefff results for query (baseTerm="+entry.word +
						") ... aborting correction");
				return entry.word;
			}
			
			return correctedWords.iterator().next();
		}
	}
}
