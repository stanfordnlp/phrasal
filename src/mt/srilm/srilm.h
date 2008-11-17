#ifndef SRILMWRAP_H
#define SRILMWRAP_H

#ifdef __cplusplus
  extern "C" {
#else
    typedef struct Ngram Ngram; /* dummy type to stand in for class */
    typedef struct Vocab Vocab; /* dummy type to stand in for class *///add by lzf
#endif

Ngram* initLM(int order, int start_id, int end_id);
Vocab* initVocab(int start, int end);

unsigned getIndexForWord(const char* s);
const char* getWordForIndex(unsigned i);
int readLM(Ngram* ngram, const char* filename);
float getWordProb(Ngram* ngram, unsigned word, unsigned* context);
float getProb_lzf(Ngram* ngram, unsigned *context, int hist_size, unsigned cur_wrd); //add by lzf
unsigned getBOW_depth(Ngram* ngram, unsigned *context, int hist_size);//add by lzf
float get_backoff_weight_sum(Ngram* ngram, unsigned *context, int hist_size, int min_len);//add by lzf

int getVocab_None(); //add by lzf
void write_vocab_map(Vocab* vo, const char *fname);//by lzf
void write_default_vocab_map(const char *fname);//by lzf

const char* getWordForIndex_Vocab(Vocab* vo, unsigned i);
unsigned getIndexForWord_Vocab(Vocab* vo, const char *s);


#ifdef __cplusplus
  }
#endif

#endif

