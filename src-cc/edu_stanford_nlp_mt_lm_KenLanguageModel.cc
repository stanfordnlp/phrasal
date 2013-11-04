#include <math.h>
#include "edu_stanford_nlp_mt_lm_KenLanguageModel.h"
#include "lm/model.hh"
#include "lm/virtual_interface.hh"

#define MAX_MODELS 128

// Verify that jint and lm::ngram::WordIndex are the same size.
template<bool> struct StaticCheck {
};

template<> struct StaticCheck<true> {
  typedef bool StaticAssertionPassed;
};

typedef StaticCheck<sizeof(jint) == sizeof(lm::WordIndex)>::StaticAssertionPassed FloatSize;

lm::base::Model *models[MAX_MODELS];
lm::ngram::ModelType model_types[MAX_MODELS];

int last_model_id = -1;


/*
 * Class:     edu_stanford_nlp_mt_lm_KenLanguageModel
 * Method:    readKenLM
 * Signature: (Ljava/lang/String;)Z
 */
JNIEXPORT jlong JNICALL Java_edu_stanford_nlp_mt_lm_KenLanguageModel_readKenLM
  (JNIEnv *env, jobject thisJObj, jstring jlm_filename) {
  lm::ngram::ModelType model_type;
  jboolean isCopy;

  const char* lm_filename = env->GetStringUTFChars(jlm_filename, &isCopy);

  lm::base::Model *kenLM = NULL;

  if (lm_filename == NULL) {
     return 0;
  }

  if (RecognizeBinary(lm_filename, model_type)) {
    switch(model_type) {
      case lm::ngram::PROBING:
        kenLM = new lm::ngram::ProbingModel(lm_filename);
        break;
      case lm::ngram::REST_PROBING:
        kenLM = new lm::ngram::RestProbingModel(lm_filename);
        break;
      case lm::ngram::TRIE:
        kenLM = new lm::ngram::TrieModel(lm_filename);
        break;
      case lm::ngram::QUANT_TRIE:
        kenLM = new lm::ngram::QuantTrieModel(lm_filename);
        break;
      case lm::ngram::ARRAY_TRIE:
        kenLM = new lm::ngram::ArrayTrieModel(lm_filename); 
        break;
      case lm::ngram::QUANT_ARRAY_TRIE:
        kenLM = new lm::ngram::QuantArrayTrieModel(lm_filename); 
        break;
    
    }
  } else {
    model_type = lm::ngram::PROBING;
    kenLM = new lm::ngram::Model(lm_filename);
  }

  env->ReleaseStringUTFChars(jlm_filename, lm_filename);
  last_model_id++;
  models[last_model_id] = kenLM;
  model_types[last_model_id] = model_type;
  return (jlong)last_model_id;
}

/*
 * Class:     edu_stanford_nlp_mt_lm_KenLanguageModel
 * Method:    getId
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_edu_stanford_nlp_mt_lm_KenLanguageModel_getId
  (JNIEnv *env, jobject this_jobj, jlong kenLM_ptr, jstring jstr_token) {
  lm::base::Model* kenLM = models[kenLM_ptr];
  const char* token = env->GetStringUTFChars(jstr_token, NULL);
  int id = kenLM->BaseVocabulary().Index(token);
  env->ReleaseStringUTFChars(jstr_token, token);
  return id;
}

void scoreNgram
(JNIEnv *env, jobject this_jobj, jlong kenLM_ptr, int ngram_array[], int ngram_sz, double &score, lm::ngram::State &out_state) {
  lm::base::Model* kenLM = models[kenLM_ptr];
  lm::ngram::ModelType model_type = model_types[kenLM_ptr];
  //  jint ngram_sz = env->GetArrayLength(jint_ngram);
  //jint ngram_array[ngram_sz]; 
  //env->GetIntArrayRegion(jint_ngram, 0, ngram_sz, ngram_array);
  
  switch(model_type) {
    case lm::ngram::PROBING:
        score = ((lm::ngram::ProbingModel*)kenLM)->FullScoreForgotState((lm::WordIndex*)&ngram_array[1], (lm::WordIndex*)&ngram_array[ngram_sz], (lm::WordIndex)ngram_array[0], out_state).prob;
        break;
    case lm::ngram::REST_PROBING:
        score = ((lm::ngram::RestProbingModel*)kenLM)->FullScoreForgotState((lm::WordIndex*)&ngram_array[1], (lm::WordIndex*)&ngram_array[ngram_sz], (lm::WordIndex)ngram_array[0], out_state).prob;
        break;
    case lm::ngram::TRIE:
        score = ((lm::ngram::TrieModel*)kenLM)->FullScoreForgotState((lm::WordIndex*)&ngram_array[1], (lm::WordIndex*)&ngram_array[ngram_sz], (lm::WordIndex)ngram_array[0], out_state).prob;
        break;
    case lm::ngram::QUANT_TRIE:
        score = ((lm::ngram::QuantTrieModel*)kenLM)->FullScoreForgotState((lm::WordIndex*)&ngram_array[1], (lm::WordIndex*)&ngram_array[ngram_sz], (lm::WordIndex)ngram_array[0], out_state).prob;
        break;
    case lm::ngram::ARRAY_TRIE:
        score = ((lm::ngram::ArrayTrieModel*)kenLM)->FullScoreForgotState((lm::WordIndex*)&ngram_array[1], (lm::WordIndex*)&ngram_array[ngram_sz], (lm::WordIndex)ngram_array[0], out_state).prob;
        break;
    case lm::ngram::QUANT_ARRAY_TRIE:
        score = ((lm::ngram::QuantArrayTrieModel*)kenLM)->FullScoreForgotState((lm::WordIndex*)&ngram_array[1], (lm::WordIndex*)&ngram_array[ngram_sz], (lm::WordIndex)ngram_array[0], out_state).prob;
        break;
  }
}

/*
 * Class:     edu_stanford_nlp_mt_lm_KenLanguageModel
 * Method:    scoreNGram
 * Signature: ([Ljava/lang/String;)D
 */
JNIEXPORT jobject JNICALL Java_edu_stanford_nlp_mt_lm_KenLanguageModel_scoreNGram
  (JNIEnv *env, jobject this_jobj, jlong kenLM_ptr, jobjectArray stringArray) {
  lm::base::Model* kenLM = models[kenLM_ptr];

  // KenLM stores n-grams in reverse order relative to Phrasal
  const int string_count = env->GetArrayLength(stringArray);
  int int_ngram [string_count];
  for (int i=0; i < string_count; ++i) {
    jstring string = (jstring) env->GetObjectArrayElement(stringArray, i);
    const char *rawString = env->GetStringUTFChars(string, 0);
    int id = kenLM->BaseVocabulary().Index(rawString);
    int_ngram[string_count-i-1] = id;
    env->ReleaseStringUTFChars(string, rawString);
  }

  // LM query
  double score;
  lm::ngram::State out_state;
  scoreNgram(env, this_jobj, kenLM_ptr, int_ngram, string_count, score, out_state);

  // Phrasal expects natural log.
  score *= log(10.0);
  
  // TODO(spenceg): These could be cached for fast lookup
  jclass cls = env->FindClass("edu/stanford/nlp/mt/lm/KenLMState");
  jmethodID constructor = env->GetMethodID(cls, "<init>", "(D[I)V");

  // Create the state object to return
  int state_sz = (sizeof(out_state.words)/sizeof(*(out_state.words)));
  jintArray state = env->NewIntArray(state_sz);
  env->SetIntArrayRegion(state, 0, state_sz, (jint*) out_state.words);
  jobject object = env->NewObject(cls, constructor, score, state);
  return object;
}

/*
 * Class:     edu_stanford_nlp_mt_lm_KenLanguageModel
 * Method:    getOrder
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_edu_stanford_nlp_mt_lm_KenLanguageModel_getOrder
  (JNIEnv *env, jobject thisJObj, jlong kenLM_ptr) {
  lm::base::Model* kenLM = models[kenLM_ptr];
  return kenLM->Order();
}


