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

/* Using a custom virtual wrapper instead of virtual_interface mostly to support NPLM.*/
class WrapAbstract {
  public:
    virtual ~WrapAbstract() {}
    virtual lm::WordIndex Index(const char *word) const = 0;
    virtual jlong Query(const lm::WordIndex *context_begin, const lm::WordIndex *context_end, lm::WordIndex predict) const = 0;
    // This isn't called much so just be lazy and make it virtual.
    virtual unsigned char Order() const = 0;

  protected:
    WrapAbstract() {}
};

template <class Model> class Wrap : public WrapAbstract {
  public:
    explicit Wrap(const char *name) : back_(name) {}

    lm::WordIndex Index(const char *word) const {
      return back_.GetVocabulary().Index(StringPiece(word));
    }

    jlong Query(const lm::WordIndex *context_begin, const lm::WordIndex *context_end, lm::WordIndex predict) const {
      typename Model::State out_state;
      lm::FullScoreReturn got = back_.FullScoreForgotState(context_begin, context_end, predict, out_state);
      union {float f; uint32_t i; } convert;
      convert.f = got.prob * M_LN10;
      // Return a jlong whose top bits are state length and bottom bits are floating-point probability.
      return convert.i | (static_cast<jlong>(StateLength(out_state)) << 32);
    }

    unsigned char Order() const { return back_.Order(); }

  private:
    unsigned char StateLength(const lm::ngram::State &state) const {
      return state.Length();
    }

#ifdef HAVE_NPLM
    // State length depends on how many <null> and <s> words pad the beginning.
    unsigned char StateLength(const lm::np::State &state) const {
      if (state.words[0] == back_.GetVocabulary().NullWord()) {
        unsigned char position;
        for (position = 1; (position < Order() - 1) && state.words[position] == back_.GetVocabulary().NullWord(); ++position) {}
        // Position is now the first non-null entry.  Do not include any <null> in state.
        return Order() - 1 - position;
      } else if (state.words[0] == back_.GetVocabulary().BeginSentence()) {
        unsigned char position;
        for (position = 1; (position < Order() - 1) && state.words[position] == back_.GetVocabulary().BeginSentence(); ++position) {}
        // Include the last <s> in state.
        return Order() - position;
      } else {
        return Order() - 1;
      }
    }
#endif

    const Model back_;
};

} // namespace

extern "C" {

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
      return reinterpret_cast<jlong>(new Wrap<lm::np::Model>(filename.get()));
    }
#endif
    std::cerr << "Non-NPLM " << filename.get() << std::endl; 
     
    WrapAbstract *kenLM;
    // Recognize with default probing for ARPA files.
    lm::ngram::ModelType model_type = lm::ngram::PROBING;
    RecognizeBinary(filename.get(), model_type);
    switch(model_type) {
      case lm::ngram::PROBING:
        kenLM = new Wrap<lm::ngram::ProbingModel>(filename.get());
        break;
      case lm::ngram::REST_PROBING:
        kenLM = new Wrap<lm::ngram::RestProbingModel>(filename.get());
        break;
      case lm::ngram::TRIE:
        kenLM = new Wrap<lm::ngram::TrieModel>(filename.get());
        break;
      case lm::ngram::QUANT_TRIE:
        kenLM = new Wrap<lm::ngram::QuantTrieModel>(filename.get());
        break;
      case lm::ngram::ARRAY_TRIE:
        kenLM = new Wrap<lm::ngram::ArrayTrieModel>(filename.get()); 
        break;
      case lm::ngram::QUANT_ARRAY_TRIE:
        kenLM = new Wrap<lm::ngram::QuantArrayTrieModel>(filename.get()); 
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
  return reinterpret_cast<WrapAbstract*>(kenLM_ptr)->Index(token.get());
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

  return reinterpret_cast<WrapAbstract*>(kenLM_ptr)->Query((lm::WordIndex*)&ngram_array[1], (lm::WordIndex*)&ngram_array[ngram_sz], (lm::WordIndex)ngram_array[0]);
}

/*
 * Class:     edu_stanford_nlp_mt_lm_KenLanguageModel
 * Method:    getOrder
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_edu_stanford_nlp_mt_lm_KenLanguageModel_getOrder
  (JNIEnv *env, jobject thisJObj, jlong kenLM_ptr) {
  return reinterpret_cast<WrapAbstract*>(kenLM_ptr)->Order();
}

}
