#include "edu_stanford_nlp_mt_base_KenLanguageModel.h"
#include "lm/model.hh"
#include "lm/virtual_interface.hh"

#define MAX_MODELS 128

lm::base::Model *models[MAX_MODELS];
lm::ngram::ModelType model_types[MAX_MODELS];

int last_model_id = -1;


/*
 * Class:     edu_stanford_nlp_mt_base_KenLanguageModel
 * Method:    readKenLM
 * Signature: (Ljava/lang/String;)Z
 */
JNIEXPORT jlong JNICALL Java_edu_stanford_nlp_mt_base_KenLanguageModel_readKenLM
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
 * Class:     edu_stanford_nlp_mt_base_KenLanguageModel
 * Method:    getId
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_edu_stanford_nlp_mt_base_KenLanguageModel_getId
  (JNIEnv *env, jobject this_jobj, jlong kenLM_ptr, jstring jstr_token) {
  lm::base::Model* kenLM = models[kenLM_ptr];
  const char* token = env->GetStringUTFChars(jstr_token, NULL);
  int id = kenLM->BaseVocabulary().Index(token);
  env->ReleaseStringUTFChars(jstr_token, token);
  return id;
}

void scoreNgram
(JNIEnv *env, jobject this_jobj, jlong kenLM_ptr, jintArray jint_ngram, double &score, bool &relPrefix);

/*
 * Class:     edu_stanford_nlp_mt_base_KenLanguageModel
 * Method:    scoreNGram
 * Signature: ([Ljava/lang/String;)D
 */
JNIEXPORT jdouble JNICALL Java_edu_stanford_nlp_mt_base_KenLanguageModel_scoreNGram
  (JNIEnv *env, jobject this_jobj, jlong kenLM_ptr, jintArray jint_ngram) {


  double score;
  bool relPrefix;

  scoreNgram(env, this_jobj, kenLM_ptr, jint_ngram, score, relPrefix);

  return score;
}

/*
 * Class:     edu_stanford_nlp_mt_base_KenLanguageModel
 * Method:    relevantPrefixGram
 * Signature: (J[Ljava/lang/String;ZZ)Z
 */
JNIEXPORT jboolean JNICALL Java_edu_stanford_nlp_mt_base_KenLanguageModel_relevantPrefixGram
(JNIEnv *env, jobject this_jobj, jlong kenLM_ptr, jintArray jint_ngram) {
  double score;
  bool relPrefix;
  scoreNgram(env, this_jobj, kenLM_ptr, jint_ngram, score, relPrefix);
  return relPrefix; 
}

void scoreNgram
(JNIEnv *env, jobject this_jobj, jlong kenLM_ptr, jintArray jint_ngram, double &score, bool &relPrefix) {
  lm::base::Model* kenLM = models[kenLM_ptr];
  lm::ngram::ModelType model_type = model_types[kenLM_ptr];
  jint ngram_sz = env->GetArrayLength(jint_ngram);
  jint ngram_array[ngram_sz]; 
  env->GetIntArrayRegion(jint_ngram, 0, ngram_sz, ngram_array);
  
  /* for (int i = 0 ; i < ngram_sz; i++) {
    std::cerr<<"\t c++ ["<<i<<"] "<< ngram_array[i]<<"\n";
  } */
  lm::ngram::State out_state;
  switch(model_type) {
    case lm::ngram::PROBING:
        score = ((lm::ngram::ProbingModel*)kenLM)->FullScoreForgotState((lm::WordIndex*)&ngram_array[1], (lm::WordIndex*)&ngram_array[ngram_sz], (lm::WordIndex)ngram_array[0], out_state).prob;
        relPrefix = out_state.length == ngram_sz;
        break;
    case lm::ngram::REST_PROBING:
        score = ((lm::ngram::RestProbingModel*)kenLM)->FullScoreForgotState((lm::WordIndex*)&ngram_array[1], (lm::WordIndex*)&ngram_array[ngram_sz], (lm::WordIndex)ngram_array[0], out_state).prob;
        relPrefix = out_state.length == ngram_sz;
        break;
    case lm::ngram::TRIE:
        score = ((lm::ngram::TrieModel*)kenLM)->FullScoreForgotState((lm::WordIndex*)&ngram_array[1], (lm::WordIndex*)&ngram_array[ngram_sz], (lm::WordIndex)ngram_array[0], out_state).prob;
        relPrefix = out_state.length == ngram_sz;
        break;
    case lm::ngram::QUANT_TRIE:
        score = ((lm::ngram::QuantTrieModel*)kenLM)->FullScoreForgotState((lm::WordIndex*)&ngram_array[1], (lm::WordIndex*)&ngram_array[ngram_sz], (lm::WordIndex)ngram_array[0], out_state).prob;
        relPrefix = out_state.length == ngram_sz;
        break;
    case lm::ngram::ARRAY_TRIE:
        score = ((lm::ngram::ArrayTrieModel*)kenLM)->FullScoreForgotState((lm::WordIndex*)&ngram_array[1], (lm::WordIndex*)&ngram_array[ngram_sz], (lm::WordIndex)ngram_array[0], out_state).prob;
        relPrefix = out_state.length == ngram_sz;
        break;
    case lm::ngram::QUANT_ARRAY_TRIE:
        score = ((lm::ngram::QuantArrayTrieModel*)kenLM)->FullScoreForgotState((lm::WordIndex*)&ngram_array[1], (lm::WordIndex*)&ngram_array[ngram_sz], (lm::WordIndex)ngram_array[0], out_state).prob;
        relPrefix = out_state.length == ngram_sz;
        break;
  }
}

/*
 * Class:     edu_stanford_nlp_mt_base_KenLanguageModel
 * Method:    getOrder
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_edu_stanford_nlp_mt_base_KenLanguageModel_getOrder
  (JNIEnv *env, jobject thisJObj, jlong kenLM_ptr) {
  lm::base::Model* kenLM = models[kenLM_ptr];
  return kenLM->Order();
}


