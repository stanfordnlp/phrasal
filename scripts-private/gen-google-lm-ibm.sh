#!/bin/bash

N=/scr/gale/NIST_EVAL08/GoogleNgrams-ibm/1-3gms.gz
MBL=/u/nlp/packages/SRILM-1.5.11/bin/make-big-lm-50-fast

# Remove any temporary files:
\rm -rf biglm.gt*

# Create LM:
($MBL -kndiscount -interpolate -tolower -unk -read $N -debug 2 -order 3 -vocab all.e.vocab -limit-vocab -memuse -gt1min 1 -gt2min 1 -gt3min 1 -lm google.3gram.kn.lm.gz) >& google.3gram.kn.lm.gz.log > $0.log 2> $0.err
