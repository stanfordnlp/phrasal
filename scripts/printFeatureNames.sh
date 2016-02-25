#!/usr/bin/env bash
#

java edu.stanford.nlp.mt.tools.PrintWeights $* | awk -F':' '{for(i=1;i<NF-1;i++){printf($i); printf(":")} print $(NF-1)}'
