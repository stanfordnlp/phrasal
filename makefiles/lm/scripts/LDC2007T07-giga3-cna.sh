#!/bin/bash

find -L /scr/nlp/data/ldc/LDC2007T07_English_Gigaword/cna_eng/ -name '*.gz' | \
	grep -v -e 200202 -e 200203 -e 200204 -e 200205 -e 200212 -e 200301 -e 200302 -e 200303 -e 200411 -e 200412 -e 200501 -e 200502 -e 200601 -e 200602 -e 200603 | \
	#grep -v -e 200411 -e 200412 -e 200501 -e 200502 -e 200601 -e 200602 -e 200603 | \
	batch-filter-names java -Xmx2g edu.stanford.nlp.process.DocumentPreprocessor XXX -xml '"P|HEADLINE|DATELINE"' \
	-suppressEscaping -noTokenization
