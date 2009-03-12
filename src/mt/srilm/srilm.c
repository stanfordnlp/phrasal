
#include "Prob.h"
#include "Ngram.h"
#include "Vocab.h"
#include "srilm.h"

#include <cstdio>
#include <cstring>
#include <cmath>

// Initialize the vocabulary:
Vocab* initVocab(int start, int end) {
  return new Vocab(start, end);
}

// Initialize the ngram model:
Ngram* initLM(int order, Vocab* vocab) {
  return new Ngram(*vocab, order);
}

// Get index for given string and given Vocab:
unsigned getIndexForWord(Vocab* vo, const char *s) {
  unsigned ans;
  ans = vo->addWord((VocabString)s);
  if(ans == Vocab_None) {
    printf("Trying to get index for Vocab_None.\n");
  }
  return ans;
}

// Read in an LM file into the model:
int readLM(Ngram* ngram, const char* filename) {
	File file(filename, "r");
	if(!file) {
		fprintf(stderr,"Error:: Could not open file %s\n", filename);
		return 0;
	}
	else 
		return ngram->read(file, 0);
}

// Get word probability:
float getWordProb(Ngram* ngram, unsigned w, unsigned* context) {
    return (float)ngram->wordProb(w, context);
}

// Get n-gram probability:
float getProb(Ngram* ngram, unsigned *context, int hist_size, unsigned cur_wrd) {
	unsigned hist[hist_size+1];
	for(int i=hist_size-1; i>=0; --i)
		hist[hist_size-1-i] = (VocabIndex)context[i];
	hist[hist_size]=Vocab_None;
	return getWordProb(ngram, cur_wrd, hist);
}

unsigned getDepth(Ngram* ngram, unsigned *context, int hist_size) {
	unsigned hist[hist_size+1];
	for(int i=hist_size-1; i>=0; --i)
		hist[hist_size-1-i] = (VocabIndex)context[i];
	hist[hist_size]=Vocab_None;
	unsigned depth;
	ngram->contextID(Vocab_None, hist, depth);    
	return depth;
}

int getVocab_None() {
   return Vocab_None;
}
