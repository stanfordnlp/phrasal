#!/bin/bash

remove_unk | \
java edu.stanford.nlp.process.Americanize | \
#aren-postprocess-nw | \
aren-postprocess-text | \
cat
