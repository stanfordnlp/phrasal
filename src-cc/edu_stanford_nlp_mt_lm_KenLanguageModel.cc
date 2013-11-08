#include <math.h>
#include "edu_stanford_nlp_mt_lm_KenLanguageModel.h"
#include "lm/max_order.hh"
#include "lm/model.hh"
#include "lm/virtual_interface.hh"

#include <iostream>

// Verify that jint and lm::ngram::WordIndex are the same size.
template<bool> struct StaticCheck {
};

template<> struct StaticCheck<true> {
  typedef bool StaticAssertionPassed;
};

typedef StaticCheck<sizeof(jint) == sizeof(lm::WordIndex)>::StaticAssertionPassed FloatSize;

const double LOG_10 = log(10);

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
 * Class:     edu_stanford_nlp_mt_lm_KenLanguageModel
 * Method:    readKenLM
 * Signature: (Ljava/lang/String;)Z
 */
JNIEXPORT jlong JNICALL Java_edu_stanford_nlp_mt_lm_KenLanguageModel_readKenLM
  (JNIEnv *env, jobject thisJObj, jstring jlm_filename) {
  try {
    JNIString filename(env, jlm_filename);
#ifdef HAVE_NPLM
    if (lm::np::Model::Recognize(filename.get())) {
      return reinterpret_cast<jlong>(new lm::np::Model(filename.get()));
    }
#endif
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
 * Class:     edu_stanford_nlp_mt_lm_KenLanguageModel
 * Method:    getId
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_edu_stanford_nlp_mt_lm_KenLanguageModel_getId
  (JNIEnv *env, jobject this_jobj, jlong kenLM_ptr, jstring jstr_token) {
  JNIString token(env, jstr_token);
  return reinterpret_cast<lm::base::Model*>(kenLM_ptr)->BaseVocabulary().Index(token.get());
}

/*
 * Class:     edu_stanford_nlp_mt_lm_KenLanguageModel
 * Method:    scoreNGram
 * Signature: ([Ljava/lang/String;)D
 */
JNIEXPORT jdouble JNICALL Java_edu_stanford_nlp_mt_lm_KenLanguageModel_scoreNGram
(JNIEnv *env, jobject this_jobj, jlong kenLM_ptr, jintArray jint_ngram, jobject buf) {
  // Convert the sequence input to a C array
  jint ngram_sz = env->GetArrayLength(jint_ngram);
  jint ngram_array[ngram_sz]; 
  env->GetIntArrayRegion(jint_ngram, 0, ngram_sz, ngram_array);

  // LM query
  lm::ngram::State out_state;
  lm::base::Model* kenLM = reinterpret_cast<lm::base::Model*>(kenLM_ptr);
  double score = kenLM->FullScoreForgotState((lm::WordIndex*)&ngram_array[1], (lm::WordIndex*)&ngram_array[ngram_sz], (lm::WordIndex)ngram_array[0], &out_state).prob;

  // Extract and copy the state
  jbyte *cBuf = (jbyte*) env->GetDirectBufferAddress(buf);
  out_state.ZeroRemaining();
  memcpy(cBuf, out_state.words, (KENLM_MAX_ORDER-1) * sizeof(int));

  // Phrasal expects natural log. Change base.
  return score*LOG_10;
}

/*
 * Class:     edu_stanford_nlp_mt_lm_KenLanguageModel
 * Method:    getOrder
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_edu_stanford_nlp_mt_lm_KenLanguageModel_getOrder
  (JNIEnv *env, jobject thisJObj, jlong kenLM_ptr) {
  return reinterpret_cast<lm::base::Model*>(kenLM_ptr)->Order();
}

/*
 * Class:     edu_stanford_nlp_mt_lm_KenLanguageModel
 * Method:    getMaxOrder
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_edu_stanford_nlp_mt_lm_KenLanguageModel_getMaxOrder
  (JNIEnv *env, jobject thisJObj, jlong kenLM_ptr) {
  return KENLM_MAX_ORDER;
}
