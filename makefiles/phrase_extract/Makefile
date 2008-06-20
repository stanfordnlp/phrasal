###########################################
# Makefile for generating phrase tables
# and LMs filtered against a dev/test set.
###########################################

# Make sure JAVANLP_HOME is defined:
JNLP=$(JAVANLP_HOME)
SCRIPTS=$(JNLP)/projects/mt/scripts
ESCRIPTS=$(JNLP)/projects/mt/external_scripts

# Moses MERT:
MOSESDIR=/u/nlp/packages/moses-2008-05-18
MM=$(MOSESDIR)/trunk/scripts/training/mert-moses.pl
MOSESDEC=$(MOSESDIR)/bin/moses 

# TER script:
METEOR=/u/nlp/packages/meteor-0.6
JTER=tercom_mult_java.pl

# TER default parameters (too slow for MERT):
#BEAM_WIDTH=20
#SHIFT_SIZE=50
# TER parameters (runs about 4x faster than default, but still slow -- 
# e.g., 1 iteration of MERT takes 24h on MT06):
BEAM_WIDTH=5
SHIFT_SIZE=12

# Corpus/genre/language specific stuff here:
include Makefile.local
include Makefile.extra

LTABLES= \
	$(DEVID).tables/lo-word.msd-bidirectional-fe.gz \
	$(DEVID).tables/lo-phrase.msd-bidirectional-fe.gz \
	$(DEVID).tables/lo-hier.msd-bidirectional-fe.gz \
	$(DEVID).tables/lo-hier.msd2-bidirectional-fe.gz \
	$(TESTID).tables/lo-word.msd-bidirectional-fe.gz \
	$(TESTID).tables/lo-phrase.msd-bidirectional-fe.gz \
	$(TESTID).tables/lo-hier.msd-bidirectional-fe.gz \
	$(TESTID).tables/lo-hier.msd2-bidirectional-fe.gz

# Main targets:
all: train
train: $(DEVID).flt_giga.lm.gz
ltables: $(LTABLES)

# Java configuration:
PHRASAL_ARGS=$(DEBUG_PHRASAL) -Xmx$(PMEMSIZE) -Xms$(PMEMSIZE)

.SECONDARY:

#############################################
# Evaluation:
#############################################

### TER for test/dev data:
### (the X in .terX refers to the "speed" of the algorithm used for TER computation;
###  1=DP, generally runs in 4-5 hours for 1000 sentences
###  2=beam search (beam size=10), generally runs in 10-15 minutes 
###  In general, expect 0.5% TER difference between DP and beam search. 
$(DEVID).%.ter: $(DEVID).%
	$(JTER) --sgm $(DEV_REF)/empty.sgm $(DEV_REF)/ref $< $@ >& logs/$@.log
$(TESTID).%.ter: $(TESTID).%
	$(JTER) --sgm $(TEST_REF)/empty.sgm $(TEST_REF)/ref $< $@ >& logs/$@.log

### Meteor:
$(TESTID).%.meteor: $(TESTID).%.mbr
	/scr/nlp/data/gale/MT-metric-correlation/scripts/txt2sgml --type hyp < $< > $<.hyp.sgm
	/scr/nlp/data/gale/MT-metric-correlation/scripts/txt2sgml --type ref < $(TEST_REF)/ref0 > $<.ref.sgm
	perl $(METEOR)/meteor.pl -s any -t $<.hyp.sgm -r $<.ref.sgm --modules "exact porter_stem wn_synonymy" >& $@
	rm $<.hyp.sgm $<.ref.sgm

### BLEU:
$(TESTID).%.bleu: $(TESTID).%
	cat $< | $(SCRIPTS)/multi-bleu-uncased-nist.perl $(TEST_REF)/ref > $@
$(DEVID).%.bleu: $(DEVID).%
	cat $< | $(SCRIPTS)/multi-bleu-uncased-nist.perl $(DEV_REF)/ref > $@

#############################################
# 1-best output with Phrasal:
#############################################

# Delete unk words and use MERT final paramters:
$(TESTID).%.phrasal.fp1best: $(DEVID).%.pmert/phrasal.final.ini $(TESTID).prep
	$(SCRIPTS)/update_ini $(DEVID) $(TESTID) < $< > $<.$(TESTID)
	(java $(PHRASAL_ARGS) mt.PseudoMoses $<.$(TESTID) < $(TESTID).prep > $@) 2> logs/$@.log
$(DEVID).%.phrasal.fp1best: $(DEVID).%.pmert/phrasal.final.ini $(DEVID).prep
	(java $(PHRASAL_ARGS) mt.PseudoMoses $< < $(DEVID).prep > $@) 2> logs/$@.log

# Delete unk words and use MERT best paramters:
$(TESTID).%.phrasal.bp1best: $(DEVID).%.pmert/phrasal.best.ini $(TESTID).prep
	$(SCRIPTS)/update_ini $(DEVID) $(TESTID) < $< > $<.$(TESTID)
	(java $(PHRASAL_ARGS) mt.PseudoMoses $<.$(TESTID) < $(TESTID).prep > $@) 2> logs/$@.log
$(DEVID).%.phrasal.bp1best: $(DEVID).%.pmert/phrasal.best.ini $(DEVID).prep
	(java $(PHRASAL_ARGS) mt.PseudoMoses $< < $(DEVID).prep > $@) 2> logs/$@.log

# Preserve unk words and use MERT final parameters:
# (preserving unk words is preferable in system combination)
$(TESTID).%.phrasal.1best: $(TESTID).%.phrasal.ini $(TESTID).unk
	(java $(PHRASAL_ARGS) -DOutputMosesNBestList=true -DUniqNBest=true mt.PseudoMoses $< < $(TESTID).unk > $@) 2> logs/$@.log
$(DEVID).%.phrasal.1best: $(DEVID).%.phrasal.ini $(DEVID).unk
	(java $(PHRASAL_ARGS) -DOutputMosesNBestList=true -DUniqNBest=true mt.PseudoMoses $< < $(DEVID).unk > $@) 2> logs/$@.log

#############################################
# 1-best output with Moses (MBR or Viterbi):
#############################################

$(TESTID).%.1best: $(TESTID).%.ini $(TESTID).prep
	$(MOSESDEC) -config $< < $(TESTID).prep > $@ 2> $@.log

$(TESTID).%.mbr: $(TESTID).%.ini $(TESTID).prep $(TESTID).flt_giga.lm.gz
	$(MOSESDEC) -config $< -mbr -mbr-size 200 -mbr-scale 1 < $(TESTID).prep > $@ 2> logs/$@.log

$(DEVID).%.mbr: $(DEVID).%.ini $(DEVID).prep $(DEVID).flt_giga.lm.gz
	$(MOSESDEC) -config $< -mbr -mbr-size 200 -mbr-scale 1 < $(DEVID).prep > $@ 2> logs/$@.log

#############################################
# N-best output with Moses:
#############################################

$(DEVID).%.nbest: $(DEVID).%.mert/moses.ini $(DEVID).unk $(DEVID).flt_giga.lm.gz
	$(MOSESDEC) -config $< -include-alignment-in-n-best -n-best-list $@ 500 distinct < $(DEVID).unk \
	> $@.1best 2> logs/$@.log
$(TESTID).%.nbest: $(TESTID).%.ini $(TESTID).unk $(TESTID).flt_giga.lm.gz
	$(MOSESDEC) -config $< -include-alignment-in-n-best -n-best-list $@ 500 distinct < $(TESTID).unk \
	> $@.1best 2> logs/$@.log

#############################################
# Convert dev ini file into test:
#############################################

$(TESTID).%.ini: $(DEVID).%.mert/moses.ini $(TESTID).flt_giga.lm.gz
	$(SCRIPTS)/update_ini $(DEVID) $(TESTID) < $< > $@

$(TESTID).%.phrasal.ini: $(DEVID).%.pmert/phrasal.final.ini $(TESTID).flt_giga.lm.gz
	$(SCRIPTS)/update_ini $(DEVID) $(TESTID) < $< > $@

#############################################
# Moses MERT:
#############################################

$(DEVID).%.mert/moses.ini: %.ini $(DEVID).flt_giga.lm.gz $(DEVID).prep 
	mkdir $(DEVID).$(*F).mert
	$(SCRIPTS)/update_ini mt03 $(DEVID) < $< > $(DEVID).$<
	/u/nlp/packages/moses-2008-05-18/trunk/scripts/training/absolutize_moses_model.pl $(DEVID).$< > $(DEVID).$<.abs
	$(MM) --nofilter-phrase-table --rootdir=$(MOSES)/scripts --working-dir=$(DEVID).$(*F).mert/ --closest \
	$(DEVID).prep $(DEV_REF)/ref $(MOSESDEC) $(DEVID).$<.abs > $@.log 2> $@.err

#############################################
# Phrasal MERT:
#############################################

# Maximize BLEU:
$(DEVID).%.pmert/phrasal.final.ini: %.ini $(DEVID).flt_giga.lm.gz $(DEVID).prep 
	$(SCRIPTS)/update_ini mt03 $(DEVID) < $< > $(DEVID).$<
	phrasal-mert-michel.pl --working-dir=$(DEVID).$(*F).pmert --java-flags="$(PHRASAL_ARGS)" \
	$(DEVID).prep $(DEV_REF)/ref bleu $(DEVID).$< >& $(DEVID).$(*F).pmert.log

# Minimize TER:
$(DEVID).%.ter_pmert/phrasal.final.ini: %.ini $(DEVID).flt_giga.lm.gz $(DEVID).prep 
	$(SCRIPTS)/update_ini mt03 $(DEVID) < $< > $(DEVID).$<
	phrasal-mert-michel.pl --working-dir=$(DEVID).$(*F).ter_pmert --java-flags="$(PHRASAL_ARGS)" \
	$(DEVID).prep $(DEV_REF)/ref ter:$(BEAM_WIDTH):$(SHIFT_SIZE) $(DEVID).$< >& $(DEVID).$(*F).ter_pmert.log

#############################################
# Pre- and post-processing: 
# (remove unk words, etc.)
#############################################

%.prep: %.unk %.tables/phrase-table.gz
	$(PRE) $(*F).tables/phrase-table.gz $(*F).unk $@ >& $@.err

%.postproc: %.mbr
	cat $< | $(POST) 2> $@.err | $(SCRIPTS)/nist_tok > $@

#############################################
# Language models:
#############################################

### Gigaword:

# Filter out any ngram that does not match any substring of any concatenation of English phrase table entries:
%.flt_giga.lm.gz: %.tables/phrase-table.gz %.unk.vocab $(GIGA)
	(zcat $< | sed 's/ ||| /\t/g' | cut -f 2 | \
	remove_bad_english 2> $@.skip | grep -v '^$$' | \
	cat - $(*F).unk.vocab | filter_lm $(GIGA) | \
	ngram -lm - -write-lm - -order 5 | gzip) 2> $@.log > $@

### Google LM:

%.rsc_google.lm.gz: %.flt_google.lm.gz
	(ngram -rescore-ngram $< -lm $(GOOGLE_LM) -write-lm $@ -count-lm -order 3 -vocab-aliases $(*F).e.aliases -limit-vocab -memuse -count-lm -vocab $(*F).e.vocab) >& $@.log
%.flt_google.lm.gz: %.tables/phrase-table.gz %.unk.vocab %.init_google.lm.gz
	(zcat $< | sed 's/ ||| /\t/g' | cut -f 2 | \
	remove_bad_english 2> $@.skip | grep -v '^$$' | \
	cat - $(*F).unk.vocab | filter_lm $(*F).init_google.lm.gz | \
	ngram -lm - -write-lm - -order 3 | gzip) 2> $@.log > $@
%.init_google.lm.gz: %.e.vocab %.e.aliases
	(zcat $(GOOGLE_CT)/{2,3}gms/*.gz | cat $(GOOGLE_CT)/1gms/vocab - | time ngram-count -wbdiscount -interpolate -read - -debug 2 -order 3 -vocab $(*F).e.vocab -vocab-aliases $(*F).e.aliases -limit-vocab -read-with-mincounts -memuse -gt1min 1 -gt2min 3 -gt2max 100 -gt3min 300 -gt3max 10000 -lm $@) >& $@.log

### Vocab files:

%.aliases: %.vocab
	make_alias < $< > $@

%.e.vocab: %.tables/phrase-table.gz %.unk
	zcat $< | sed 's/ ||| /\t/g' | cut -f 2 | \
	remove_bad_english 2> $@.skip | grep -v '^$$' | \
	cat - $(*F).unk | ngram-count -text - -write-vocab $@ -order 1

%.unk.vocab: %.unk
	cat $< | ngram-count -text - -write-vocab $@ -order 1

#############################################
# Phrase tables:
#############################################

### Split translation features and re-ordering features:
%.tables/phrase-table.gz: %.tables/merged.gz
	zcat $< | $(SCRIPTS)/split_po_tables $(*F).tables/phrase-table.gz $(*F).tables/msd-bidirectional-fe.gz $(MINP) >& $@.log

### Generate table containing both phrase and lexicalized re-ordering probabilities for a given dev/test set:
### (needs to be run on 16G machines)
%.tables/merged.gz: %.unk $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/giza/model/aligned.$(ALIGN)
	mkdir $(*F).tables || true
	java -DkeepBadTokens=true -Xmx$(MEMSIZE) -Xms$(MEMSIZE) mt.train.CombinedFeatureExtractor $(SPLIT) -fCorpus $(TRAIN)/corpus.$F -eCorpus $(TRAIN)/corpus.$E -align $(TRAIN)/giza/model/aligned.$(ALIGN) -fFilterCorpus $< -extractors moses -maxLen $(maxPLen) -exactPhiCounts $(exactPhiCounts) -endAtLine $(LINES) -maxFertility 5 2> $@.log | gzip > $@

#############################################
# Lexicalized reordering tables:
#############################################

LEX_EXTRACT=java -DkeepBadTokens=true -Xmx$(MEMSIZE) -Xms$(MEMSIZE) mt.train.CombinedFeatureExtractor $(SPLIT) -fCorpus $(TRAIN)/corpus.$F -eCorpus $(TRAIN)/corpus.$E -align $(TRAIN)/giza/model/aligned.$(ALIGN) -phiFilter $(MINP) -endAtLine $(LINES) -noAlign -exactPhiCounts false -maxFertility 5 -maxELen $(maxPLen)

### for tuning set:
$(DEVID).tables/lo-word.%.gz: $(DEVID).unk $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/giza/model/aligned.$(ALIGN)
	$(LEX_EXTRACT) -fFilterCorpus $< -extractors mt.train.PhiFeatureExtractor:mt.train.ExperimentalLexicalReorderingFeatureExtractor -maxLen $(maxPLen) -pharaohLexicalizedModel $(*F) -phrasalLexicalizedModel false 2> $@.log | gzip > $@
$(DEVID).tables/lo-phrase.%.gz: $(DEVID).unk $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/giza/model/aligned.$(ALIGN)
	$(LEX_EXTRACT) -fFilterCorpus $< -extractors mt.train.PhiFeatureExtractor:mt.train.ExperimentalLexicalReorderingFeatureExtractor -maxLen $(maxPLen) -pharaohLexicalizedModel $(*F) -phrasalLexicalizedModel true  2> $@.log | gzip > $@
$(DEVID).tables/lo-hier.%.gz: $(DEVID).unk $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/giza/model/aligned.$(ALIGN)
	$(LEX_EXTRACT) -fFilterCorpus $< -extractors mt.train.PhiFeatureExtractor:mt.train.ExperimentalLexicalReorderingFeatureExtractor -maxLen 500        -pharaohLexicalizedModel $(*F) -phrasalLexicalizedModel true  2> $@.log | gzip > $@

### for test set:
$(TESTID).tables/lo-word.%.gz: $(TESTID).unk $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/giza/model/aligned.$(ALIGN)
	$(LEX_EXTRACT) -fFilterCorpus $< -extractors mt.train.PhiFeatureExtractor:mt.train.ExperimentalLexicalReorderingFeatureExtractor -maxLen $(maxPLen) -pharaohLexicalizedModel $(*F) -phrasalLexicalizedModel false 2> $@.log | gzip > $@
$(TESTID).tables/lo-phrase.%.gz: $(TESTID).unk $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/giza/model/aligned.$(ALIGN)
	$(LEX_EXTRACT) -fFilterCorpus $< -extractors mt.train.PhiFeatureExtractor:mt.train.ExperimentalLexicalReorderingFeatureExtractor -maxLen $(maxPLen) -pharaohLexicalizedModel $(*F) -phrasalLexicalizedModel true  2> $@.log | gzip > $@
$(TESTID).tables/lo-hier.%.gz: $(TESTID).unk $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/giza/model/aligned.$(ALIGN)
	$(LEX_EXTRACT) -fFilterCorpus $< -extractors mt.train.PhiFeatureExtractor:mt.train.ExperimentalLexicalReorderingFeatureExtractor -maxLen 500        -pharaohLexicalizedModel $(*F) -phrasalLexicalizedModel true  2> $@.log | gzip > $@
