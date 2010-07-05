#!/bin/bash

find /scr/nlp/data/ldc/LDC2003E14_FBIS/CD1/000918_1428231/Chinese/  \
     /scr/nlp/data/ldc/LDC2003E14_FBIS/CD2/011119_0731/Chinese/ -name '*.txt' | \
	batch-filter-names java -Xmx3g edu.stanford.nlp.process.DocumentPreprocessor XXX -xml '"document_Body|document_EngSum"' \
	-suppressEscaping -noTokenization | mxterminator | \
	/home/mgalley/mt/tm/scripts/fix_bad_utf8_clean | \
	/home/mgalley/mt/tm/scripts/normalize_en
