#!/bin/bash

# Remove epoch of MT03, MT04, MT05, MT06, MT08, MT09, DEV07, respectively (nothing to remove for DEV08; DEV09 still unknown):

find -L /scr/nlp/data/ldc/LDC2009T13/DVD?/data/cna_eng/ -name '*.gz' | \
	grep -v -e 200301 -e 200302 | \
	grep -v -e 200401 | \
	grep -v -e 200412 -e 200501 | \
	grep -v -e 200602 | \
	grep -v -e 200707 | \
	grep -v -e 200706 | \
	grep -v -e 200611 | \
	batch-filter-names java -Xmx2g edu.stanford.nlp.process.DocumentPreprocessor XXX -xml '"P|HEADLINE|DATELINE"' \
	-suppressEscaping -noTokenization
