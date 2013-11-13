#include <math.h>
#include <jni.h>
#include "lm/max_order.hh"
#include "lm/model.hh"
#include "lm/virtual_interface.hh"

#ifdef HAVE_NPLM
#include "lm/wrappers/nplm.hh"
#endif

#include <iostream>

// Verify that jint and lm::ngram::WordIndex are the same size.
template<bool> struct StaticCheck {
};

template<> struct StaticCheck<true> {
  typedef bool StaticAssertionPassed;
};

typedef StaticCheck<sizeof(jint) == sizeof(lm::WordIndex)>::StaticAssertionPassed IntSizePassed;

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
      std::cerr << "NPLM " << filename.get() << std::endl; 
      return reinterpret_cast<jlong>(new lm::np::Model(filename.get()));
    }
#endif
    std::cerr << "Non-NPLM " << filename.get() << std::endl; 
     
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
 * Method:    getLMId
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_edu_stanford_nlp_mt_lm_KenLanguageModel_getLMId
  (JNIEnv *env, jobject this_jobj, jlong kenLM_ptr, jstring jstr_token) {
  JNIString token(env, jstr_token);
  return reinterpret_cast<lm::base::Model*>(kenLM_ptr)->BaseVocabulary().Index(token.get());
}

/*
 * Class:     edu_stanford_nlp_mt_lm_KenLanguageModel
 * Method:    scoreNGram
 * Signature: ([Ljava/lang/String;)D
 */
JNIEXPORT jlong JNICALL Java_edu_stanford_nlp_mt_lm_KenLanguageModel_scoreNGram
(JNIEnv *env, jobject this_jobj, jlong kenLM_ptr, jintArray jint_ngram) {
  // Convert the sequence input to a C array
  jint ngram_sz = env->GetArrayLength(jint_ngram);
  jint ngram_array[ngram_sz]; 
  env->GetIntArrayRegion(jint_ngram, 0, ngram_sz, ngram_array);

  // LM query
  union {
    lm::ngram::State gram;
#ifdef HAVE_NPLM
    lm::np::State neural;
#endif
  } out_state;
  lm::base::Model* kenLM = reinterpret_cast<lm::base::Model*>(kenLM_ptr);
  union {float f; unsigned int i; } convert;
  lm::FullScoreReturn got = kenLM->FullScoreForgotState((lm::WordIndex*)&ngram_array[1], (lm::WordIndex*)&ngram_array[ngram_sz], (lm::WordIndex)ngram_array[0], &out_state);
  convert.f = got.prob * M_LN10;
  // Return a jlong whose top bits are state length and bottom bits are floating-point probability.
  jlong ret = convert.i | (static_cast<jlong>(got.right_state_length) << 32);
  return ret;
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
