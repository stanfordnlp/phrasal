#!/bin/bash

export CXXFLAGS="$CXXFLAGS -O3 -fPIC -DHAVE_ZLIB -lz"

if [ ${#NPLM} != 0 ]; then
  extra_flags="kenlm/lm/wrappers/*.o -DHAVE_NPLM -lneuralLM -lboost_thread-mt -L$NPLM/src -I$NPLM/src -fopenmp"
fi

(cd kenlm; ./compile_query_only.sh)

javah edu.stanford.nlp.mt.lm.KenLanguageModel

if [ "$(uname)" == Darwin ]; then
  SUFFIX=.dylib
  RT=""
else
  RT=-lrt
  SUFFIX=.so
fi

g++ -I. -O3 -DNDEBUG -DHAVE_ZLIB -DKENLM_MAX_ORDER=6 -fPIC $RT -I$JAVA_HOME/include -Ikenlm/ -I$JAVA_HOME/include/linux edu_stanford_nlp_mt_lm_KenLanguageModel.cc kenlm/lm/*.o kenlm/util/*.o kenlm/util/double-conversion/*.o -shared -o libPhrasalKenLM$SUFFIX -lz $extra_flags
