#include "edu_stanford_nlp_mt_base_KenLanguageModel.h"
#include "lm/model.hh"
#include "lm/virtual_interface.hh"

#include <iostream>

class JNIString {
  public:
    JNIString(JNIEnv *env, jstring from) : env_(env), from_(from) {
      jboolean isCopy;
      local_ = env->GetStringUTFChars(from, &isCopy);
      UTIL_THROW_IF(!local_, util::Exception, "GetStringUTFChars JNI call failed.");
    }

    ~JNIString() {
      env_->ReleaseStringUTFChars(from_, local_);
    }

    const char *get() const { return local_; }

  private:
    JNIEnv *env_;
    jstring from_;
    const char *local_;
};

/*
 * Class:     edu_stanford_nlp_mt_base_KenLanguageModel
 * Method:    readKenLM
 * Signature: (Ljava/lang/String;)Z
 */
JNIEXPORT jlong JNICALL Java_edu_stanford_nlp_mt_base_KenLanguageModel_readKenLM
  (JNIEnv *env, jobject thisJObj, jstring jlm_filename) {
  try {
    JNIString filename(env, jlm_filename);
    lm::base::Model *kenLM;
    // Recognize with default probing for ARPA files.
    lm::ngram::ModelType model_type = lm::ngram::PROBING;
    RecognizeBinary(filename.get(), model_type);
    switch(model_type) {
      case lm::ngram::PROBING:
        kenLM = new lm::ngram::ProbingModel(filename.get());
        break;
      case lm::ngram::REST_PROBING:
        kenLM = new lm::ngram::RestProbingModel(filename.get());
        break;
      case lm::ngram::TRIE:
        kenLM = new lm::ngram::TrieModel(filename.get());
        break;
      case lm::ngram::QUANT_TRIE:
        kenLM = new lm::ngram::QuantTrieModel(filename.get());
        break;
      case lm::ngram::ARRAY_TRIE:
        kenLM = new lm::ngram::ArrayTrieModel(filename.get()); 
        break;
      case lm::ngram::QUANT_ARRAY_TRIE:
        kenLM = new lm::ngram::QuantArrayTrieModel(filename.get()); 
        break;
      default:
        UTIL_THROW(util::Exception, "Unrecognized model type " << model_type);
    }
    return reinterpret_cast<jlong>(kenLM);
  } catch (const std::exception &e) {
    std::cerr << e.what();
    return 0;
  }
}

/*
 * Class:     edu_stanford_nlp_mt_base_KenLanguageModel
 * Method:    getId
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_edu_stanford_nlp_mt_base_KenLanguageModel_getId
  (JNIEnv *env, jobject this_jobj, jlong kenLM_ptr, jstring jstr_token) {
  JNIString token(env, jstr_token);
  return reinterpret_cast<lm::base::Model*>(kenLM_ptr)->BaseVocabulary().Index(token.get());
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
  lm::base::Model* kenLM = reinterpret_cast<lm::base::Model*>(kenLM_ptr);
  jint ngram_sz = env->GetArrayLength(jint_ngram);
  jint ngram_array[ngram_sz]; 
  env->GetIntArrayRegion(jint_ngram, 0, ngram_sz, ngram_array);
  
  lm::ngram::State out_state;
  kenLM->FullScoreForgotState((lm::WordIndex*)&ngram_array[1], (lm::WordIndex*)&ngram_array[ngram_sz], (lm::WordIndex)ngram_array[0], &out_state).prob;
  relPrefix = (out_state.length == ngram_sz);
}

/*
 * Class:     edu_stanford_nlp_mt_base_KenLanguageModel
 * Method:    getOrder
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_edu_stanford_nlp_mt_base_KenLanguageModel_getOrder
  (JNIEnv *env, jobject thisJObj, jlong kenLM_ptr) {
  return reinterpret_cast<lm::base::Model*>(kenLM_ptr)->Order();
}


