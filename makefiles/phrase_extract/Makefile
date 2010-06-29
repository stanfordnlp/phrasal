.SECONDARY:

.PHONY: test

###########################################
# Makefile for generating phrase tables
# and LMs filtered against a dev/test set.
###########################################

# Make sure JAVANLP_HOME is defined:
JNLP=$(JAVANLP_HOME)
SCRIPTS=$(JNLP)/projects/mt/scripts
ESCRIPTS=$(JNLP)/projects/mt/external_scripts

# Moses Paths
# See below for configuration settings
MOSESDIR=/u/nlp/packages/moses-2008-05-18
MOSESDEC=$(MOSESDIR)/bin/moses
SCRIPTS_ROOTDIR=$(MOSESDIR)/trunk/scripts
MM=$(SCRIPTS_ROOTDIR)/training/mert-moses.pl

# Evaluation scripts:
METEOR=/u/nlp/packages/meteor-0.7
JTER=tercom_mult_java.pl

# TER default parameters (too slow for MERT):
#BEAM_WIDTH=20
#SHIFT_SIZE=50
# TER parameters (runs about 4x faster than default, but still slow -- 
# e.g., 1 iteration of MERT takes 24h on MT06):
BEAM_WIDTH=5
SHIFT_SIZE=12

OPT_METRIC=bleu

# Corpus/genre/language specific stuff here:
include Makefile.local

LTABLES_DEV= \
	$(DEVID).tables/lo-word.msd-bidirectional-fe.gz \
	$(DEVID).tables/lo-phrase.msd-bidirectional-fe.gz \
	$(DEVID).tables/lo-phrase.msd2-bidirectional-fe.gz \
	$(DEVID).tables/lo-hier.msd-bidirectional-fe.gz \
	$(DEVID).tables/lo-hier.msd2-bidirectional-fe.gz

LTABLES_TEST= \
	$(TESTID).tables/lo-word.msd-bidirectional-fe.gz \
	$(TESTID).tables/lo-phrase.msd-bidirectional-fe.gz \
	$(TESTID).tables/lo-phrase.msd2-bidirectional-fe.gz \
	$(TESTID).tables/lo-hier.msd-bidirectional-fe.gz \
	$(TESTID).tables/lo-hier.msd2-bidirectional-fe.gz

D=logs/.create $(DEVID).tables/.create $(TESTID).tables/.create

# Main targets:
all:
	echo "No default target; please refer to http://nlp.stanford.edu/nlpwiki/MTPipelineJavaNLP for a list of valid targets."

$D:
	mkdir $(DEVID).tables $(TESTID).tables logs 2> /dev/null || true

# Build LMs + phrase tables (test):
test_extract: $(TESTID).tables/phrase-table.gz
test_extract_google: $(TESTID).tables/phrase-table.gz
test_extract_gale: $(TESTID).tables/phrase-table.gz

# Build LMs + phrase tables (dev):
dev_extract: $(DEVID).tables/phrase-table.gz
dev_extract_google: $(DEVID).tables/phrase-table.gz
dev_extract_gale: $(DEVID).tables/phrase-table.gz

# Run MERT:
train: dev_extract $(DEVID).$(SYSID).$N.pmert/phrasal.final.ini
train_google: dev_extract_google $(DEVID).$(SYSID).$N.pmert/phrasal.final.ini
train_gale: dev_extract_gale $(DEVID).$(SYSID).$N.pmert/phrasal.final.ini

# Decode test data (with nbest list):
test: test_extract $(TESTID).$(DEVID).$(SYSID).$N.f1best.post
test_google: test_extract_google $(TESTID).$(DEVID).$(SYSID).$N.b1best.post
test_gale: test_extract_gale $(TESTID).$(DEVID).$(SYSID).$N.b1best.post

# Decode test data (without nbest list):
nbest: test_extract $(TESTID).$(DEVID).$(SYSID).$N.$Nbest
nbest_google: test_extract_google $(TESTID).$(DEVID).$(SYSID).$N.$Nbest
nbest_gale: test_extract_gale $(TESTID).$(DEVID).$(SYSID).$N.$Nbest

ltables: $(LTABLES_DEV) $(LTABLES_TEST)

# Java configuration:
JAVA_MERT_ARGS=$(DEBUG_PHRASAL) -Xmx$(MMEMSIZE) -Xms$(MMEMSIZE)
JAVA_PHRASAL_ARGS=$(DEBUG_PHRASAL) -Xmx$(PMEMSIZE) -Xms$(PMEMSIZE)
JAVA_PHRASAL_ARGS_N=$(DEBUG_PHRASAL) -Xmx$(PMEMSIZE_BM) -Xms$(PMEMSIZE_BM) 

# Moses Configuration
MOSES_ARGS=$(LOCAL_MOSES_ARGS)

#############################################
# Evaluation:
#############################################

### BLEU:
$(DEVID).%.bleu: $(DEVID).%
	cat $< | phrasal_sort | remove_unk | $(JAVA) edu.stanford.nlp.mt.metrics.BLEUMetric $(DEV_REF)/ref{0,1,2,3} > $@
$(TESTID).%.bleu: $(TESTID).%
	cat $< | phrasal_sort | remove_unk | $(JAVA) edu.stanford.nlp.mt.metrics.BLEUMetric $(TEST_REF)/ref{0,1,2,3} > $@

### TER:
$(DEVID).%.ter: $(DEVID).%
	cat $< | $(JAVA) edu.stanford.nlp.mt.metrics.TERMetric $(DEV_REF)/ref{0,1,2,3} > $@
$(TESTID).%.ter: $(TESTID).%
	cat $< | $(JAVA) edu.stanford.nlp.mt.metrics.TERMetric $(TEST_REF)/ref{0,1,2,3} > $@

### TER-BLEU:
$(DEVID).%.tb: $(DEVID).%
	cat $< | $(JAVA) -Xmx2g -Xms2g edu.stanford.nlp.mt.metrics.LinearCombinationMetric $(DEV_REF)/ref{0,1,2,3} > $@
$(TESTID).%.tb: $(TESTID).%
	cat $< | $(JAVA) -Xmx2g -Xms2g edu.stanford.nlp.mt.metrics.LinearCombinationMetric $(TEST_REF)/ref{0,1,2,3} > $@

### Meteor:
$(TESTID).%.meteor: $(TESTID).%
	/scr/nlp/data/gale/MT-metric-correlation/scripts/txt2sgml --type hyp < $< > $<.hyp.sgm
	/scr/nlp/data/gale/MT-metric-correlation/scripts/txt2sgml --type ref < $(TEST_REF)/ref0 > $<.ref.sgm
	perl $(METEOR)/meteor.pl -s any -t $<.hyp.sgm -r $<.ref.sgm --modules "exact porter_stem wn_synonymy" >& $@
	rm $<.hyp.sgm $<.ref.sgm

#############################################
# Post-processing:
#############################################

### Generate XML nbest list:
$(TESTID).%.gale.xml: $(TESTID).%.sorted $(TESTID).$(IE)
	cat $(TESTID).$(*F).1best.log | grep ^WeightConfig | perl -ne 'while(/:([\d\.E\-]+)/g) { print "$$1 " }' > .tmp.weights
	mtij.py $+ "`cat .tmp.weights`" stanford > $@
	rm .tmp.weights

%.sorted: %
	nbest_sort < $< > $@

### Truecase and generate xml:
%.post.xml.scbleu: %.post.xml
	mteval-v13.pl --brevity-penalty shortest -s $(TESTID)-src.sgm -r $(TESTID)-ref.sgm -t $< -c -b > $@ 
%.post.xml.cbleu: %.post.xml
	mteval-v13.pl -s $(TESTID)-src.sgm -r $(TESTID)-ref.sgm -t $< -c -b > $@ 
%.post.xml: %.post
	cat $< | $(TC) $(TESTID).sgm 2> $@.log | $(POSTTC) > $@ 
%.fixed.xml: %.post.xml
	cat $< | perl -pe 's/<doc docid="(.*)">/<DOC docid="$$1">/ig' > $@

%.lcpost.xml: %
	(cat $< | phrasal_sort | $(POST) | en_detokenizer | plain2sgml $(TESTID).sgm stanford | perl -pe 's/<doc docid="(.*)">/<DOC docid="$$1">/ig') 2> logs/$@.err > $@

### Language specific postprocessing:
%.post: %
	(cat $< | phrasal_sort | $(POST)) 2> $@.err > $@

### Get phrase alignment for 1best:
%.align: %.$Nbest
	nbest-to-1best < $< > $@

#############################################
# 1-best and n-best output with Phrasal:
#############################################

MBR_SCALE=1

# MBR:
$(TESTID).$(DEVID).%.mbr.$Nbest.s$(MBR_SCALE): $(TESTID).$(DEVID).%.mbr.$Nbest
	mbr -s $(MBR_SCALE) -n 4 -w $<.wts -i $< > $@ 2> $@.log

# Create Moses nbest list (no duplicates):
$(TESTID).$(DEVID).%.mbr.$Nbest: $(TESTID).$(DEVID).%.$Nbest
	nbest_sparse2dense `grep -A 1 '^\[weights-file\]' $(DEVID).$(*F).pmert/phrasal.best.ini | tail -1` $< uniq  2> $@.wts | perl -pe 's/([^|])\|([^|])/$1$2/g' | sort -s -k 1n -k 10nr -t '|' > $@

$(TESTID).$(DEVID).mix.%.mbr.$Nbest: $(TESTID).$(DEVID).%.$Nbest $(TESTID).$(MIXMBR).$Nbest
	cat $+ | nbest_sort > $<.mix
	nbest_sparse2dense `grep -A 1 '^\[weights-file\]' $(DEVID).$(*F).pmert/phrasal.best.ini | tail -1` $<.mix uniq  2> $@.wts | perl -pe 's/([^|])\|([^|])/$1$2/g' | sort -s -k 1n -k 10nr -t '|' > $@

#############################################
# 1-best and n-best output with Phrasal:
#############################################

# Use best MERT paramters to generate n-best list:
test.$(TESTID).%.$Nbest: %.ini $(TESTID).$(IE) in.voc
	$(SCRIPTS)/update_ini -f $(TESTID).$(DEVID).$(*F).$Nbest -n $N $(DEVID) $(TESTID) < $< > $<.$(TESTID)
	($(JAVA) $(JAVA_PHRASAL_ARGS_N) -DUniqNBest=true edu.stanford.nlp.mt.Phrasal -config-file $<.$(TESTID) -moses-n-best-list true < $(TESTID).$(IE) > $@.1best) 2> $@.1best.log

# Use best MERT paramters to generate n-best list:
$(TESTID).$(DEVID).%.$Nbest: $(DEVID).%.pmert/phrasal.best.ini $(TESTID).$(IE) in.voc
	$(SCRIPTS)/update_ini -f $(TESTID).$(DEVID).$(*F).$Nbest -n $N $(DEVID) $(TESTID) < $< > $<.$(TESTID)
	($(JAVA) $(JAVA_PHRASAL_ARGS_N) -DUniqNBest=true edu.stanford.nlp.mt.Phrasal -config-file $<.$(TESTID) -moses-n-best-list true < $(TESTID).$(IE) > $@.1best) 2> $@.1best.log
$(TESTID).$(DEVID).%-bt.$Nbest: $(DEVID).%.pmert/phrasal.best.ini $(TESTID).$(IE) in.voc 
	$(SCRIPTS)/update_ini -f $(TESTID).$(DEVID).$(*F).$Nbest -n $N $(DEVID) $(TESTID) < $< > $<.$(TESTID)
	($(JAVA) $(JAVA_PHRASAL_ARGS_N) -DUniqNBest=true edu.stanford.nlp.mt.Phrasal -config-file $<.$(TESTID) -moses-n-best-list true < $(TESTID).$(IE) > $@.1best) 2> $@.1best.log

# Use final MERT paramters:
$(TESTID).$(DEVID).%.f1best: $(DEVID).%.pmert/phrasal.final.ini $(TESTID).$(IE) in.voc
	$(SCRIPTS)/update_ini $(DEVID) $(TESTID) < $< > $<.$(TESTID)
	($(JAVA) $(JAVA_PHRASAL_ARGS) edu.stanford.nlp.mt.Phrasal $<.$(TESTID) < $(TESTID).$(IE) > $@) 2> $@.log

# Use best MERT paramters:
$(TESTID).$(DEVID).%.b1best: $(DEVID).%.pmert/phrasal.best.ini $(TESTID).$(IE) in.voc
	$(SCRIPTS)/update_ini $(DEVID) $(TESTID) < $< > $<.$(TESTID)
	($(JAVA) $(JAVA_PHRASAL_ARGS) edu.stanford.nlp.mt.Phrasal $<.$(TESTID) < $(TESTID).$(IE) > $@) 2> $@.log
$(TESTID).$(DEVID).%.b1best: $(DEVID).%.pmert/phrasal.best.ini $(TESTID).$(IE) in.voc
	$(SCRIPTS)/update_ini $(DEVID) $(TESTID) < $< > $<.$(TESTID)
	($(JAVA) $(JAVA_PHRASAL_ARGS) edu.stanford.nlp.mt.Phrasal $<.$(TESTID) < $(TESTID).$(IE) > $@) 2> $@.log

#############################################
# Convert dev ini file into test:
#############################################

$(TESTID).%.ini: $(DEVID).%.mert/moses.ini
	$(SCRIPTS)/update_ini SETID $(TESTID) < $< > $@

$(TESTID).%.phrasal.ini: $(DEVID).%.pmert/phrasal.final.ini
	$(SCRIPTS)/update_ini SETID $(TESTID) < $< > $@

#############################################
# MERT:
#############################################

# Maximize BLEU (n-best):
$(DEVID).%.$N.pmert/phrasal.final.ini: %.ini $D $(DEVID).$(IE) in.voc
	$(SCRIPTS)/update_ini SETID $(DEVID) < $< > $(DEVID).$<
	$(PM) --working-dir=$(DEVID).$(*F).$N.pmert \
	--phrasal-flags="$(PHRASAL_ARGS)" --java-flags="$(JAVA_PHRASAL_ARGS)" --mert-java-flags="$(JAVA_MERT_ARGS)" \
	--nbest=$N $(DEVID).$(IE) $(DEV_REF)/ref $(OPT_METRIC) $(DEVID).$< >& logs/$(DEVID).$(*F).$N.pmert.log

#############################################
# Language models:
#############################################

### Binarize LM:
%.lm.bin: %.lm.gz
	ngram -order 5 -lm $< -write-bin-lm $@ > $@.log

### Filter LM for target dev/test set:
%.flt_giga.lm.gz: %.e.phrases %.e.class.phrases $(GIGA)
	cat $(*F).e.phrases $(*F).e.class.phrases | sort | uniq | $(LM_FILTER_SCRIPT) $(GIGA) 5 2> $@.log | gzip > $@

#############################################
# Vocabulary files:
#############################################

%.e.vocab: %.e.phrases
	cat $+ | ngram-count -text - -write-vocab $@ -order 1 -tolower

%.e.phrases: %.tables/phrase-table.gz %.$(FE)
	zcat $< | sed 's/ ||| /\t/g' | cut -f 2 | \
	remove_bad_english 2> $@.skip | grep -v '^$$' | \
	cat - $(*F).$(FE) | sort | uniq > $@

#############################################
# Phrase tables:
#############################################

### Split translation features and re-ordering features:
ifneq ($(FILTER),fast)
%.tables/phrase-table.gz: %.tables/filtered.gz
	zcat $< | $(SCRIPTS)/split_po_tables $(*F).tables/phrase-table.gz $(*F).tables/$(LO_ID).gz $(LO_SZ) $(DEP) >& $@.log
%.tables/filtered.gz: %.tables/merged.gz
	zcat $< | $(SCRIPTS)/filter_po_tables $(*F).tables/filtered.gz /dev/null $(MINP) 0 >& $@.log
else
%.tables/phrase-table.gz: %.tables/merged.gz
	zcat $< | $(SCRIPTS)/filter_po_tables $(*F).tables/phrase-table.gz $(*F).tables/$(LO_ID).gz $(MINP) $(LO_SZ) >& $@.log
endif

### Generate table containing both phrase and lexicalized re-ordering probabilities for a given dev/test set:
### (needs to be run on 16G machines)
%.tables/merged.gz: %.$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN)
	mkdir $(*F).tables 2> /dev/null || true
	$(JAVA) -Xmx$(MEMSIZE) -Xms$(MEMSIZE) edu.stanford.nlp.mt.train.PhraseExtract $(THREADS) $(XOPTS) $(SPLIT) -fCorpus $(TRAIN)/corpus.$F -eCorpus $(TRAIN)/corpus.$E -align $(TRAIN)/corpus.$(ALIGN) -fFilterCorpus $< -maxELen $(maxPLen) -endAtLine $(LINES) $(LO_ARGS) 2> $@.log | gzip > $@

#############################################
# Phrase counts:
#############################################

COUNT_EXTRACT=$(JAVA) -Xmx$(MEMSIZE) -Xms$(MEMSIZE) edu.stanford.nlp.mt.train.PhraseExtract $(THREADS) $(XOPTS) $(SPLIT) -fCorpus $(TRAIN)/corpus.$F -eCorpus $(TRAIN)/corpus.$E -align $(TRAIN)/corpus.$(ALIGN) -endAtLine $(LINES) -noAlign -exactPhiCounts false -maxELen $(maxPLen) -extractors edu.stanford.nlp.mt.train.CountFeatureExtractor -maxLen $(maxPLen) 

$(TESTID).tables/counts.gz: $(TESTID).$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN)
	$(COUNT_EXTRACT) -fFilterCorpus $< 2> $@.log | gzip > $@

#############################################
# Lexicalized reordering tables:
#############################################

LEX_EXTRACT=$(JAVA) -Xmx$(MEMSIZE) -Xms$(MEMSIZE) edu.stanford.nlp.mt.train.PhraseExtract $(THREADS) $(XOPTS) $(SPLIT) -fCorpus $(TRAIN)/corpus.$F -eCorpus $(TRAIN)/corpus.$E -align $(TRAIN)/corpus.$(ALIGN) -phiFilter 0 -endAtLine $(LINES) -noAlign -exactPhiCounts false -maxELen $(maxPLen)

### for tuning set:
$(DEVID).tables/lo-word.%.gz: $(DEVID).$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN) $D
	$(LEX_EXTRACT) -fFilterCorpus $< -extractors edu.stanford.nlp.mt.train.PhiFeatureExtractor:edu.stanford.nlp.mt.train.LexicalReorderingFeatureExtractor -maxLen $(maxPLen) -orientationModelType $(*F) -phrasalLexicalizedModel false 2> $@.log | gzip > $@
$(DEVID).tables/lo-phrase.%.gz: $(DEVID).$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN) $D
	$(LEX_EXTRACT) -fFilterCorpus $< -extractors edu.stanford.nlp.mt.train.PhiFeatureExtractor:edu.stanford.nlp.mt.train.LexicalReorderingFeatureExtractor -maxLen $(maxPLen) -orientationModelType $(*F) -phrasalLexicalizedModel true  2> $@.log | gzip > $@
$(DEVID).tables/lo-hier.%.gz: $(DEVID).$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN) $D
	$(LEX_EXTRACT) -fFilterCorpus $< -extractors edu.stanford.nlp.mt.train.PhiFeatureExtractor:edu.stanford.nlp.mt.train.LexicalReorderingFeatureExtractor -maxLen 500        -orientationModelType $(*F) -phrasalLexicalizedModel true  2> $@.log | gzip > $@

### for test set:
$(TESTID).tables/lo-word.%.gz: $(TESTID).$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN) $D
	$(LEX_EXTRACT) -fFilterCorpus $< -extractors edu.stanford.nlp.mt.train.PhiFeatureExtractor:edu.stanford.nlp.mt.train.LexicalReorderingFeatureExtractor -maxLen $(maxPLen) -orientationModelType $(*F) -phrasalLexicalizedModel false 2> $@.log | gzip > $@
$(TESTID).tables/lo-phrase.%.gz: $(TESTID).$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN) $D
	$(LEX_EXTRACT) -fFilterCorpus $< -extractors edu.stanford.nlp.mt.train.PhiFeatureExtractor:edu.stanford.nlp.mt.train.LexicalReorderingFeatureExtractor -maxLen $(maxPLen) -orientationModelType $(*F) -phrasalLexicalizedModel true  2> $@.log | gzip > $@
$(TESTID).tables/lo-hier.%.gz: $(TESTID).$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN) $D
	$(LEX_EXTRACT) -fFilterCorpus $< -extractors edu.stanford.nlp.mt.train.PhiFeatureExtractor:edu.stanford.nlp.mt.train.LexicalReorderingFeatureExtractor -maxLen 500        -orientationModelType $(*F) -phrasalLexicalizedModel true  2> $@.log | gzip > $@

#############################################
# Pre-processing: 
# (remove unk words, etc.)
#############################################

NE=sed 's/^ *$$/null/'


### sgml with unknown words marked in the text:
%.mprep: %.prep %.unk
	dwdiff $(*F).unk $(*F).prep > $@ || true

### Handle unknown words (e.g., delete them):
%.prep: %.unk %.tables/phrase-table.gz
	rm -f $@
	$(PRE) $(*F).tables/phrase-table.gz $(*F).unk $@.tmp >& $@.err 
	cat $@.tmp | $(NE) > $@
	rm -f $@.tmp

### Convert SGML file into raw text, and remove IBM classing:
%.unk: %.sgm
	cat $< | ruby -w /u/nlp/data/gale/bin/sgml-to-text.rb | ibm2noclass | tr 'A-Z' 'a-z' > $@
%.cunk: %.sgm
	cat $< | ruby -w /u/nlp/data/gale/bin/sgml-to-text.rb | tr 'A-Z' 'a-z' > $@

%.counts.html: %.counts
	cat $< | counts2html $(TESTID) > $@

#############################################
# Tests
#############################################

.PHONY: check

check:
	cat Makefile.local | $(JAVANLP_HOME)/projects/mt/makefiles/phrase_extract/scripts/check-local-makefile
