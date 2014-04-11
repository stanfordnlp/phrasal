package edu.stanford.nlp.mt.lm;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import edu.stanford.nlp.lm.KenLM;
import edu.stanford.nlp.lm.NPLM;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.TokenUtils;
import edu.stanford.nlp.mt.util.MurmurHash;
import edu.stanford.nlp.mt.util.Util;

/**
 * NPLM Language Model interface backed by KenLanguageModel
 * 
 * @author Thang Luong
 *
 */
public class SrcNPLM implements LanguageModel<IString> {
	private NPLM nplm;
  //private KenLM kenlm;
	
  private final String INPUT_VOCAB_SIZE = "input_vocab_size";
  private final String OUTPUT_VOCAB_SIZE = "output_vocab_size";
  private final String SRC_ORDER = "src_ngram_size";
  
  private final String name;
  private final int order;
  private final int srcOrder;
  private final int srcWindow; // = (srcOrder-1)/2
	private final int tgtOrder;
	
	// vocabulary
	private final List<IString> srcWords;
	private final List<IString> tgtWords;
	private int srcVocabSize;
  private int tgtVocabSize;
  
	// map IString id to NPLM id
  private final int[] srcVocabMap;
	private final int[] tgtVocabMap;
  
	// map NPLM id to IString id
  private final int[] reverseVocabMap;

  // NPLM id
  private final int srcUnkNPLMId;
  private final int tgtUnkNPLMId;
  private final int srcStartNPLMId;
  private final int tgtStartNPLMId;
  private final int srcEndNPLMId;
//  private final int tgtEndNPLMId;
  
  // we're not handling <null> right now so does NPLM
//  private final String NULL = "<null>";
//  private final int srcNullNPLMId;
//  private final int tgtNullNPLMId;
	
  // caching
  private long cacheHit=0, cacheLookup = 0;
  private ConcurrentHashMap<Long, Float> cacheMap = null;
  
  private int DEBUG = 0; // 0: no print-out, 1: minimal print out
  /**
   * Constructor for NPLMLanguageModel
   * 
   * @param filename
   * @throws IOException 
   */
  public SrcNPLM(String filename, int cacheSize) throws IOException {
  	//System.err.println("# Loading NPLMLanguageModel ...");
  	name = String.format("NPLM(%s)", filename);
  	nplm = new NPLM(filename, 0);
  	order = nplm.order();
  	//kenlm = new KenLM(filename, 1<<20);
  	//order = kenlm.order();
  	
  	// cache
  	if (cacheSize>0){
      if(DEBUG>0) { System.err.println("  Use global caching, size=" + cacheSize); }
  		cacheMap = new ConcurrentHashMap<Long, Float>(cacheSize);
  	}

  	// load src-conditioned info
  	BufferedReader br = new BufferedReader(new FileReader(filename));
    String line;
    int srcOrder=0, vocabSize=0;
    while((line=br.readLine())!=null){
      if (line.startsWith(INPUT_VOCAB_SIZE)) {
        vocabSize = Integer.parseInt(line.substring(INPUT_VOCAB_SIZE.length()+1));
      } else if (line.startsWith(OUTPUT_VOCAB_SIZE)) {
        this.tgtVocabSize = Integer.parseInt(line.substring(OUTPUT_VOCAB_SIZE.length()+1));
      } else if (line.startsWith(SRC_ORDER)) {
        srcOrder = Integer.parseInt(line.substring(SRC_ORDER.length()+1));
      } else if (line.startsWith("\\input_vocab")) { // stop reading
        break;
      }
    }
    // = tgtVocabSize + srcVocabSize
    this.srcVocabSize=vocabSize-tgtVocabSize;
    
    // load tgtWords first
    tgtWords = new ArrayList<IString>(); 
    for (int i = 0; i < tgtVocabSize; i++) {
      tgtWords.add(new IString(br.readLine()));
      
      if(DEBUG>0 && i==0) { System.err.println("  first tgt word=" + tgtWords.get(i)); }
      else if(i==(tgtVocabSize-1)) { System.err.println("  last tgt word=" + tgtWords.get(i)); }
    }

    // load srcWords
    srcWords = new ArrayList<IString>();
    for (int i = 0; i < srcVocabSize; i++) {
      srcWords.add(new IString(br.readLine()));
      
      if(DEBUG>0 && i==0) { System.err.println("  first src word=" + srcWords.get(i)); }
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
    // Important: DO NOT remove this line, we need it to get the correct size of IString.index.size() in the subsequent code
    System.err.println("  unk=" + TokenUtils.UNK_TOKEN + ", start=" + TokenUtils.START_TOKEN 
    		 + ", end=" + TokenUtils.END_TOKEN  + ", IString.index.size = " + IString.index.size());
    srcVocabMap = new int[IString.index.size()];
    tgtVocabMap = new int[IString.index.size()];
    reverseVocabMap = new int[srcVocabSize+tgtVocabSize];
    
    // initialize to -1, to make sure we don't map words not in NPLM to 0.
    for (int i = 0; i < IString.index.size(); i++) {
			srcVocabMap[i] = -1;
			tgtVocabMap[i] = -1;
		}
    // map tgtWords first
    for (int i = 0; i < tgtVocabSize; i++) {
    	tgtVocabMap[tgtWords.get(i).id] = i;
    	reverseVocabMap[i] = tgtWords.get(i).id;
    }
    // map srcWords
    for (int i = 0; i < srcVocabSize; i++) {
    	srcVocabMap[srcWords.get(i).id] = i+tgtVocabSize;
    	reverseVocabMap[i+tgtVocabSize] = srcWords.get(i).id;
    }
    
    // special tokens
    this.srcUnkNPLMId = srcVocabMap[TokenUtils.UNK_TOKEN.id];
    this.tgtUnkNPLMId = tgtVocabMap[TokenUtils.UNK_TOKEN.id];
    this.srcStartNPLMId = srcVocabMap[TokenUtils.START_TOKEN.id];
    this.tgtStartNPLMId = tgtVocabMap[TokenUtils.START_TOKEN.id];
    this.srcEndNPLMId = srcVocabMap[TokenUtils.END_TOKEN.id];
//    this.tgtEndNPLMId = tgtVocabMap[TokenUtils.END_TOKEN.id];
    
    // replace -1 by unk id
    for (int i = 0; i < IString.index.size(); i++) {
			if(srcVocabMap[i] == -1) srcVocabMap[i] = this.srcUnkNPLMId;
			if(tgtVocabMap[i] == -1) tgtVocabMap[i] = this.tgtUnkNPLMId;
		}
    
    // ngram orders
    this.srcOrder = srcOrder;
    this.tgtOrder = order - this.srcOrder;
    this.srcWindow = (srcOrder-1)/2;
    
    if(DEBUG>0){
	    System.err.println("  srcOrder=" + this.srcOrder + ", tgtOrder=" + this.tgtOrder + 
	        ", srcVocabSize=" + srcVocabSize + ", tgtVocabSize=" + tgtVocabSize + 
	        ", srcUnkNPLMId=" + srcUnkNPLMId + ", tgtUnkNPLMId=" + srcUnkNPLMId +
	        ", srcStartNPLMId=" + srcStartNPLMId + ", tgtStartNPLMId=" + srcStartNPLMId +
	        ", srcEndNPLMId=" + srcEndNPLMId + ", tgtEndNPLMId=" + srcEndNPLMId);
    }
  }
  
  /**
   * Score a single ngram.
   * 
   * @param ngramIds: normal order ids
   * @return
   */
  public double score(int[] ngramIds) {
  	long key = 0;
  	double score;
  	
    if(cacheMap != null) { // caching
    	cacheLookup++;
    	byte[] data = Util.toByteArray(ngramIds, ngramIds.length); 
    	key = MurmurHash.hash64(data, data.length);
    	
    	if(cacheMap.containsKey(key)) { // cache hit
    		score = cacheMap.get(key);
    		if(cacheHit++ %1000000==0) { System.err.println("cache hit=" + cacheHit + ", cache lookup=" + cacheLookup + ", cache size=" + cacheMap.size()); }
    	} else { // cache miss
    	  score = nplm.scoreNgram(ngramIds);
    	  cacheMap.put(key, (float) score);
    	}
    } else {
      score = nplm.scoreNgram(ngramIds);
    }
          
//    System.err.println(Arrays.toString(ngramIds) + "\t" + score);
    return score;
  }
  
  /**
   * Score multiple ngrams.
   * 
   * @param ngramIds: normal order ids
   * @return
   */
  public double[] scoreMultiNgrams(List<int[]> ngramList) {
    int numNgrams = ngramList.size();
    double[] scores = new double[numNgrams];
    
    int key = 0;
    if(cacheMap != null) { // caching
      List<Integer> remainedIndices = new LinkedList<Integer>(); // those that we will call NPLMs
      List<int[]> remainedNgrams = new LinkedList<int[]>();
      int i=0;
      
      // get precomputed scores
      Iterator<int[]> iter = ngramList.iterator(); 
      while(iter.hasNext()){ 
        int[] ngram = iter.next();
        
        byte[] data = Util.toByteArray(ngram, ngram.length); 
        key = MurmurHash.hash32(data, data.length);
      
        cacheLookup++;
        if(cacheMap.containsKey(key)) { // cache hit
          // get cache results
          scores[i] = cacheMap.get(key);
          
          // remove ngram
          iter.remove();
          
          if(cacheHit++ % 1000000==0) { System.err.println("cache hit=" + cacheHit + ", cache lookup=" + cacheLookup + ", cache size=" + cacheMap.size()); }
        } else { // cache miss
          remainedIndices.add(i);
          remainedNgrams.add(ngram);
        }
        
        i++;
      }
      
      // get remaining scores
      double[] remainedScores = nplm.scoreMultiNgrams(remainedNgrams);
      i=0;
      for (int remainedId : remainedIndices) {
        scores[remainedId] =  remainedScores[i++];
      }
    } else {
      scores = nplm.scoreMultiNgrams(ngramList);
    }

    return scores;
  }
  
  /**
   * Score a sequence of IString
   * 
   * @param sequence: sequence of words in normal order.
   * @return
   */
  public double score(Sequence<IString> sequence){
  	return score(toId(sequence));
  }
  
  public int[] toId(Sequence<IString> sequence){
    int numTokens = sequence.size();
    int[] ngramIds = new int[numTokens];
    
    IString tok;
    
    for (int i = 0; i<numTokens; i++) {
      tok = sequence.get(i);
      if(i<srcOrder) { // look up from tgt vocab
        ngramIds[i] = (tok.id<srcVocabMap.length) ? srcVocabMap[tok.id] : srcUnkNPLMId;
      } else {
        ngramIds[i] = (tok.id<tgtVocabMap.length) ? tgtVocabMap[tok.id] : tgtUnkNPLMId;
      }
    }
    
    return ngramIds;
  }
  
  public Sequence<IString> toIString(int[] ngramIds){
    int numTokens = ngramIds.length;
    int[] istringIndices = new int[numTokens];
    for (int i = 0; i<numTokens; i++) {
      istringIndices[i] = reverseVocabMap[ngramIds[i]];
    }
    return IString.getIStringSequence(istringIndices);
  }
  
  /**
   * Extract ngrams that we want to score after adding the recently added phrase pair. 
   * 
   * @param srcSent
   * @param tgtSent
   * @param alignment -- alignment of the recently added phrase pair
   * @param srcStartPos -- src start position of the recently added phrase pair. 
   * @param tgtStartPos -- tgt start position of the recently added phrase pair.
   * @return list of ngrams, each of which consists of NPLM ids.
   */
	public LinkedList<int[]> extractNgrams(Sequence<IString> srcSent, Sequence<IString> tgtSent, 
			PhraseAlignment alignment, int srcStartPos, int tgtStartPos){
		LinkedList<int[]> ngramList = new LinkedList<int[]>();
		
		for (int pos = tgtStartPos; pos < tgtSent.size(); pos++) {
      ngramList.add(extractNgram(pos, srcSent, tgtSent, alignment, srcStartPos, tgtStartPos));
    }
		
		return ngramList;
	}
  
	/**
   * Extract an ngram. 
   * 
   * @param pos -- tgt position of the last word in the ngram to be extracted.
   * @param srcSent
   * @param tgtSent
   * @param alignment -- alignment of the recently added phrase pair
   * @param srcStartPos -- src start position of the recently added phrase pair. 
   * @param tgtStartPos -- tgt start position of the recently added phrase pair.
   * @return list of ngrams, each of which consists of NPLM ids.
   */
  public int[] extractNgram(int pos, Sequence<IString> srcSent, Sequence<IString> tgtSent, 
      PhraseAlignment alignment, int srcStartPos, int tgtStartPos){
    int id, srcAvgPos;
    int[] ngram = new int[order]; // will be stored in normal order (cf. KenLM stores in reverse order)
    
    if(pos==(tgtSent.size()-1) && tgtSent.get(pos).id==TokenUtils.END_TOKEN.id) { // end of sent
      srcAvgPos = srcSent.size()-1;
    } else {
      // get the local srcAvgPos within the current srcPhrase
      // pos-startPos: position within the local target phrase
      srcAvgPos = alignment.findSrcAvgPos(pos-tgtStartPos); 
      assert(srcAvgPos>=0);
      
      // convert to the global position within the source sent
      srcAvgPos += srcStartPos;
    }
    
    // extract src subsequence
    int srcSeqStart = srcAvgPos-srcWindow;
    int srcSeqEnd = srcAvgPos+srcWindow;
    int i=0;
    for (int srcPos = srcSeqStart; srcPos <= srcSeqEnd; srcPos++) {
      if(srcPos<0) { id = srcStartNPLMId; } // start
      else if (srcPos>=srcSent.size()) { id = srcEndNPLMId; } // end
      else { // within range
        IString srcTok = srcSent.get(srcPos);
        if(srcTok.id<srcVocabMap.length) { id = srcVocabMap[srcTok.id]; } // known
        else { id = srcUnkNPLMId; }  // unk
      }
      ngram[i++] = id;
    }
    assert(i==srcOrder);
    
    // extract tgt subsequence
    int tgtSeqStart = pos - tgtOrder + 1;
    for (int tgtPos = tgtSeqStart; tgtPos <= pos; tgtPos++) {        
      if(tgtPos<0) { id = tgtStartNPLMId; } // start
      else { // within range 
        IString tgtTok = tgtSent.get(tgtPos);
        if(tgtTok.id<tgtVocabMap.length) { id = tgtVocabMap[tgtTok.id]; } // known
        else { id = tgtUnkNPLMId; } // unk
      }
      ngram[i++] = id;
    }
    assert(i==order);
    
    return ngram;
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
	
  public int getSrcUnkNPLMId() {
		return srcUnkNPLMId;
	}

	public int getTgtUnkNPLMId() {
		return tgtUnkNPLMId;
	}

	public int getSrcStartVocabId() {
		return srcStartNPLMId;
	}
	
	public int getTgtStartVocabId() {
		return tgtStartNPLMId;
	}
	
	public int getSrcEndVocabId() {
		return srcEndNPLMId;
	}
	
	public int[] getSrcVocabMap() {
		return srcVocabMap;
	}
	
	public int[] getTgtVocabMap() {
		return tgtVocabMap;
	}

  public int getSrcVocabSize(){
    return srcVocabSize;
  }

  public int getTgtVocabSize(){
    return tgtVocabSize;
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

