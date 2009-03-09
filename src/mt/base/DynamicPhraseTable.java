/**
 * 
 */
package mt.base;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mt.decoder.feat.IsolatedPhraseFeaturizer;
import mt.decoder.util.Scorer;

import edu.stanford.nlp.util.IString;
import edu.stanford.nlp.util.IStrings;

import java.util.LinkedList;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;


/**
 * @author danielcer
 *
 */
public class DynamicPhraseTable<FV> extends AbstractPhraseGenerator<IString,FV> {
	
	Database db;
	Environment dbEnv;
	
	Set<String> currentSequence; 
	IBMModel1 model1S2T, model1T2S;
	
	public DynamicPhraseTable(
			IsolatedPhraseFeaturizer<IString, FV> phraseFeaturizer, Scorer<FV> scorer, String phraseTableName, String model1S2T, String model1T2S) {
		super(phraseFeaturizer, scorer);
		currentSequence = new HashSet<String>();
		initdb(phraseTableName);
		try {
		this.model1S2T = IBMModel1.load(model1S2T);
		this.model1T2S = IBMModel1.load(model1T2S);
		} catch (Exception e) { throw new RuntimeException(e); }		
	}
	
	public DynamicPhraseTable(
			IsolatedPhraseFeaturizer<IString, FV> phraseFeaturizer, Scorer<FV> scorer, String phraseTableName) {
		super(phraseFeaturizer, scorer);
		currentSequence = new HashSet<String>();
		initdb(phraseTableName);
	}

	private void initdb(String phraseTableName) {
		try {			
			EnvironmentConfig envConfig = new EnvironmentConfig();
			envConfig.setAllowCreate(true);
 
			File f = new File(phraseTableName);
			if (!f.exists()) {
				f.mkdir();
			}
			
			dbEnv = new Environment(new File(phraseTableName), envConfig);

			// Open the database. Create it if it does not already exist.
			DatabaseConfig dbConfig = new DatabaseConfig();
			dbConfig.setAllowCreate(true);
			// dbConfig.setDeferredWrite(true);
			dbConfig.setSortedDuplicates(true);
			db = dbEnv.openDatabase(null, "dpt", dbConfig);
		} catch (Exception e) {
			throw new RuntimeException(e);			
		}	
	}
	
	public final int phraseLengthLimit = 3;
	
	@Override
	public String getName() {
		return "DynaPhraseTable";
	}


	@Override
	public List<TranslationOption<IString>> getTranslationOptions(
			Sequence<IString> sequence) {		
		List<TranslationOption<IString>> opts = new LinkedList<TranslationOption<IString>>();
	
		IString noConst = new IString("noConst");
		
		try {
			Cursor cursor = db.openCursor(null, null);
			
			DatabaseEntry key = new DatabaseEntry(sequence.toString().getBytes("UTF-8"));
			DatabaseEntry data = new DatabaseEntry();
			int pairsRetrived = 0;
			for (OperationStatus status = cursor.getSearchKey(key, data, LockMode.READ_UNCOMMITTED); 
			status == OperationStatus.SUCCESS; status = cursor.getNextDup(key, data, LockMode.READ_UNCOMMITTED)) { 
				String transS = new String(data.getData(), "UTF-8");
				RawSequence<IString> transSeq = new RawSequence<IString>(IStrings.toIStringArray(transS.split("\\s+")));
				String mappingKey = sequence+"=:=>"+transS;
				//System.err.printf("Sequences: %s => %s Feature rep: %s\n", sequence, transSeq, featRep);
				if (model1S2T != null && model1T2S != null) {
					opts.add(new TranslationOption<IString>(
							new float[]{(float)-1.0, (float)Math.log(model1S2T.score(sequence, transSeq)), (float)Math.log(model1T2S.score(transSeq, sequence))}, 
							new String[]{"PhrPen", "lex(f|e)", "lex(e|f)"}, 
							new RawSequence<IString>(transSeq), new RawSequence<IString>(sequence), noConst, currentSequence.contains(mappingKey)));
				} else {
					opts.add(new TranslationOption<IString>(new float[]{(float)-1.0}, new String[]{"PhrPen"}, 
							new RawSequence<IString>(transSeq), new RawSequence<IString>(sequence), noConst, currentSequence.contains(mappingKey)));
				}
				if (currentSequence.contains(mappingKey)) {
					// System.err.printf("mapping key found '%s'\n", mappingKey);
					pairsRetrived++; 
				} else {
					// System.err.printf("mapping key not found '%s'\n", mappingKey);
				}
			}
			
			// System.err.printf("Sequence specific pairs retrived: '%s' : %d\n", sequence, pairsRetrived);
			// System.err.printf("currentSequence size: %d\n", currentSequence.size());
			cursor.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		return opts;
	}

	@Override
	public int longestForeignPhrase() {
		return phraseLengthLimit;
	}
	
	public void close() {
		try {
		db.close();
		dbEnv.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public void setCurrentSequence(Sequence<IString> foreign, List<Sequence<IString>> tranList) {
		//System.err.printf("****DYN**** PHRASE GENERATOR SETTING CURRENT SEQ\n");
		currentSequence = new HashSet<String>();
		//System.err.printf("foreign: %s\n", foreign);
		if (tranList == null) return;
		//System.err.println("Setting Current Sequence Pair:");
		//System.err.printf("source: %s\n", foreign);
		//System.err.printf("target: %s\n", tranList.get(0));
		int pairSpecificPhrases = 0;
		try {
		for (Sequence<IString> trans : tranList) {
		//System.err.printf("tran: %s\n", trans);
		for (int fStart = 0; fStart < foreign.size(); fStart++) {
		for (int fEnd = fStart; fEnd < foreign.size() && fEnd < fStart + phraseLengthLimit; fEnd++) {
		Sequence<IString> fPhrase = foreign.subsequence(fStart, fEnd+1);
		int phraseSpecificTranslations = 0;
		for (int tStart = 0; tStart < trans.size(); tStart++) {
		for (int tEnd = tStart; tEnd < trans.size() && tEnd < tStart + phraseLengthLimit; tEnd++) {
			
			Sequence<IString> tPhrase = trans.subsequence(tStart, tEnd+1);
			String featRep = fPhrase+"=:=>"+tPhrase;
			currentSequence.add(featRep);
			DatabaseEntry key = new DatabaseEntry(fPhrase.toString().getBytes("UTF-8"));
			DatabaseEntry data = new DatabaseEntry(tPhrase.toString().getBytes("UTF-8"));
			if (db.put(null, key, data) != OperationStatus.SUCCESS) throw new RuntimeException();
			// System.err.printf("putting '%s=:=>%s'\n", fPhrase, tPhrase);
			pairSpecificPhrases++;
			phraseSpecificTranslations++;
		}
		}
		/*
		System.err.printf("phrase specific inserts: '%s' : %d\n", fPhrase, phraseSpecificTranslations);
		Cursor cursor = db.openCursor(null, null);
		
		DatabaseEntry key = new DatabaseEntry(fPhrase.toString().getBytes("UTF-8"));
		DatabaseEntry data = new DatabaseEntry();
		int pairsRetrived = 0;
		
		for (OperationStatus status = cursor.getSearchKey(key, data, LockMode.READ_UNCOMMITTED); 
		status == OperationStatus.SUCCESS; status = cursor.getNextDup(key, data, LockMode.READ_UNCOMMITTED)) {
			String foreignS = new String(key.getData(), "UTF-8");
			String transS = new String(data.getData(), "UTF-8");
			System.err.printf("retrived '%s=:=>%s'\n", foreignS, transS);
			pairsRetrived++;
		}
		System.err.printf("Pairs retreived: %d\n", pairsRetrived);
		*/
		}				
		}
		}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		/*
		System.err.printf("Current sequence mapping keys\n");
		for (String mappingKey : currentSequence) {
			System.out.printf("'%s'\n", mappingKey);
		}
		System.err.printf("Pair specific phrases generated: %d\n", pairSpecificPhrases);
		System.err.printf("currentSequence size: %d\n", currentSequence.size());
		*/
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
	  DynamicPhraseTable<String> dpt = new DynamicPhraseTable<String>(null, null, args[0]);
	  
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		for (String foreign = br.readLine(); foreign != null && !foreign.equals(""); foreign = br.readLine()) {
			String trans = br.readLine();
			if (trans == null || trans.equals("")) break;
		
			RawSequence<IString> foreignSeq = new RawSequence<IString>(IStrings.toIStringArray(foreign.split("\\s+")));
			List<Sequence<IString>> transList = new ArrayList<Sequence<IString>>();
			transList.add(new RawSequence<IString>(IStrings.toIStringArray(trans.split("\\s+"))));
			dpt.setCurrentSequence(foreignSeq, transList);
			for (int fStart = 0; fStart < foreignSeq.size(); fStart++) {
				for (int fEnd = fStart; fEnd < foreignSeq.size() && fEnd < fStart + dpt.phraseLengthLimit; fEnd++) {
					RawSequence<IString> foreignPhr = new RawSequence(foreignSeq.subsequence(fStart, fEnd+1));
					List<TranslationOption<IString>> options = dpt.getTranslationOptions(foreignPhr);
					System.out.printf("%s =>\n", foreignPhr);
					for (TranslationOption<IString> option : options) {
						System.out.printf("\t%s %s %s\n", option.translation, option.forceAdd, option.phraseScoreNames[0]);
					}
				}
			}
		}
		dpt.close();
	}
}