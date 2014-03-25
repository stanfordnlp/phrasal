package edu.stanford.nlp.mt.lm;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.lm.KenLM;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.TokenUtils;
import edu.stanford.nlp.util.Util;

/**
 * NPLM Language Model interface backed by KenLanguageModel
 * 
 * @author Thang Luong
 *
 */
public class NPLMLanguageModel implements LanguageModel<IString> {
	private KenLM kenlm;
	
  private final String INPUT_VOCAB_SIZE = "input_vocab_size";
  private final String OUTPUT_VOCAB_SIZE = "output_vocab_size";
  private final String SRC_ORDER = "src_ngram_size";
  
  private final String name;
  private final int order;
  private final int srcOrder;
	private final int tgtOrder;
	
	private final List<IString> srcWords;
	private final List<IString> tgtWords;
	
	// map IString id to NPLM id
  private final int[] srcVocabMap;
	private final int[] tgtVocabMap;
  
  private final int srcUnkVocabId; 
	private final int tgtUnkVocabId; 
  private final int tgtStartVocabId;
  
  protected native long scoreNGram(long kenLMPtr, int[] ngram);
    
  /**
   * Constructor for NPLMLanguageModel
   * 
   * @param filename
   * @throws IOException 
   */
  public NPLMLanguageModel(String filename) throws IOException {
  	name = String.format("NPLM(%s)", filename);
  	kenlm = new KenLM(filename);
  	order = kenlm.order();
  	
  	System.err.println("# Loading NPLMLanguageModel ...");
  	
  	// load src-conditioned info
    BufferedReader br = new BufferedReader(new FileReader(filename));
    String line;
    int vocabSize=0; // = tgtVocabSize + srcVocabSize
    int tgtVocabSize=0;
    int srcOrder=0;
    while((line=br.readLine())!=null){
      if (line.startsWith(INPUT_VOCAB_SIZE)) {
        vocabSize = Integer.parseInt(line.substring(INPUT_VOCAB_SIZE.length()+1));
      } else if (line.startsWith(OUTPUT_VOCAB_SIZE)) {
        tgtVocabSize = Integer.parseInt(line.substring(OUTPUT_VOCAB_SIZE.length()+1));
      } else if (line.startsWith(SRC_ORDER)) {
        srcOrder = Integer.parseInt(line.substring(SRC_ORDER.length()+1));
      } else if (line.startsWith("\\input_vocab")) { // stop reading
        break;
      }
    }
    int srcVocabSize=vocabSize-tgtVocabSize;
    
    // load tgtWords first
    tgtWords = new ArrayList<IString>(); 
    for (int i = 0; i < tgtVocabSize; i++) {
      tgtWords.add(new IString(br.readLine()));
      
      if(i==0) { System.err.println("  first tgt word=" + tgtWords.get(i)); }
      else if(i==(tgtVocabSize-1)) { System.err.println("  last tgt word=" + tgtWords.get(i)); }
    }

    // load srcWords
    srcWords = new ArrayList<IString>();
    for (int i = 0; i < srcVocabSize; i++) {
      srcWords.add(new IString(br.readLine()));
      
      if(i==0) { System.err.println("  first src word=" + srcWords.get(i)); }
      else if(i==(srcVocabSize-1)) { System.err.println("  last src word=" + srcWords.get(i)); }
    }
    br.readLine(); // empty line
    
    line = br.readLine(); // should be equal to "\output_vocab"
    if (!line.startsWith("\\output_vocab")) {
      System.err.println("! Expect \\output_vocab in NPLM model");
      System.exit(1);
    }
    br.close();

    /** create mapping **/
    System.err.println("unk=" + TokenUtils.UNK_TOKEN + ", start=" + TokenUtils.START_TOKEN + 
    		", IString.index.size = " + IString.index.size());
    srcVocabMap = new int[IString.index.size()];
    tgtVocabMap = new int[IString.index.size()];
    // initialize to -1, to make sure we don't map words not in NPLM to 0.
    for (int i = 0; i < IString.index.size(); i++) {
			srcVocabMap[i] = -1;
			tgtVocabMap[i] = -1;
		}
    // map tgtWords first
    for (int i = 0; i < tgtVocabSize; i++) {
    	tgtVocabMap[tgtWords.get(i).id] = i;
    }
    // map srcWords
    for (int i = 0; i < srcVocabSize; i++) {
    	srcVocabMap[srcWords.get(i).id] = i+tgtVocabSize;
    }
    
    // special tokens
    this.srcUnkVocabId = srcVocabMap[TokenUtils.UNK_TOKEN.id];
    this.tgtUnkVocabId = tgtVocabMap[TokenUtils.UNK_TOKEN.id];
    this.tgtStartVocabId = tgtVocabMap[TokenUtils.START_TOKEN.id];
    
    // replace -1 by unk id
    for (int i = 0; i < IString.index.size(); i++) {
			if(srcVocabMap[i] == -1) srcVocabMap[i] = this.srcUnkVocabId;
			if(tgtVocabMap[i] == -1) tgtVocabMap[i] = this.tgtUnkVocabId;
		}
    
    // ngram orders
    this.srcOrder = srcOrder;
    this.tgtOrder = order - this.srcOrder;
    System.err.println("  srcOrder=" + this.srcOrder + ", tgtOrder=" + this.tgtOrder + 
        ", srcVocabSize=" + srcVocabSize + ", tgtVocabSize=" + tgtVocabSize + 
        ", srcUnkVocabId=" + srcUnkVocabId + ", tgtUnkVocabId=" + srcUnkVocabId + ", tgtStartVocabId=" + tgtStartVocabId);
  }
  
  /**
   * Thang Mar14: factor out from the original score(Sequence<IString sequence) method
   * 
   * @param ngramIds: reversed ids
   * @return
   */
  public LMState score(int[] ngramIds) { 
    // got is (state_length << 32) | prob where prob is a float.
    long got = kenlm.marshalledScore(ngramIds);
    float score = Float.intBitsToFloat((int)(got & 0xffffffff));
    int stateLength = (int)(got >> 32);
    return new KenLMState(score, ngramIds, stateLength);
  }
  
  /**
   * 
   * @param sequence: sequence of words in normal order.
   * @return
   */
  public LMState score(Sequence<IString> sequence){
  	Util.error(sequence.size()!=order, "Currently, NPLMLanguageModel requires sequence " + sequence + 
  			" to have " + order + " tokens.");
  	int numTokens = sequence.size();
  	int[] ngramIds = new int[numTokens];
  	
  	IString tok;
  	
  	for (int i = 0; i<numTokens; i++) {
			tok = sequence.get(i);
			if(i<srcOrder) { // look up from tgt vocab
				ngramIds[numTokens-i-1] = (tok.id<srcVocabMap.length) ? srcVocabMap[tok.id] : srcUnkVocabId;
			} else {
				ngramIds[numTokens-i-1] = (tok.id<tgtVocabMap.length) ? tgtVocabMap[tok.id] : tgtUnkVocabId;
			}
		}
  	System.err.println(Util.sprint(ngramIds));
  	return score(ngramIds);
  }
  
  
  
  /** Getters & Setters **/
  public IString getSrcWord(int i){
  	return srcWords.get(i);
  }
  
  public IString getTgtWord(int i){
  	return tgtWords.get(i);
  }
  
  public int getSrcOrder() {
		return srcOrder;
	}

	public int getTgtOrder() {
		return tgtOrder;
	}
	
  public int getSrcUnkVocabId() {
		return srcUnkVocabId;
	}

	public int getTgtUnkVocabId() {
		return tgtUnkVocabId;
	}

	public int getTgtStartVocabId() {
		return tgtStartVocabId;
	}
	
	public int[] getSrcVocabMap() {
		return srcVocabMap;
	}
	
	public int[] getTgtVocabMap() {
		return tgtVocabMap;
	}

  @Override
  public IString getStartToken() {
    return TokenUtils.START_TOKEN;
  }

  @Override
  public IString getEndToken() {
    return TokenUtils.END_TOKEN;
  }

	@Override
	public String getName() {
		return name;
	}

	@Override
	public int order() {
		return order;
	}

	@Override
	public LMState score(Sequence<IString> sequence, int startOffsetIndex,
			LMState priorState) {
		// TODO Auto-generated method stub
		return null;
	}
}

//private final Map<Integer, IString> srcReverseVocabMap;
//private final Map<Integer, IString> tgtReverseVocabMap;
//tgtReverseVocabMap = new HashMap<Integer, IString>();
//tgtReverseVocabMap.put(i, new IString(line));
//srcReverseVocabMap = new HashMap<Integer, IString>();
//srcReverseVocabMap.put(i+tgtVocabSize, new IString(line));

