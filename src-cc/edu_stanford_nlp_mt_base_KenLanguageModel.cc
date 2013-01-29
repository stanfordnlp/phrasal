#include "edu_stanford_nlp_mt_base_KenLanguageModel.h"
#include "lm/model.hh"
#include "lm/virtual_interface.hh"

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
    kenLM = new lm::ngram::Model(lm_filename);
  }

  env->ReleaseStringUTFChars(jlm_filename, lm_filename);
  jlong kenLM_ptr = (jlong)kenLM;
  return kenLM_ptr;
}

void scoreNgram
(JNIEnv *env, jobject this_jobj, jlong kenLM_ptr, jobjectArray jstr_ngram, jboolean first_is_start_token, jboolean last_is_end_token, double &score, bool &relPrefix);

/*
 * Class:     edu_stanford_nlp_mt_base_KenLanguageModel
 * Method:    scoreNGram
 * Signature: ([Ljava/lang/String;)D
 */
JNIEXPORT jdouble JNICALL Java_edu_stanford_nlp_mt_base_KenLanguageModel_scoreNGram
  (JNIEnv *env, jobject this_jobj, jlong kenLM_ptr, jobjectArray jstr_ngram, jboolean first_is_start_token, jboolean last_is_end_token) {

  lm::base::Model* kenLM = (lm::base::Model*)(kenLM_ptr);

  double score;
  bool relPrefix;

  scoreNgram(env, this_jobj, kenLM_ptr, jstr_ngram, first_is_start_token, last_is_end_token, score, relPrefix);

  return score;
}

/*
 * Class:     edu_stanford_nlp_mt_base_KenLanguageModel
 * Method:    relevantPrefixGram
 * Signature: (J[Ljava/lang/String;ZZ)Z
 */
JNIEXPORT jboolean JNICALL Java_edu_stanford_nlp_mt_base_KenLanguageModel_relevantPrefixGram
(JNIEnv *env, jobject this_jobj, jlong kenLM_ptr, jobjectArray jstr_ngram, jboolean first_is_start_token, jboolean last_is_end_token) {
  double score;
  bool relPrefix;
  scoreNgram(env, this_jobj, kenLM_ptr, jstr_ngram, first_is_start_token, last_is_end_token, score, relPrefix);
  return relPrefix; 
}

void scoreNgram
(JNIEnv *env, jobject this_jobj, jlong kenLM_ptr, jobjectArray jstr_ngram, jboolean first_is_start_token, jboolean last_is_end_token, double &score, bool &relPrefix) {
  lm::base::Model* kenLM = (lm::base::Model*)(kenLM_ptr);

  relPrefix = 1;

  size_t model_state_sz = kenLM->StateSize();

  jint ngram_size = env->GetArrayLength(jstr_ngram);
  void *state = (void*)kenLM->NullContextMemory();
  void *out_state = malloc(model_state_sz);

  for (int i = 0; i < ngram_size-1; i++) {
     if (i == 0 && first_is_start_token) {
       kenLM->Score(state, kenLM->BaseVocabulary().BeginSentence(),
         out_state);
     } else {
       jstring jstr_token = (jstring) env->GetObjectArrayElement(jstr_ngram, i);
       const char* token = env->GetStringUTFChars(jstr_token, NULL);
       kenLM->Score(state, kenLM->BaseVocabulary().Index(token), out_state);
       env->ReleaseStringUTFChars(jstr_token, token);
     }

     if (i != 0) {
       free((void*)state);
     }
     state = out_state;
     out_state = malloc(model_state_sz);
  }


  if (last_is_end_token == JNI_TRUE) {
     score = kenLM->Score(state, kenLM->BaseVocabulary().EndSentence(),
       out_state);
  } else {
     jstring jstr_token  = (jstring)env->GetObjectArrayElement(jstr_ngram,
       ngram_size-1);
     char* token = (char *)env->GetStringUTFChars(jstr_token, NULL);
     score = kenLM->Score(state, kenLM->BaseVocabulary().Index(token),
       out_state);
     env->ReleaseStringUTFChars(jstr_token, token);
  }

  relPrefix = ((lm::ngram::State*)out_state)->length == ngram_size;

  if (ngram_size > 1) {
    free(state);
  }
  free(out_state);
}

/*
 * Class:     edu_stanford_nlp_mt_base_KenLanguageModel
 * Method:    getOrder
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_edu_stanford_nlp_mt_base_KenLanguageModel_getOrder
  (JNIEnv *env, jobject thisJObj, jlong kenLM_ptr) {

  lm::base::Model* kenLM = (lm::base::Model*)(kenLM_ptr);
  return kenLM->Order();
}


