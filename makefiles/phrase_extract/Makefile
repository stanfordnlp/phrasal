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

LTABLES_DEV= \
	$(DEVID).tables/lo-word.msd-bidirectional-fe.gz \
	$(DEVID).tables/lo-phrase.msd-bidirectional-fe.gz \
	$(DEVID).tables/lo-hier.msd-bidirectional-fe.gz \
	$(DEVID).tables/lo-hier.msd2-bidirectional-fe.gz \

LTABLES_TEST= \
	$(TESTID).tables/lo-word.msd-bidirectional-fe.gz \
	$(TESTID).tables/lo-phrase.msd-bidirectional-fe.gz \
	$(TESTID).tables/lo-hier.msd-bidirectional-fe.gz \
	$(TESTID).tables/lo-hier.msd2-bidirectional-fe.gz

# Main targets:
all: train
builddev: $(DEVID).flt_giga.lm.gz $(DEVID).rsc_google.lm.gz $(LTABLES_DEV)
buildtest: $(TESTID).flt_giga.lm.gz $(TESTID).rsc_google.lm.gz $(LTABLES_TEST)
ltables: $(LTABLES)

# Java configuration:
PHRASAL_ARGS=$(DEBUG_PHRASAL) -Xmx$(PMEMSIZE) -Xms$(PMEMSIZE) 
PHRASAL_ARGS_N=$(DEBUG_PHRASAL) -Xmx$(PMEMSIZE_BM) -Xms$(PMEMSIZE_BM) -DOutputMosesNBestList=true -DUniqNBest=true

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

# Use best MERT paramters to generate 500best list:
$(TESTID).$(DEVID).%.500best.1best: $(DEVID).%.pmert/phrasal.best.ini $(TESTID).$(IE)
	$(SCRIPTS)/update_ini -f $(TESTID).$(DEVID).$(*F).500best -n 500 $(DEVID) $(TESTID) < $< > $<.$(TESTID)
	(java $(PHRASAL_ARGS_N) mt.PseudoMoses $<.$(TESTID) < $(TESTID).$(IE) > $@) 2> logs/$@.log

# Use final MERT paramters:
$(TESTID).$(DEVID).%.f1best: $(DEVID).%.pmert/phrasal.final.ini $(TESTID).$(IE)
	$(SCRIPTS)/update_ini $(DEVID) $(TESTID) < $< > $<.$(TESTID)
	(java $(PHRASAL_ARGS) mt.PseudoMoses $<.$(TESTID) < $(TESTID).$(IE) > $@) 2> logs/$@.log
$(DEVID).$(DEVID).%.f1best: $(DEVID).%.pmert/phrasal.final.ini $(DEVID).$(IE)
	$(SCRIPTS)/update_ini $(DEVID) $(DEVID) < $< > $<.$(DEVID)
	(java $(PHRASAL_ARGS) mt.PseudoMoses $<.$(DEVID) < $(DEVID).$(IE) > $@) 2> logs/$@.log

# Use best MERT paramters:
$(TESTID).$(DEVID).%.b1best: $(DEVID).%.pmert/phrasal.best.ini $(TESTID).$(IE)
	$(SCRIPTS)/update_ini $(DEVID) $(TESTID) < $< > $<.$(TESTID)
	(java $(PHRASAL_ARGS) mt.PseudoMoses $<.$(TESTID) < $(TESTID).$(IE) > $@) 2> logs/$@.log
$(DEVID).$(DEVID).%.b1best: $(DEVID).%.pmert/phrasal.best.ini $(DEVID).$(IE)
	$(SCRIPTS)/update_ini $(DEVID) $(DEVID) < $< > $<.$(DEVID)
	(java $(PHRASAL_ARGS) mt.PseudoMoses $<.$(DEVID) < $(DEVID).$(IE) > $@) 2> logs/$@.log

#############################################
# 1-best output with Moses (MBR or Viterbi):
#############################################

$(TESTID).%.1best: $(TESTID).%.ini $(TESTID).$(IE)
	$(MOSESDEC) -config $< < $(TESTID).$(IE) > $@ 2> $@.log

$(TESTID).%.mbr: $(TESTID).%.ini $(TESTID).$(IE) $(TESTID).flt_giga.lm.gz
	$(MOSESDEC) -config $< -mbr -mbr-size 200 -mbr-scale 1 < $(TESTID).$(IE) > $@ 2> logs/$@.log

$(DEVID).%.mbr: $(DEVID).%.ini $(DEVID).$(IE) $(DEVID).flt_giga.lm.gz
	$(MOSESDEC) -config $< -mbr -mbr-size 200 -mbr-scale 1 < $(DEVID).$(IE) > $@ 2> logs/$@.log

#############################################
# N-best output with Moses:
#############################################

$(DEVID).%.nbest: $(DEVID).%.mert/moses.ini $(DEVID).$(IE) $(DEVID).flt_giga.lm.gz
	$(MOSESDEC) -config $< -include-alignment-in-n-best -n-best-list $@ 500 distinct < $(DEVID).$(IE) \
	> $@.1best 2> logs/$@.log
$(TESTID).%.nbest: $(TESTID).%.ini $(TESTID).$(IE) $(TESTID).flt_giga.lm.gz
	$(MOSESDEC) -config $< -include-alignment-in-n-best -n-best-list $@ 500 distinct < $(TESTID).$(IE) \
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

$(DEVID).%.mert/moses.ini: %.ini $(DEVID).flt_giga.lm.gz $(DEVID).$(IE)
	mkdir $(DEVID).$(*F).mert
	$(SCRIPTS)/update_ini mt03 $(DEVID) < $< > $(DEVID).$<
	/u/nlp/packages/moses-2008-05-18/trunk/scripts/training/absolutize_moses_model.pl $(DEVID).$< > $(DEVID).$<.abs
	$(MM) --nofilter-phrase-table --rootdir=$(MOSES)/scripts --working-dir=$(DEVID).$(*F).mert/ --closest \
	$(DEVID).$(IE) $(DEV_REF)/ref $(MOSESDEC) $(DEVID).$<.abs > $@.log 2> $@.err

#############################################
# Phrasal MERT:
#############################################

# Maximize BLEU (500 best):
$(DEVID).%.500.pmert/phrasal.final.ini: %.ini $(DEVID).flt_giga.lm.gz $(DEVID).$(IE) 
	$(SCRIPTS)/update_ini mt03 $(DEVID) < $< > $(DEVID).$<
	$(PM) $(NBEST_SIZE) --working-dir=$(DEVID).$(*F).500.pmert --java-flags="$(PHRASAL_ARGS)" \
	--nbest=500 $(DEVID).$(IE) $(DEV_REF)/ref bleu $(DEVID).$< >& logs/$(DEVID).$(*F).500.pmert.log

# Maximize BLEU (200 best):
$(DEVID).%.200.pmert/phrasal.final.ini: %.ini $(DEVID).flt_giga.lm.gz $(DEVID).$(IE) 
	$(SCRIPTS)/update_ini mt03 $(DEVID) < $< > $(DEVID).$<
	$(PM) $(NBEST_SIZE) --working-dir=$(DEVID).$(*F).200.pmert --java-flags="$(PHRASAL_ARGS)" \
	--nbest=200 $(DEVID).$(IE) $(DEV_REF)/ref bleu $(DEVID).$< >& logs/$(DEVID).$(*F).200.pmert.log

# Maximize BLEU:
$(DEVID).%.pmert/phrasal.final.ini: %.ini $(DEVID).flt_giga.lm.gz $(DEVID).$(IE) 
	$(SCRIPTS)/update_ini mt03 $(DEVID) < $< > $(DEVID).$<
	$(PM) $(NBEST_SIZE) --working-dir=$(DEVID).$(*F).pmert --java-flags="$(PHRASAL_ARGS)" \
	$(DEVID).$(IE) $(DEV_REF)/ref bleu $(DEVID).$< >& logs/$(DEVID).$(*F).pmert.log

# Minimize TER:
$(DEVID).%.ter_pmert/phrasal.final.ini: %.ini $(DEVID).flt_giga.lm.gz $(DEVID).$(IE) 
	$(SCRIPTS)/update_ini mt03 $(DEVID) < $< > $(DEVID).$<
	$(PM) --working-dir=$(DEVID).$(*F).ter_pmert --java-flags="$(PHRASAL_ARGS)" \
	$(DEVID).$(IE) $(DEV_REF)/ref ter:$(BEAM_WIDTH):$(SHIFT_SIZE) $(DEVID).$< >& logs/$(DEVID).$(*F).ter_pmert.log

# Minimize TER-BLEU (tune BP only):
$(DEVID).%.bt_pmert/phrasal.final.ini: %.ini $(DEVID).flt_giga.lm.gz $(DEVID).$(IE) 
	$(SCRIPTS)/update_ini mt03 $(DEVID) < $< > $(DEVID).$<
	$(PM) --working-dir=$(DEVID).$(*F).bt_pmert --java-flags="$(PHRASAL_ARGS) -DSTARTING_POINTS=1 -DtuneLength=true" \
	$(DEVID).$(IE) $(DEV_REF)/ref bleu-ter $(DEVID).$< >& logs/$(DEVID).$(*F).bt_pmert.log

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
	(zcat $(GOOGLE_CT)/{2,3}gms/*.gz | cat $(GOOGLE_CT)/1gms/vocab - | time /u/nlp/packages/SRILM-1.5.6/bin/i686-m64_c/ngram-count -wbdiscount -interpolate -read - -debug 2 -order 3 -vocab $(*F).e.vocab -vocab-aliases $(*F).e.aliases -limit-vocab -read-with-mincounts -memuse -gt1min 1 -gt2min 3 -gt2max 100 -gt3min 300 -gt3max 10000 -lm $@) >& $@.log

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
