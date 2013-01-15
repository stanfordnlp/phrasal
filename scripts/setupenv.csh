#!/bin/csh

if ( "$PHRASAL" == "" ) then
   echo "ERROR: Env variable PHRASAL is not set!"
   exit -1;
endif

if ( "$CORENLP" == "" ) then
   echo "ERROR: Env variable CORENLP is not set!"
   exit -1;
endif

foreach jf ($PHRASAL/*.jar $PHRASAL/lib/*.jar $CORENLP/*.jar)
  setenv CLASSPATH "${jf}:${CLASSPATH}"
  #echo  "${jf}:${CLASSPATH}"
end


