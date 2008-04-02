package mt.decoder.efeat;

import java.util.*;
import java.io.*;

import mt.base.ConcreteTranslationOption;
import mt.base.FeatureValue;
import mt.base.Featurizable;
import mt.base.IString;
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
public class VerbLinkageFeaturizer implements IncrementalFeaturizer<IString, String> {
	public static final String PREFIX = "VRBLNK:";
	public Sequence<IString> previousForeign; // previous foreign sentence
	public Sequence<IString> currentForeign = new SimpleSequence<IString>(new 
IString("<BEGIN-CORPUS>"));  

	public Set<String> previousForeignWords = new HashSet<String>(); // set of foreign words
	public Set<String> fExistVerbs = new HashSet<String>();
	public static final Set<String> knownVerbs;
	public static final Set<String> fVerbs;
	public static final Set<String> fVAVerbs = new HashSet<String>();
	public static final Set<String> fNouns = new HashSet<String>();
	public static final Set<String> fSHIs = new HashSet<String>();
	public static final Set<String> fYOUs = new HashSet<String>();
	public static final Set<String> fBPUs = new HashSet<String>();
	public static final Set<String> fnBPUs = new HashSet<String>();
	public static final Set<String> fDEs = new HashSet<String>();
	public static final Set<String> fASs = new HashSet<String>();
	public static final Set<String> fADs = new HashSet<String>();

	public static final Set<String> tBeVerbs = new HashSet<String>();
	public static final Set<String> tHaveVerbs = new HashSet<String>();
	public static final Set<String> tIngVerbs = new HashSet<String>();
	public static final Set<String> tEdVerbs = new HashSet<String>();
	public static final Set<String> tProtoVerbs = new HashSet<String>();
	public static final Set<String> tNounVerbs = new HashSet<String>();
	public static final Set<String> tRelVerbs = new HashSet<String>();
	public static final Set<String> tConjVerbs = new HashSet<String>();
	public static final Set<String> tToVerbs = new HashSet<String>();
	public static final Set<String> tINVerbs = new HashSet<String>();
	public static final Set<String> tVerbs = new HashSet<String>();

	static String PATH_TO_LIST_OF_fWords = "/juice/u3/htseng/mt/ctb.txt";
	static String PATH_TO_LIST_OF_tWords = "/juice/u3/htseng/mt/wsj.txt";

	static {
		knownVerbs = new HashSet<String>();
		fVerbs = new HashSet<String>();

		try {
			BufferedReader freader = new BufferedReader(new 
FileReader(PATH_TO_LIST_OF_fWords));
			for (String line; (line = freader.readLine()) != null; ) {          
				if (line.equals("")) continue;
				String[] fields = line.split("	");
			if (fields.length == 2 ){
				String tag=fields[1];
				if(tag.contains("VV")||tag.contains("VC")||tag.contains("VE")){
					knownVerbs.add(fields[0]);
				}
				if(tag.contains("VV")){
					fVerbs.add(fields[0]);
				}
				if(tag.contains("VA")){
					fVAVerbs.add(fields[0]);
				}
				if(tag.contains("NN")||tag.contains("NR")||tag.contains("PN")){
					fNouns.add(fields[0]);
				}
				if(tag.contains("PUA")|| tag.contains("CC")||tag.contains("CS")){
					fBPUs.add(fields[0]);
				}  				
				if(tag.contains("PUB")){
					fnBPUs.add(fields[0]);
				}
				if(tag.contains("VC")){
					fSHIs.add(fields[0]);
				} 
				if(tag.contains("VE")){
					fYOUs.add(fields[0]);
				} 
				if(tag.contains("AS")){
					fASs.add(fields[0]);
				} 
				if(tag.contains("AD")){
					fADs.add(fields[0]);
				} 
				if(tag.startsWith("DE")){
					fDEs.add(fields[0]);
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
				if(tag.startsWith("V")){
					tVerbs.add(fields[0]);
				}
				
if(tag.contains("NN")||tag.contains("NNS")||tag.contains("NNP")||tag.contains("NNPS")){
					tNounVerbs.add(fields[0]);
				}
				
if(fields[0].equals("have")||fields[0].equals("had")||fields[0].equals("has")){
					tHaveVerbs.add(fields[0]);
				} 
				
if(fields[0].equals("be")||fields[0].equals("is")||fields[0].equals("are")||fields[0].equals("am")||
						fields[0].equals("was")||fields[0].equals("were")){
					tBeVerbs.add(fields[0]);
				} 
				if(tag.contains("VBG")){
					tIngVerbs.add(fields[0]);
				} 
				if(tag.contains("VBN")||tag.contains("VBD")){
					tEdVerbs.add(fields[0]);
				} 
				if(tag.contains("VBP")||tag.contains("VBZ")){
					tProtoVerbs.add(fields[0]);
				} 
				if(tag.startsWith("W")){
					tRelVerbs.add(fields[0]);
				} 
				if(tag.contains("CC")){
					tConjVerbs.add(fields[0]);
				} 
				if(fields[0].equals("to")||fields[0].equals("To")){
					tToVerbs.add(fields[0]);
				} 
				if(tag.contains("IN")){
					tINVerbs.add(fields[0]);
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
		fExistVerbs.clear();
		for (IString fWord : previousForeign) {
			previousForeignWords.add(fWord.toString());
			if (knownVerbs.contains(fWord.toString())) fExistVerbs.add(fWord.toString());
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
			if (knownVerbs.contains(fWord)) {
				int foreignPhrasePos = f.foreignPosition; 
				int fWordPos = fPos + foreignPhrasePos;
				String fverb = "f";
				String fVAverb = "f";
				String fnoun = "f";
				String fSHI = "f";
				String fYOU = "f";
				String fSHIi = "f";
				String fYOUi = "f";
				String fBPU = "f";
				String fnBPU ="f";
				String fDE ="f";
				String fAD ="f";
				String fAS ="f";
				String fEX ="f";

				if(fVAVerbs.contains(fWord)){
					fVAverb = "t";
				}
				if(fSHIs.contains(fWord)){
					fSHIi="t";
				}
				if(fYOUs.contains(fWord)){
					fYOUi="t";
				}
				if(fExistVerbs.contains(fWord)){
					fEX="t";
				}
				
				for(int sourcePos = fWordPos-1; sourcePos > 0 ; sourcePos--){
					
if(fVerbs.contains(f.foreignSentence.get(sourcePos).toString())){
						fverb = "t";
					}
					
if(fNouns.contains(f.foreignSentence.get(sourcePos).toString())){
						fnoun="t";
					}
					
if(fSHIs.contains(f.foreignSentence.get(sourcePos).toString())){
						fSHI="t";
					}
					
if(fYOUs.contains(f.foreignSentence.get(sourcePos).toString())){
						fYOU="t";
					}
					
if(fBPUs.contains(f.foreignSentence.get(sourcePos).toString())){
						fBPU="t";
						break;
					}
					
if(fnBPUs.contains(f.foreignSentence.get(sourcePos).toString()) ){
						fnBPU="t";
					}
				}

				if( (fWordPos+1) < f.foreignSentence.size() && 
fDEs.contains(f.foreignSentence.get(fWordPos+1)) ){
					fDE="t";
				}
				if( (fWordPos+1) < f.foreignSentence.size() && 
fASs.contains(f.foreignSentence.get(fWordPos+1)) ){
					fAS="t";
				}
				if( (fWordPos-1) >= 0  && 
fADs.contains(f.foreignSentence.get(fWordPos+1)) ){
					fAD="t";
				}
				
				fCon = fEX + "_" + fverb + "_" + fVAverb + "_" + fnoun + "_" + fSHI + 
"_" + fYOU + 
				"_" + fSHIi + "_" + fYOUi + "_" + fBPU + "_" + fnBPU +"_" + fDE + "_" 
+ fAS + "_" + fAD;
				break;
			}
		}


		//List<IString> transVerbs = new LinkedList<IString>();		
		int tPhrSz = f.translatedPhrase.size(); 
		String tBeVerb ="f";
		String tHaveVerb ="f";
		String tVerb ="f";
		String tEdVerb ="f";
		String tIngVerb ="f";
		String tProtoVerb ="f";
		String tNounVerb ="f";
		String tRel ="f";
		String tConj ="f";
		String tTo ="f";
		String tIN ="f";

		for (int tPos = 0; tPos < tPhrSz; tPos++) {	
			IString tWord = f.translatedPhrase.get(tPos);
			if (tBeVerbs.contains(tWord.toString())) {
				tBeVerb = "t";
				tVerb = "t";
			}
			if (tHaveVerbs.contains(tWord.toString())) {
				tHaveVerb = "t";
				tVerb = "t";
			}
			if (tIngVerbs.contains(tWord.toString()) || tWord.toString().endsWith("ing")) 
{
				tIngVerb = "t";
				tVerb = "t";
			}
			if (tEdVerbs.contains(tWord.toString()) || tWord.toString().endsWith("ed")) {
				tEdVerb = "t";
				tVerb = "t";
			}
			if (tProtoVerbs.contains(tWord.toString())) {
				tProtoVerb = "t";
				tVerb = "t";
			}
			if (tNounVerbs.contains(tWord.toString()) && tVerb.equals("t")) {
				tNounVerb = "t";
			}
			if (tRelVerbs.contains(tWord.toString())) {
				tRel = "t";
			}
			if (tConjVerbs.contains(tWord.toString())) {
				tConj = "t";
			}
			if (tToVerbs.toString().equals("to")) {
				tTo = "t";
			}		  
			if (tINVerbs.contains(tWord.toString())) {
				tIN = "t";
			}
		}


		String tCnd = "" ;
		tCnd = tBeVerb + "_" + tHaveVerb + "_" + tIngVerb + "_" 
		+ tEdVerb + "_" + tProtoVerb + "_" + tNounVerb + "_" 
		+ tRel + "_" + tConj + "_" + tTo + "_" + tIN;		

		features.add(new FeatureValue<String>(PREFIX+fCon+":"+tCnd, 1.0));
		features.add(new FeatureValue<String>(PREFIX+fWord+":"+tCnd, 1.0));
		features.add(new FeatureValue<String>(PREFIX+fCon+":"+fWord+":"+tCnd, 1.0));

		return features;
	}

	@Override
	public void reset() { }

}

