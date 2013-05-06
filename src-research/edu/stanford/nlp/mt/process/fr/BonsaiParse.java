package edu.stanford.nlp.mt.process.fr;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefChain.CorefMention;
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
	public boolean isEmpty() { return entries.isEmpty(); }
	
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
				//
                //Note (kevin) 2012-05-1: this is error prone due to attachment ambiguities, so we leave it out.
//                noun = getNounModifiedByParticiple(entry);
								
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
	private void process(int lineNo, String frenchLine, Writer out, String engLine, Alignment alignment, SourceSideCoref ssc) {
		try {
			System.err.println("beginning process... lineNo: "+lineNo);
			//
			// First correct gendered pronouns
			//
			frenchLine = correctPronounGender(frenchLine, alignment, engLine, ssc);
			
			
			//
			// Next correct agreement
			//
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

			List<Correction> participleCorrections = getParticipleCorrections();
			for(Correction corr : participleCorrections) {
				totalCorrections++;
				if(!corr.alreadyCorrect()) {
					totalActualCorrections++;

					String correctParticiple = corr.getCorrectedParticipleString();
					tokens[corr.entry.index - 1] = correctParticiple;
				}
			}

			frenchLine = StringUtils.join(tokens);
			frenchLine += " "; //add space to maintain original format

		} catch(Exception e) {
			System.err.println("ERROR: Exception processing.  lineNo="+lineNo+
					" line="+frenchLine +
					" Exception: " + e.getMessage());
			throw new RuntimeException(e);
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
	 * Determine the proper pronoun genders for this parse.
	 * 
	 * Handles il/elle and ils/elles
	 * 
	 *  This method modifies the parse with the updated pronouns.
	 *  
	 *  The matching updated french sentence string is also returned
	 */
	private String correctPronounGender(String frenchSent, Alignment alignment, String englishSent, SourceSideCoref ssc) {
		if(englishSent.isEmpty()) {
			//skip strange case where there is no source sent
			return frenchSent;
		}
		
		final Set<String> pronouns = new HashSet<String>();
		pronouns.add("il");
		pronouns.add("elle");
		pronouns.add("ils");
		pronouns.add("elles");
		
		Map<Integer,CorefChain> corefGraph = null;
		List<String> tokens = Arrays.asList(englishSent.split(" "));
		
		//
		// Get all occurences of il/elle/ils/elles
		//
		List<BonsaiEntry> candidates = new ArrayList<BonsaiEntry>();
		for(BonsaiEntry be : entries) {
			if(pronouns.contains(be.word)) {
				candidates.add(be);
			}
		}

		if(candidates.isEmpty()) {
			return frenchSent;
		}
		else {
			//
			// run english coref once to use later on
			//
			corefGraph = ssc.getCorefGraph(englishSent);
		}
		
		
		for(BonsaiEntry candidate : candidates) {
			//
			// find alignment to english
			//
			int candidateIdx = candidate.index - 1;  //convert 1-based idx to 0-based
			Span sourceSpan = alignment.getSourceAlignment(candidateIdx);
			
			//
			// See if coref chain exists with 'it' or 'they' in this span
			//
			CorefChain ccMatch = null;
			int headMatch = -1;
			ccLoop: for(CorefChain cc : corefGraph.values()) {
				for(CorefMention cm : cc.getMentionsInTextualOrder()) {
					int headIndex = cm.headIndex - 1; //convert 1-based to 0based
					String head = tokens.get(headIndex);
					if(sourceSpan.inSpan(headIndex) && (head.equals("it") || head.equals("they"))) {
						ccMatch = cc;
						headMatch = headIndex;
						break ccLoop;
					}
				}
			}
			
			if(ccMatch == null) continue; //no match, try next candidate
			
			//
			// Now find the heads of each corefering mention
			//
			List<Integer> coreferingHeadIdxs = new ArrayList<Integer>(); //0-based index
			for(CorefMention cm : ccMatch.getMentionsInTextualOrder()) {
				int headIndex = cm.headIndex - 1; //convert 1-based to 0based
				if(!sourceSpan.inSpan(headIndex)) { 
					//don't count coreferents in same span.
					coreferingHeadIdxs.add(headIndex);
				}
			}
			
			//
			// Next, for each of these heads, get the span of the alignment back to the target french
			//
			List<Span> coreferingTargetSpans = new ArrayList<Span>();
			for(int coreferingHeadIdx : coreferingHeadIdxs) {
				coreferingTargetSpans.add(alignment.getTargetAlignment(coreferingHeadIdx));
			}
			
			//
			// Now poll the nouns in these spans for gender
			//
			LinkedHashSet<Integer> spannedIndices = new LinkedHashSet<Integer>();
			for(Span span : coreferingTargetSpans) {
				if(!span.inSpan(candidateIdx) || span.start > candidateIdx) { //don't poll from the original candidate's span or later
					for(int i = span.start; i < span.end; i++) {
						spannedIndices.add(i);
					}
				}
			}
			int fVotes = 0;
			int mVotes = 0;
			for(int i : spannedIndices) {
				BonsaiEntry be = entries.get(i);
				if(be.isNoun() && be.isFeminine()) {
					fVotes++;
				}
				else if(be.isNoun() && be.isMasculine()) {
					mVotes++;
				}
			}
			
			//
			// now update the pronoun
			//
			if(fVotes > mVotes) {
				//change to feminine
				if("ils".equals(candidate.word)){ 
					candidate.word = "elles";
				}
				else if("il".equals(candidate.word)) {
					candidate.word = "elle";
				}
			}
			else if(mVotes > fVotes) {
				//change to masculing
				if("elles".equals(candidate.word)){
					candidate.word = "ils";
				}
				if("elle".equals(candidate.word)) {
					candidate.word = "il";
				}
			}
			
			//
			// Make candidate word and french sentence word equivalent
			//
			List<String> frenchTokens = Arrays.asList(frenchSent.split(" "));
			frenchTokens.set(candidateIdx, candidate.word);
			frenchSent = StringUtils.join(frenchTokens);
			frenchSent += " ";
			
		}
		
		return frenchSent;
	}
	
	/**
	 * This main method handles agreement correction on french output for the
	 *  WMT2013 fr-en translation task.
	 * 
	 * 
	 * 
	 * @param args			args[0]	french output path
	 * 						args[1]	Bonzai parser output path
	 * 						args[2]	lefff extension lexicon path
	 *                      args[3] english source path
	 *                      args[4] alignment path
	 * 						args[5] destination path
	 * @throws Exception
	 */
	public static void main(String args[]) throws Exception {
		System.err.println("french path:" + args[0]);
		BufferedReader frenchBR = new BufferedReader(new FileReader(args[0]));

		System.err.println("Bonzai path: "+args[1]);
		
		System.err.println("lefff path: " + args[2]);
		LefffLexicon.load(new File(args[2]));
		
		System.err.println("english source path: "+ args[3]);
		BufferedReader engBR = new BufferedReader(new FileReader(args[3]));
		
		System.err.println("alignment path: "+args[4]);
		List<Alignment> alignmentList = Alignment.fromNBestFile(new File(args[4]));
		
		System.err.println("destination: "+ args[5]);
		BufferedWriter bw = new BufferedWriter(new FileWriter(args[5]));
		
		SourceSideCoref ssc = new SourceSideCoref();
		
		List<String> parseLines = new ArrayList<String>();
		int parseNo = 0;
		for(String line : ObjectBank.getLineIterator(args[1])) {
			if(line.isEmpty()) { //there is an empty line marking the end of each parse.  A missing parse is a single empty line.
				BonsaiParse bp = new BonsaiParse(parseLines);
				String frenchLine = frenchBR.readLine();
				String engLine = engBR.readLine();
				if(bp.isEmpty()) {
					//empty parse caused by parsing error
					// no changes to french sentence
					bw.write(frenchLine);
					bw.write("\n");
				}
				else {
					bp.process(parseNo, frenchLine, bw, engLine, alignmentList.get(parseNo), ssc);
				}
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
		frenchBR.close();
		engBR.close();
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
	
	/**
	 * The alignment between english source and 
	 *  french target words.
	 * 
	 * @author kevinreschke
	 *
	 */
	private static class Alignment {
		
		String alignmentString; //e.g. 0=0 1-2=3 3=4 4=5 5=6
		
		private Alignment(String alignmentString) {
			this.alignmentString = alignmentString.trim();
		}
		
		//Get source alignment span given target index (0-based)
		private Span getSourceAlignment(int targetIndex) {
			String[] alignments = alignmentString.split(" ");
			for(String alignment : alignments) {
				String[] sourceTargetPair = alignment.split("=");
				int target = Integer.parseInt(sourceTargetPair[1]);
				if(target >= targetIndex) {
					String [] source = sourceTargetPair[0].split("-");
					int start = Integer.parseInt(source[0]);
					int end;
					if(source.length == 1) {
						end = start+1;
					}
					else {
						end = Integer.parseInt(source[1])+1;
					}
					return new Span(start,end);
				}
			}
			return null; //invalid target index
		}
		
		//Get target alignment span given source index (0-based)
		private Span getTargetAlignment(int sourceIndex) {
			String[] alignments = alignmentString.split(" ");
			int targetStart = 0;
			for(String alignment : alignments) {
				String[] sourceTargetPair = alignment.split("=");
				int targetEnd = Integer.parseInt(sourceTargetPair[1]) + 1;
				String[] source = sourceTargetPair[0].split("-");
				int sourceStart = Integer.parseInt(source[0]);
				int sourceEnd;
				if(source.length == 1) 
					sourceEnd = sourceStart + 1;
				else
					sourceEnd = Integer.parseInt(source[1]) + 1;
				if((new Span(sourceStart,sourceEnd)).inSpan(sourceIndex)) {
					Span targetSpan = new Span(targetStart,targetEnd);
					return targetSpan;
				}
				targetStart = targetEnd;
			}
			return null; //invalid source index
		}
		
		private static Alignment fromAlignmentFileLine(String line) {
			String[] fields = line.split("\\|\\|\\|");
			return new Alignment(fields[fields.length - 1]);
		}
		
		private static List<Alignment> fromNBestFile(File nBest) {
			List<Alignment> alignments = new ArrayList<Alignment>();
			
			int sentNo = 0;
			
			for(String line : ObjectBank.getLineIterator(nBest)) {
				if(line.startsWith(String.valueOf(sentNo))) {  //take first alignment... skip the rest
					alignments.add(fromAlignmentFileLine(line));
					sentNo++;
				}
			}
			
			return alignments;
		}
	}
	
	//Span
	// a-b includes a and excludes b
	private static class Span implements Comparable<Span>{
		int start;
		int end;
		private Span(int start, int end) {this.start=start;this.end=end;}
		private boolean inSpan(int x) {return x >= start && x < end;}
		@Override
		public String toString() {
			return "Span [start=" + start + ", end=" + end + "]";
		}
		
		//give the sentence subset picket out by this span
		// Assumes 0-based indexing
		public String toString(String sent) {
			String[] s = sent.split(" ");
			String rtn = "";
			for(int i = start; i < end; i++) {
				rtn += s[i];
			}
			return rtn.trim();
		}
		
		/** order by start, then by end */
		@Override
		public int compareTo(Span that) {
			int c = this.start - that.start;
			if(c == 0) c = this.end - that.end;
			return c;
		}
		
	}
}
