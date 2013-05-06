package edu.stanford.nlp.mt.process.fr;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import edu.stanford.nlp.objectbank.ObjectBank;


/**
 * Simple lefff-based extensional lexicon for french inflections 
 * 
 * @author kevinreschke
 *
 */
public class LefffLexicon {

	private static MultiHashMap<String,LefffEntry> wordToEntries = new MultiHashMap<String,LefffEntry>();
	private static MultiHashMap<String,LefffEntry> baseTermToEntries = new MultiHashMap<String,LefffEntry>();
	
	public static void load(File lexFile) {
		for(String line : ObjectBank.getLineIterator(lexFile,"ISO-8859-1")) {
			if(!line.isEmpty() && !line.startsWith("_")) {
				try {
					line = new String(line.getBytes(),"UTF-8");
				} catch (UnsupportedEncodingException e) {
					throw new RuntimeException(e);
				}
				
				LefffEntry le = LefffEntry.fromLine(line);
				wordToEntries.addValue(le.word, le);
				baseTermToEntries.addValue(le.baseTerm, le);
			}
		}
	}
	
	/**
	 * @param query	A query entry with unknown fields set to null.
	 * 				Either name or baseTerm must be non-null, otherwise lookup returns null.
	 * @return	List of matching queries.  If no match, empty list is returned.
	 */
	public static List<LefffEntry> lookup(LefffEntry query) {
		List<LefffEntry> rtn = new ArrayList<LefffEntry>();
		
		if(query.word != null) {
			LinkedHashSet<LefffEntry> results = wordToEntries.get(query.word);
			if(results == null) {
				return rtn;
			}
			else {
				for(LefffEntry result : results) {
					if(match(result,query)) {
						rtn.add(result);
					}
				}
				return rtn;
			}
		}
		else if(query.baseTerm != null) {
			LinkedHashSet<LefffEntry> results = baseTermToEntries.get(query.baseTerm);
			if(results == null) {
				return rtn;
			}
			else {
				for(LefffEntry result : results) {
					if(match(result,query)) {
						rtn.add(result);
					}
				}
				return rtn;
			}
		}
		else {
			return null;
		}
	}
	
	//Two entries match if each of their non-null entries match
	//Use: for comparing a query to an entry
	private static boolean match(LefffEntry a, LefffEntry b) {
		if(a.word != null && b.word != null && !a.word.equals(b.word)) return false;
		if(a.pos != null && b.pos != null && !a.pos.equals(b.pos)) return false;
		if(a.baseTerm != null && b.baseTerm != null && !a.baseTerm.equals(b.baseTerm)) return false;
		if(a.gender != null && b.gender != null && !a.gender.equals(b.gender)) return false;
		if(a.number != null && b.number != null && !a.number.equals(b.number)) return false;
		if(a.person != null && b.person != null && !a.person.equals(b.person)) return false;
		if(a.verbCode != null && b.verbCode != null && !a.verbCode.equals(b.verbCode)) return false;
		
		return true;
	}
	
}
