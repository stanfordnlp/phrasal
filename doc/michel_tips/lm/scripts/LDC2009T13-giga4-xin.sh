#!/bin/bash

# Remove epoch of MT03, MT04, MT05, MT06, MT08, MT09, DEV07, respectively (nothing to remove for DEV08; DEV09 still unknown):

# [ Email from Yaser 2010-06-16 : I was going through the Arabic Gigaword 4th Edition Corpus and I discovered that the news wire portion of GALE-DEV09 and GALE-P4 test sets are INCLUDED in the Arabic Gigaword corpus. I suggest removing ALL documents from May and June of 2008 from that data before extracting comparable corpora to avoid contaminating the aforementioned test sets. ]

find -L /scr/nlp/data/ldc/LDC2009T13/DVD2/data/xin_eng/ -name '*.gz' | \
	grep -v -e 200301 -e 200302 | \
	grep -v -e 200401 | \
	grep -v -e 200412 -e 200501 | \
	grep -v -e 200602 | \
	grep -v -e 200707 | \
	grep -v -e 200706 | \
	grep -v -e 200611 | \
	batch-filter-names java -Xmx4g edu.stanford.nlp.process.DocumentPreprocessor XXX -xml '"P|HEADLINE|DATELINE"' \
	-suppressEscaping -noTokenization
