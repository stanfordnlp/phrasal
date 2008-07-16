package mt.decoder.efeat;

import java.util.*;
import java.io.*;

import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import edu.stanford.nlp.util.IString;
import mt.base.Sequence;
import mt.base.SimpleSequence;
import mt.decoder.feat.IncrementalFeaturizer;

/**
 * Feature template
 *
 * @author huihsin
 * @author danielcer
 *
 */
public class VerbLinkageFeaturizerThree implements IncrementalFeaturizer<IString, String> {
	public static final String PREFIX = "VRBLNKTHREE:";
	public Sequence<IString> previousForeign; // previous foreign sentence
	public Sequence<IString> currentForeign = new SimpleSequence<IString>(new 
IString("<BEGIN-CORPUS>"));  
	public Set<String> previousForeignWords = new HashSet<String>(); // set of foreign words
	public static final Set<String> fExistNouns = new HashSet<String>();
	public static final Set<String> fDTs = new HashSet<String>();
	public static final Set<String> fPNs = new HashSet<String>();
	public static final Set<String> fPUs = new HashSet<String>();
	public static final Set<String> fNouns = new HashSet<String>();
	public static final Set<String> tDTs = new HashSet<String>();

	static String PATH_TO_LIST_OF_fWords = "/juice/u3/htseng/mt/ctb.txt";
	static String PATH_TO_LIST_OF_tWords = "/juice/u3/htseng/mt/wsj.txt";

	static {
	
		try {
			BufferedReader freader = new BufferedReader(new 
FileReader(PATH_TO_LIST_OF_fWords));
			for (String line; (line = freader.readLine()) != null; ) {          
				if (line.equals("")) continue;
				String[] fields = line.split("	");
				if (fields.length == 2 ){
				String tag=fields[1];

				if(tag.contains("NN")){
					fNouns.add(fields[0]);
				}
				if(tag.contains("DT")){
					fDTs.add(fields[0]);
				}				
				if(tag.contains("PN")){
					fPNs.add(fields[0]);
				}
				if(tag.contains("PUA")|| tag.contains("CC")||tag.contains("CS")|| 
tag.contains("PUB") ){
					fPUs.add(fields[0]);
				}
				}
			}
			freader.close();

			BufferedReader treader = new BufferedReader(new 
FileReader(PATH_TO_LIST_OF_tWords));
			for (String line; (line = treader.readLine()) != null; ) {          
				if (line.equals("")) continue;
				
				String[] fields = line.split("	");
				if (fields.length == 2 ){
				String tag=fields[1];

				if(tag.contains("DT")){
					tDTs.add(fields[0]);
				}
				}
			}
			treader.close();


		} catch (IOException e) {
			throw new RuntimeException(String.format("Error reading: %s\n", 
PATH_TO_LIST_OF_fWords));
		}
	}

	@Override
	public FeatureValue<String> featurize(Featurizable<IString, String> f) { return null; }

	@Override
	public void initialize(List<ConcreteTranslationOption<IString>> options,
			Sequence<IString> foreign) {
		previousForeign = currentForeign;
		currentForeign = foreign;
		previousForeignWords.clear();
		fExistNouns.clear();
		for (IString fWord : previousForeign) {
			previousForeignWords.add(fWord.toString());
			if (fNouns.contains(fWord.toString())) fExistNouns.add(fWord.toString());
		}
	}

	@Override
	public List<FeatureValue<String>> listFeaturize(
			Featurizable<IString, String> f) {
		List<FeatureValue<String>> features = new LinkedList<FeatureValue<String>>();

		int fPhrSz = f.foreignPhrase.size(); 
		String fCon = "";
		String fWord = "";
		for (int fPos = 0; fPos < fPhrSz; fPos++) {
			fWord = f.foreignPhrase.get(fPos).toString();
			if (fNouns.contains(fWord)) {
				int foreignPhrasePos = f.foreignPosition; 
				int fWordPos = fPos + foreignPhrasePos;
				String fPU  = "f";
				String fPN  = "f";
				String fDT  = "f";
				String fPUi = "f";
				String fEX  = "f";	
				
				if(fExistNouns.contains(fWord)){
					fEX="t";
				}
				for(int sourcePos = fWordPos-1; sourcePos > 0 ; sourcePos--){
					
if(fDTs.contains(f.foreignSentence.get(sourcePos).toString())){
						fDT = "t";
					}
					
if(fPNs.contains(f.foreignSentence.get(sourcePos).toString())){
						fPN = "t";
					}
					
if(fPUs.contains(f.foreignSentence.get(sourcePos).toString())){
						fPUi = "t";
						break;
					}					
				}
				if( (fWordPos+1) < f.foreignSentence.size() && 
fPUs.contains(f.foreignSentence.get(fWordPos+1)) ){
					fPU="t";
				}
				fCon = fEX  + "_" + fDT + "_" + fPN + "_" +  fPUi + "_" + fPU ;
				break;
			}
		}

		int tPhrSz = f.translatedPhrase.size(); 
		String tDT ="f";
		
		for (int tPos = 0; tPos < tPhrSz; tPos++) {	
			IString tWord = f.translatedPhrase.get(tPos);
			if (tDT.contains(tWord.toString())) {
				tDT = "t";
			}
		}

		String tCnd = "" ;
		tCnd = tDT;

		features.add(new FeatureValue<String>(PREFIX+fCon+":"+tCnd, 1.0));
		features.add(new FeatureValue<String>(PREFIX+fWord+":"+tCnd, 1.0));
		features.add(new FeatureValue<String>(PREFIX+fCon+":"+fWord+":"+tCnd, 1.0));

		return features;
	}

	@Override
	public void reset() { }

}

