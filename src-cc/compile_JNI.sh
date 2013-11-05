#!/bin/bash

export CXXFLAGS="$CXXFLAGS -O3 -fPIC -DHAVE_ZLIB -lz"

if [ ${#NPLM} != 0 ]; then
  extra_flags="kenlm/wrappers/*.o -DHAVE_NPLM -lneuralLM -L$NPLM/src -I$NPLM/src -fopenmp"
fi

(cd kenlm; ./compile_query_only.sh)

javah edu.stanford.nlp.mt.lm.KenLanguageModel

if [ "$(uname)" == Darwin ]; then
  SUFFIX=.dylib
else
  SUFFIX=.so
fi

g++ -I. -O3 -DNDEBUG -DHAVE_ZLIB -DKENLM_MAX_ORDER=6 -fPIC -lrt -I$JAVA_HOME/include -Ikenlm/ -I$JAVA_HOME/include/linux edu_stanford_nlp_mt_lm_KenLanguageModel.cc kenlm/lm/*.o kenlm/util/*.o kenlm/util/double-conversion/*.o -shared -o libPhrasalKenLM$SUFFIX -lz

