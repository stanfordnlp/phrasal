#!/bin/bash

remove_unk | \
java edu.stanford.nlp.process.Americanize | \
aren-postprocess-wb | \
#aren-postprocess-text | \
cat
