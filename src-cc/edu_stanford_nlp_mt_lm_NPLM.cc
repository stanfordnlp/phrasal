#include "lm/max_order.hh"
#include "lm/model.hh"
#include "lm/virtual_interface.hh"

#ifdef HAVE_NPLM
#include "lm/wrappers/nplm.hh"
#endif

#include <iostream>

#include <jni.h>
#include <math.h>
#include <stdint.h>

// Thang Luong Mar14: copy edu_stanford_nlp_mt_lm_KenLM.cc with support for NPLM
namespace {

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

class NPLMWrap {
  public:
    NPLMWrap(const char *name, const long cacheSize, const int miniBatchSize) : back_(name, cacheSize, miniBatchSize) {}
    explicit NPLMWrap(const char *name) : back_(name) {}

    lm::WordIndex Index(const char *word) const {
      return back_.GetVocabulary().Index(StringPiece(word));
    }

    double ScoreNgram(const int *ngramIds) const {
      return back_.ScoreNgram(ngramIds); // * M_LN10;
    }
    
    double* ScoreNgrams(const int *ngramIds, const int numNgrams) const {
      return back_.ScoreNgrams(ngramIds, numNgrams); // * M_LN10;
    } 
  
    unsigned char Order() const { return back_.Order(); }

    const lm::np::Model back_;
};

} // namespace

extern "C" {

/*
 * Class:     edu_stanford_nlp_more_lm_NPLM
 * Method:    readNPLM
 * Signature: (Ljava/lang/String;)Z
 */
JNIEXPORT jlong JNICALL Java_edu_stanford_nlp_mt_lm_NPLM_readNPLM
  (JNIEnv *env, jobject thisJObj, jstring jlm_filename, jlong cache_size, jint mini_batch_size) {
  try {
    JNIString filename(env, jlm_filename);
    if (lm::np::Model::Recognize(filename.get())) {
      return reinterpret_cast<jlong>(new NPLMWrap(filename.get(), cache_size, mini_batch_size));
    } else {
      UTIL_THROW(util::Exception, "Unrecognized NPLM file " << filename.get());
    }
  } catch (const std::exception &e) {
    std::cerr << e.what();
    return 0;
  }
}

/*
 * Class:     edu_stanford_nlp_more_lm_NPLM
 * Method:    scoreNgram
 * Signature: (L[I)L
 */
// Thang Mar14: note that jint_ngram contains word indices in normal order (not the inverse one as in scoreNgram)
JNIEXPORT jdouble JNICALL Java_edu_stanford_nlp_mt_lm_NPLM_scoreNgram
(JNIEnv *env, jobject this_jobj, jlong kenLM_ptr, jintArray jint_ngram) {
  // Convert the sequence input to a C array
  jint ngram_sz = env->GetArrayLength(jint_ngram);
  jint ngram_array[ngram_sz]; 
  env->GetIntArrayRegion(jint_ngram, 0, ngram_sz, ngram_array);

  return reinterpret_cast<NPLMWrap*>(kenLM_ptr)->ScoreNgram(&ngram_array[0]);
}

/*
 * Class:     edu_stanford_nlp_more_lm_NPLM
 * Method:    scoreNgrams
 * Signature: (L[I)L
 */
// Thang Apr14: score multiple ngrams
JNIEXPORT jdoubleArray JNICALL Java_edu_stanford_nlp_mt_lm_NPLM_scoreNgrams
(JNIEnv *env, jobject this_jobj, jlong kenLM_ptr, jintArray jint_ngram, jint numNgrams) {
  // Convert the sequence input to a C array
  jint ngram_sz = env->GetArrayLength(jint_ngram);
  jint ngram_array[ngram_sz]; 
  env->GetIntArrayRegion(jint_ngram, 0, ngram_sz, ngram_array);

  double* log_probs = reinterpret_cast<NPLMWrap*>(kenLM_ptr)->ScoreNgrams(&ngram_array[0], numNgrams);

  // convert to jdoubleArray
  jdoubleArray doubleArray = env->NewDoubleArray(numNgrams);
  env->SetDoubleArrayRegion(doubleArray, 0, numNgrams, log_probs); 
  return doubleArray;
}

/*
 * Class:     edu_stanford_nlp_more_lm_NPLM
 * Method:    getNPLMOrder
 * Signature: (L)I
 */
JNIEXPORT jint JNICALL Java_edu_stanford_nlp_mt_lm_NPLM_getNPLMOrder
  (JNIEnv *env, jobject thisJObj, jlong kenLM_ptr) {
  return reinterpret_cast<NPLMWrap*>(kenLM_ptr)->Order();
}

/*
 * Class:     edu_stanford_nlp_more_lm_NPLM
 * Method:    getNPLMId
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_edu_stanford_nlp_mt_lm_NPLM_getNPLMId
  (JNIEnv *env, jobject this_jobj, jlong kenLM_ptr, jstring jstr_token) {
  JNIString token(env, jstr_token);
  return reinterpret_cast<NPLMWrap*>(kenLM_ptr)->Index(token.get());
}
}
