#!/bin/bash

cd "$(dirname "$0")"

export CXXFLAGS="$CXXFLAGS -DNDEBUG -O3 -fPIC -DHAVE_ZLIB"
export LDFLAGS="$LDFLAGS -lz"
if [ ${#NPLM} != 0 ]; then
  extra_flags="kenlm/lm/wrappers/*.o -DHAVE_NPLM -lneuralLM -lboost_thread-mt -L$NPLM/src -I$NPLM/src -fopenmp"
fi

export CXX=${CXX:-g++}

(cd kenlm; ./compile_query_only.sh)

# Run this command only if JDK is installed
#javah edu.stanford.nlp.mt.lm.KenLM

if [ "$(uname)" == Darwin ]; then
  SUFFIX=.dylib
  RT=""
else
  RT=-lrt
  SUFFIX=.so
fi

$CXX -I. -DKENLM_MAX_ORDER=7 -I$JAVA_HOME/include -Ikenlm/ -I$JAVA_HOME/include/linux -I$JAVA_HOME/include/darwin edu_stanford_nlp_mt_lm_KenLM.cc kenlm/lm/*.o kenlm/util/*.o kenlm/util/double-conversion/*.o -shared -o libPhrasalKenLM$SUFFIX $CXXFLAGS $LDFLAGS $extra_flags $RT

# Thang Mar14: add libPhrasalNPLM
if [ ${#NPLM} != 0 ]; then
  $CXX -I. -DKENLM_MAX_ORDER=7 -I$JAVA_HOME/include -Ikenlm/ -I$JAVA_HOME/include/linux -I$JAVA_HOME/include/darwin edu_stanford_nlp_mt_lm_NPLM.cc kenlm/lm/*.o kenlm/util/*.o kenlm/util/double-conversion/*.o -shared -o libPhrasalNPLM$SUFFIX $CXXFLAGS $LDFLAGS $extra_flags $RT
fi



