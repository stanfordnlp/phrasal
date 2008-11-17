
#include "Prob.h"
#include "Ngram.h"
#include "Vocab.h"
#include "srilm.h"

#include <cstdio>
#include <cstring>
#include <cmath>

Vocab *swig_srilm_vocab;
const float BIGNEG = -99;

// Initialize the ngram model
Ngram* initLM(int order, int start_id, int end_id) {
  swig_srilm_vocab = new Vocab(start_id, end_id);
  return new Ngram(*swig_srilm_vocab, order);
}

// Get index for given string
unsigned getIndexForWord(const char *s) {
  unsigned ans;
  ans = swig_srilm_vocab->addWord((VocabString)s);
  if(ans == Vocab_None) {
    printf("Trying to get index for Vocab_None.\n");
  }
  return ans;
}

// Get the word for a given index
const char* getWordForIndex(unsigned i) {
  return swig_srilm_vocab->getWord((VocabIndex)i);
}

// Read in an LM file into the model
int readLM(Ngram* ngram, const char* filename) {
    File file(filename, "r");
    if(!file) {
        fprintf(stderr,"Error:: Could not open file %s\n", filename);
        return 0;
    }
    else 
        return ngram->read(file, 0);
}

// Get word probability
float getWordProb(Ngram* ngram, unsigned w, unsigned* context) {
    return (float)ngram->wordProb(w, context);
}


// Get trigram probability
float getProb_lzf(Ngram* ngram, unsigned *context, int hist_size, unsigned cur_wrd) {
    unsigned hist[hist_size+1];
    for(int i=hist_size-1; i>=0; i--)//reverse
	hist[hist_size-1-i] = (VocabIndex)context[i];
    hist[hist_size]=Vocab_None;
    return getWordProb(ngram, cur_wrd, hist);
}

unsigned getBOW_depth(Ngram* ngram, unsigned *context, int hist_size) {
    unsigned hist[hist_size+1];
    for(int i=hist_size-1; i>=0; i--)//reverse
	hist[hist_size-1-i] = (VocabIndex)context[i];
    hist[hist_size]=Vocab_None;
    unsigned depth;
    ngram->contextID(Vocab_None, hist, depth);    
    return depth;
}


//return sum of backoff weights of context that has equal or greater length than min_len
//min_len=hist_size-n_add_bow+1
float get_backoff_weight_sum(Ngram* ngram, unsigned *context, int hist_size, int min_len) {
    unsigned hist[hist_size+1];
    for(int i=hist_size-1; i>=0; i--)//reverse
	hist[hist_size-1-i] = (VocabIndex)context[i];
    hist[hist_size]=Vocab_None;
    
    return ngram->contextBOW(hist, min_len);    
}



//vocabulary related 
Vocab* initVocab(int start, int end) {
  return new Vocab(start, end);
}
// Get index for given string
unsigned getIndexForWord_Vocab(Vocab* vo, const char *s) {
  unsigned ans;
  ans = vo->addWord((VocabString)s);
  if(ans == Vocab_None) {
    printf("Trying to get index for Vocab_None.\n");
  }
  return ans;
}


void write_default_vocab_map(const char *fname){
    File file(fname, "w");
    if(!file)
        fprintf(stderr,"Error:: Could not open file %s\n", fname);
    else
 	swig_srilm_vocab->writeIndexMap(file);
}


void write_vocab_map(Vocab* vo, const char *fname){
    File file(fname, "w");
    if(!file)
        fprintf(stderr,"Error:: Could not open file %s\n", fname);
    else
 	vo->writeIndexMap(file);
}

// Get the word for a given index
const char* getWordForIndex_Vocab(Vocab* vo, unsigned i) {
  return vo->getWord((VocabIndex)i);
}

int getVocab_None(){
   return Vocab_None;
}



