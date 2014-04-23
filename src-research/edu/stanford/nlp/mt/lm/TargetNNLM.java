package edu.stanford.nlp.mt.lm;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

import edu.stanford.nlp.lm.NPLM;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.PhraseAlignment;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.TokenUtils;
import edu.stanford.nlp.mt.util.MurmurHash;
import edu.stanford.nlp.mt.util.NNLMUtil;

/**
 * Target NNLM (conditioned on tgt words). 
 * Support caching. Backed by NPLM.
 * 
 * @author Thang Luong
 *
 */
public class TargetNNLM implements NNLM {
	protected NPLM nplm;
  //protected KenLM kenlm;
	
  protected final String INPUT_VOCAB_SIZE = "input_vocab_size";
  protected final String OUTPUT_VOCAB_SIZE = "output_vocab_size";
  
  protected String name;
  protected int order;
  protected int tgtOrder;
	
	// vocabulary	
	protected List<IString> tgtWords;
  protected int tgtVocabSize;
  
	// map IString id to NPLM id
	protected int[] tgtVocabMap;
  
	// map NPLM id to IString id
  protected int[] reverseVocabMap;

  // NPLM id
  protected int tgtUnkNPLMId;
  protected int tgtStartNPLMId;
  
  // we're not handling <null> right now so does NPLM
//  protected String NULL = "<null>";
//  protected int tgtNullNPLMId;
	
  // caching
  protected long cacheHit=0, cacheLookup = 0;
  protected ConcurrentLinkedHashMap<Long, Float> cacheMap = null;
//  protected ConcurrentHashMap<Long, Float> cacheMap = null;
//  LinkedList<Long> lruKeys = null; // keep recent keys at the end
  protected int cacheSize;
  
  private boolean DEBUG = true;
  
  protected TargetNNLM(){}
  
  /**
   * Constructor for TargetNNLM
   * 
   * @param filename
   * @throws IOException 
   */
  public TargetNNLM(String filename, int cacheSize, int miniBatchSize) throws IOException {
  	//System.err.println("# Loading NPLMLanguageModel ...");
  	name = String.format("TargetNNLM(%s)", filename);
  	nplm = new NPLM(filename, 0, miniBatchSize);
  	order = nplm.order();
  	tgtOrder = order;
  	//kenlm = new KenLM(filename, 1<<20);
  	//order = kenlm.order();
  	
  	// cache
  	this.cacheSize = cacheSize;
  	if (cacheSize>0){
      if(DEBUG) { System.err.println("  Use global caching, size=" + cacheSize); }
      cacheMap = new ConcurrentLinkedHashMap.Builder<Long, Float>().maximumWeightedCapacity(cacheSize).build();
//  		cacheMap = new ConcurrentHashMap<Long, Float>(cacheSize);
//  		lruKeys = new LinkedList<Long>();
  	}

  	// load src-conditioned info
  	BufferedReader br = new BufferedReader(new FileReader(filename));
    String line;
    while((line=br.readLine())!=null){
      if (line.startsWith(OUTPUT_VOCAB_SIZE)) {
        this.tgtVocabSize = Integer.parseInt(line.substring(OUTPUT_VOCAB_SIZE.length()+1));
      } else if (line.startsWith("\\input_vocab")) { // stop reading
        break;
      }
    }
    
    // load tgtWords first
    tgtWords = new ArrayList<IString>(); 
    for (int i = 0; i < tgtVocabSize; i++) {
      tgtWords.add(new IString(br.readLine()));
      
      if(DEBUG && i==0) { System.err.println("  first tgt word=" + tgtWords.get(i)); }
      else if(i==(tgtVocabSize-1)) { System.err.println("  last tgt word=" + tgtWords.get(i)); }
    }
    br.readLine(); // empty line
    
    line = br.readLine(); // should be equal to "\output_vocab"
    if (!line.startsWith("\\output_vocab")) {
      br.close();
      throw new RuntimeException("! Expect \"\\output_vocab\", but receive \"" + line + "\"");
    }
    br.close();

    /** create mapping **/
    // Important: DO NOT remove this line, we need it to get the correct size of IString.index.size() in the subsequent code
    System.err.println("  unk=" + TokenUtils.UNK_TOKEN + ", start=" + TokenUtils.START_TOKEN 
    		 + ", end=" + TokenUtils.END_TOKEN  + ", IString.index.size = " + IString.index.size());
    tgtVocabMap = new int[IString.index.size()];
    reverseVocabMap = new int[tgtVocabSize];
    
    // initialize to -1, to make sure we don't map words not in NPLM to 0.
    for (int i = 0; i < IString.index.size(); i++) {
			tgtVocabMap[i] = -1;
		}
    // map tgtWords first
    for (int i = 0; i < tgtVocabSize; i++) {
    	tgtVocabMap[tgtWords.get(i).id] = i;
    	reverseVocabMap[i] = tgtWords.get(i).id;
    }
    
    // special tokens
    this.tgtUnkNPLMId = tgtVocabMap[TokenUtils.UNK_TOKEN.id];
    this.tgtStartNPLMId = tgtVocabMap[TokenUtils.START_TOKEN.id];
    
    // replace -1 by unk id
    for (int i = 0; i < IString.index.size(); i++) {
			if(tgtVocabMap[i] == -1) tgtVocabMap[i] = this.tgtUnkNPLMId;
		}
    
    if(DEBUG){ System.err.println("  tgtOrder=" + this.tgtOrder + ", tgtVocabSize=" + tgtVocabSize +  ", tgtUnkNPLMId=" + tgtUnkNPLMId + ", tgtStartNPLMId=" + tgtStartNPLMId); }
  }
  
  /**
   * Score a single ngram.
   * 
   * @param ngramIds: normal order ids
   * @return
   */
  public double scoreNgram(int[] ngramIds) {
//    System.err.println(Arrays.toString(ngramIds));
    
  	long key = 0;
  	double score;
  	Float scoreFloat;
  	
    if(cacheMap != null) { // caching
    	cacheLookup++;
    	byte[] data = NNLMUtil.toByteArray(ngramIds, ngramIds.length); 
    	key = MurmurHash.hash64(data, data.length);
    	
    	scoreFloat = cacheMap.get(key);
    	if(scoreFloat!=null) { // cache hit
    	  score = scoreFloat;
    		if(++cacheHit % (cacheSize/10) == 0) {
    		  System.err.println("cache hit=" + cacheHit + ", cache lookup=" + cacheLookup + ", cache size=" + cacheMap.size());
//    		  clearCache(); 
    		}
    	} else { // cache miss
    	  score = nplm.scoreNgram(ngramIds);
    	  cacheMap.putIfAbsent(key, (float) score);
//    	  lruKeys.addLast(key);
    	}
    } else {
      score = nplm.scoreNgram(ngramIds);
    }
    
    return score;
  }
  
  /**
   * Score multiple ngrams.
   * 
   * @param ngramIds: normal order ids
   * @return
   */
  @Override
  public double[] scoreNgrams(int[][] ngrams) {
    int numNgrams = ngrams.length;
    double[] scores = new double[numNgrams];
    
    long key = 0;
    Float scoreFloat;
    if(cacheMap != null) { // caching
      List<Integer> remainedIndices = new ArrayList<Integer>(); // those that we will call NPLMs
      List<int[]> remainedNgrams = new ArrayList<int[]>();
      List<Long> remainedHashKeys = new ArrayList<Long>();
      
      // get precomputed scores
      for (int i = 0; i < numNgrams; i++) {
        int[] ngram = ngrams[i];
        byte[] data = NNLMUtil.toByteArray(ngram, ngram.length); 
        key = MurmurHash.hash64(data, data.length);
      
        cacheLookup++;
        scoreFloat = cacheMap.get(key);
        if(scoreFloat!=null) { // cache hit
          scores[i] = scoreFloat;
          if(++cacheHit % (cacheSize/10) == 0) { 
            System.err.println("cache hit=" + cacheHit + ", cache lookup=" + cacheLookup + ", cache size=" + cacheMap.size());
//            clearCache(); 
          }
        } else { // cache miss
          remainedIndices.add(i);
          remainedNgrams.add(ngram);
          remainedHashKeys.add(key);
        }
      }
      
      // get remaining scores
      double[] remainedScores = nplm.scoreNgrams(NNLMUtil.convertNgramList(remainedNgrams));
      
      // put to scores and cache
      for (int i = 0; i < remainedScores.length; i++) {
        key = remainedHashKeys.get(i);
        scores[remainedIndices.get(i)] =  remainedScores[i];
        cacheMap.putIfAbsent(key, (float) remainedScores[i]);
//        lruKeys.add(key);
      }
    } else {
      scores = nplm.scoreNgrams(ngrams);
    }

    return scores;
  }
  

//  private synchronized void clearCache(){
//    if(cacheMap.size()>cacheSize) {
//      // empty half
//      int count = 0;
//      Iterator<Long> it = lruKeys.iterator();
//      while(it.hasNext()){
//        long key = it.next();
//        
//        // remove key
//        it.remove(); 
//        cacheMap.remove(key);
//        if (count++ > 0.5*cacheSize) { break; }
//      }
//      System.err.println("new cache size = " + cacheMap.size());
//    }
//  }
  
  /**
   * Score a sequence of IString
   * 
   * @param sequence: sequence of words in normal order.
   * @return
   */
  public double scoreNgram(Sequence<IString> sequence){
  	return scoreNgram(toId(sequence));
  }
  
  @Override
  public int[] toId(Sequence<IString> sequence){
    int numTokens = sequence.size();
    int[] ngramIds = new int[numTokens];
    
    IString tok;
    
    for (int i = 0; i<numTokens; i++) {
      tok = sequence.get(i);
      ngramIds[i] = (tok.id<tgtVocabMap.length) ? tgtVocabMap[tok.id] : tgtUnkNPLMId;
    }
    
    return ngramIds;
  }
  
  @Override
  public Sequence<IString> toIString(int[] ngramIds){
    int numTokens = ngramIds.length;
    int[] istringIndices = new int[numTokens];
    for (int i = 0; i<numTokens; i++) {
      istringIndices[i] = reverseVocabMap[ngramIds[i]];
    }
    return IString.getIStringSequence(istringIndices);
  }
  
	/**
   * Extract ngrams that we want to score after adding a phrase pair. 
   * 
   * @param srcSent
   * @param tgtSent
   * @param alignment -- alignment of the recently added phrase pair
   * @param srcStartPos -- src start position of the recently added phrase pair. 
   * @param tgtStartPos -- tgt start position of the recently added phrase pair.
   * @return list of ngrams, each of which consists of NPLM ids.
   */
  @Override
  public int[][] extractNgrams(Sequence<IString> srcSent, Sequence<IString> tgtSent, 
      PhraseAlignment alignment, int srcStartPos, int tgtStartPos){
    int tgtLen = tgtSent.size();
    int[][] ngrams = new int[tgtLen-tgtStartPos][];
    
    int i = 0;
    for (int pos = tgtStartPos; pos < tgtLen; pos++) {
      ngrams[i++] = extractNgram(pos, srcSent, tgtSent, alignment, srcStartPos, tgtStartPos);
    }
    
    return ngrams;
  }
      
  /**
   * Extract an ngram. 
   * 
   * @param pos -- tgt position of the last word in the ngram to be extracted (should be >= tgtStartPos, < tgtSent.size())
   * @param srcSent
   * @param tgtSent
   * @param alignment -- alignment of the recently added phrase pair
   * @param srcStartPos -- src start position of the recently added phrase pair. 
   * @param tgtStartPos -- tgt start position of the recently added phrase pair.
   * @return list of ngrams, each of which consists of NPLM ids.
   */
  public int[] extractNgram(int pos, Sequence<IString> srcSent, Sequence<IString> tgtSent, 
      PhraseAlignment alignment, int srcStartPos, int tgtStartPos){
    /* we don't use srcSent, alignment, srcStartPos */
    
    int tgtLen = tgtSent.size();
    assert(pos>=tgtStartPos && pos<tgtLen);
    
    int id;
    int[] ngram = new int[tgtOrder]; // will be stored in normal order (cf. KenLM stores in reverse order)
    
    
    // extract tgt subsequence
    int i = 0;
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
    assert(i==tgtOrder);
    
    return ngram;
  }
  
  /** Getters & Setters **/
  public IString getTgtWord(int i){
  	return tgtWords.get(i);
  }

	public int getTgtOrder() {
		return tgtOrder;
	}

	public int getTgtUnkNPLMId() {
		return tgtUnkNPLMId;
	}

	public int getTgtStartVocabId() {
		return tgtStartNPLMId;
	}
		
	public int[] getTgtVocabMap() {
		return tgtVocabMap;
	}

  public int getTgtVocabSize(){
    return tgtVocabSize;
  }

	@Override
	public String getName() {
		return name;
	}

	@Override
	public int order() {
		return order;
	}
}

///**
// * Extract ngrams that we want to score after adding the recently added phrase pair. 
// * 
// * @param tgtSent 
// * @param tgtStartPos -- tgt start position of the recently added phrase pair.
// * @return list of ngrams, each of which consists of NPLM ids.
// */
//public LinkedList<int[]> extractNgrams(Sequence<IString> tgtSent, int tgtStartPos){
//  LinkedList<int[]> ngramList = new LinkedList<int[]>();
//  
//  for (int pos = tgtStartPos; pos < tgtSent.size(); pos++) {
//    ngramList.add(extractNgram(pos, tgtSent, tgtStartPos));
//  }
//  
//  return ngramList;
//}

///**
// * Extract an ngram. 
// * 
// * @param pos -- tgt position of the last word in the ngram to be extracted.
// * @param tgtSent
// * @param tgtStartPos -- tgt start position of the recently added phrase pair.
// * @return list of ngrams, each of which consists of NPLM ids.
// */
//public int[] extractNgram(int pos, Sequence<IString> tgtSent, int tgtStartPos){