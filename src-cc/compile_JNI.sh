#!/bin/sh

export CXXFLAGS="$CXXFLAGS -fPIC -DHAVE_ZLIB -lz"
(cd kenlm; ./compile_query_only.sh)

javah edu.stanford.nlp.mt.base.KenLanguageModel

g++ -I. -O3 -DNDEBUG -DHAVE_ZLIB -DKENLM_MAX_ORDER=6 -fPIC -lrt -I$JAVA_HOME/include -Ikenlm/ -I$JAVA_HOME/include/linux edu_stanford_nlp_mt_base_KenLanguageModel.cc kenlm/lm/*.o kenlm/util/*.o kenlm/util/double-conversion/*.o -shared -o libPhrasalKenLM.so -lz
