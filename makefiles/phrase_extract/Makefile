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

# Evaluation scripts:
METEOR=/u/nlp/packages/meteor-0.6
JTER=tercom_mult_java.pl
BLEU=/u/nlp/data/gale/scr/chinese-interlingua/multi-bleu-uncased-nist.perl

# TER default parameters (too slow for MERT):
#BEAM_WIDTH=20
#SHIFT_SIZE=50
# TER parameters (runs about 4x faster than default, but still slow -- 
# e.g., 1 iteration of MERT takes 24h on MT06):
BEAM_WIDTH=5
SHIFT_SIZE=12

# Corpus/genre/language specific stuff here:
include Makefile.local

LTABLES_DEV= \
	$(DEVID).tables/lo-word.msd-bidirectional-fe.gz \
	$(DEVID).tables/lo-phrase.msd-bidirectional-fe.gz \
	$(DEVID).tables/lo-hier.msd-bidirectional-fe.gz \
	$(DEVID).tables/lo-hier.msd2-bidirectional-fe.gz

LTABLES_TEST= \
	$(TESTID).tables/lo-word.msd-bidirectional-fe.gz \
	$(TESTID).tables/lo-phrase.msd-bidirectional-fe.gz \
	$(TESTID).tables/lo-hier.msd-bidirectional-fe.gz \
	$(TESTID).tables/lo-hier.msd2-bidirectional-fe.gz

# Main targets:
test: $(TESTID).tables/phrase-table.gz $(TESTID).flt_giga.lm.gz $(TESTID).$(DEVID).$(SYSID).$N.f1best
test_best: $(TESTID).tables/phrase-table.gz $(TESTID).flt_giga.lm.gz $(TESTID).$(DEVID).$(SYSID).$N.b1best
nbest: $(TESTID).tables/phrase-table.gz $(TESTID).flt_giga.lm.gz $(TESTID).$(DEVID).$(SYSID).$N.$Nbest.1best.post
train: $(TESTID).tables/phrase-table.gz $(TESTID).flt_giga.lm.gz $(DEVID).$(SYSID).$N.pmert/phrasal.final.ini

test_google: $(TESTID).tables/phrase-table.gz $(TESTID).flt_giga.lm.gz $(TESTID).rsc_google.lm.gz $(TESTID).$(DEVID).$(SYSID).$N.f1best
test_best_google: $(TESTID).tables/phrase-table.gz $(TESTID).flt_giga.lm.gz $(TESTID).rsc_google.lm.gz $(TESTID).$(DEVID).$(SYSID).$N.b1best
nbest_google: $(TESTID).tables/phrase-table.gz $(TESTID).flt_giga.lm.gz $(TESTID).rsc_google.lm.gz $(TESTID).$(DEVID).$(SYSID).$N.$Nbest.1best.post
train_google: extract_google $(DEVID).$(SYSID).$N.pmert/phrasal.final.ini
extract_google: $(TESTID).tables/phrase-table.gz $(TESTID).flt_giga.lm.gz $(TESTID).rsc_google.lm.gz 

ltables: $(LTABLES)

# Java configuration:
PHRASAL_ARGS=$(DEBUG_PHRASAL) -Xmx$(PMEMSIZE) -Xms$(PMEMSIZE) 
PHRASAL_ARGS_N=$(DEBUG_PHRASAL) -Xmx$(PMEMSIZE_BM) -Xms$(PMEMSIZE_BM) 

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

### TER-BLEU:
$(TESTID).%.tb: $(TESTID).%
	cat $< | java -Xmx2g -Xms2g mt.metrics.LinearCombinationMetric $(TEST_REF)/ref{0,1,2,3} > $@

### BLEU:
$(TESTID).%.bleu: $(TESTID).%
	cat $< | $(BLEU) $(TEST_REF)/ref > $@
$(DEVID).%.bleu: $(DEVID).%
	cat $< | $(BLEU) $(DEV_REF)/ref > $@

#############################################
# Post-processing:
#############################################

### Generate XML nbest list:
$(TESTID).%.xml: $(TESTID).% $(TESTID).unk
	cat logs/$<.1best.log | grep ^WeightConfig | perl -ne 'while(/:([\d\.E\-]+)/g) { print "$$1 " }' > .tmp.weights
	mtij.py $+ "`cat .tmp.weights`" stanford > $@
	rm .tmp.weights

### TODO: check nbest list
#%.nbest: %.raw_nbest weights.v
# cat $< | check_nbest_list weights > $@ 2> $@.log

%.release/out.sgm: %.post
	mkdir $(*F).release || true
	cat $(*F).post | empty2sgml $(TESTID).sgm stanford > $@ 2> $@.log

### Language specific postprocessing:
%.post: %
	(cat $< | $(POST)) 2> $@.err > $@

### Get phrase alignment for 1best:
%.align: %.$Nbest
	nbest-to-1best < $< > $@

#############################################
# 1-best and n-best output with Phrasal:
#############################################

# Use best MERT paramters to generate n-best list:
$(TESTID).$(DEVID).%.$Nbest.1best: $(DEVID).%.pmert/phrasal.best.ini $(TESTID).$(IE)
	$(SCRIPTS)/update_ini -f $(TESTID).$(DEVID).$(*F).$Nbest -n $N $(DEVID) $(TESTID) < $< > $<.$(TESTID)
	(java $(PHRASAL_ARGS_N) mt.PseudoMoses -config-file $<.$(TESTID) -moses-n-best-list true -uniq-n-best-list true < $(TESTID).$(IE) > $@) 2> logs/$@.log

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
	$(MOSESDEC) -config $< -include-alignment-in-n-best -n-best-list $@ $N distinct < $(DEVID).$(IE) \
	> $@.1best 2> logs/$@.log
$(TESTID).%.nbest: $(TESTID).%.ini $(TESTID).$(IE) $(TESTID).flt_giga.lm.gz
	$(MOSESDEC) -config $< -include-alignment-in-n-best -n-best-list $@ $N distinct < $(TESTID).$(IE) \
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

# Maximize BLEU (n-best):
$(DEVID).%.$N.pmert/phrasal.final.ini: %.ini $(DEVID).flt_giga.lm.gz $(DEVID).$(IE) 
	$(SCRIPTS)/update_ini mt03 $(DEVID) < $< > $(DEVID).$<
	$(PM) $(NBEST_SIZE) --working-dir=$(DEVID).$(*F).$N.pmert --java-flags="$(PHRASAL_ARGS)" \
	--nbest=$N $(DEVID).$(IE) $(DEV_REF)/ref bleu $(DEVID).$< >& logs/$(DEVID).$(*F).$N.pmert.log

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
$(DEVID).%.$N.bt_pmert/phrasal.final.ini: %.ini $(DEVID).flt_giga.lm.gz $(DEVID).$(IE) 
	$(SCRIPTS)/update_ini mt03 $(DEVID) < $< > $(DEVID).$<
	$(PM) --working-dir=$(DEVID).$(*F).$N.bt_pmert --java-flags="$(PHRASAL_ARGS) -DSTARTING_POINTS=5 -DtuneLength=false" \
	--nbest=$N $(DEVID).$(IE) $(DEV_REF)/ref bleu-ter $(DEVID).$< >& logs/$(DEVID).$(*F).$N.bt_pmert.log

$(DEVID).%.bt_pmert/phrasal.final.ini: %.ini $(DEVID).flt_giga.lm.gz $(DEVID).$(IE) 
	$(SCRIPTS)/update_ini mt03 $(DEVID) < $< > $(DEVID).$<
	$(PM) --working-dir=$(DEVID).$(*F).bt_pmert --java-flags="$(PHRASAL_ARGS) -DSTARTING_POINTS=5 -DtuneLength=false" \
	$(DEVID).$(IE) $(DEV_REF)/ref bleu-ter $(DEVID).$< >& logs/$(DEVID).$(*F).bt_pmert.log

#############################################
# Prepare class-based models:
#############################################

%.clm.gz: %.lm.gz
	zcat $< | lm_ibm2danc | gzip > $@
%.cunk: %.sgm
	cat $< | sgml2txt | input_ibm2danc | lc > $@

#############################################
# Language models:
#############################################

### Gigaword:

# Filter out any ngram that does not match any substring of any concatenation of English phrase table entries:
%.flt_giga.lm.gz: %.e.vocab $(GIGA)
	cat $(*F).e.vocab | $(LM_FILTER_SCRIPT) $(GIGA) 3 2> $@.log | gzip > $@

### Google LM:

%.rsc_google.lm.gz: %.flt_google.lm.gz
	(ngram -rescore-ngram $< -lm $(GOOGLE_LM) -write-lm $@ -count-lm -order 3 -vocab-aliases $(*F).e.aliases -limit-vocab -memuse -count-lm -vocab $(*F).e.vocab) >& $@.log
%.flt_google.lm.gz: %.e.vocab %.init_google.lm.gz
	cat $(*F).e.vocab | $(LM_FILTER_SCRIPT) $(*F).init_google.lm.gz 2> $@.log | gzip > $@
%.init_google.lm.gz: %.e.vocab %.e.aliases
	(zcat $(GOOGLE_CT)/{2,3}gms/*.gz | cat $(GOOGLE_CT)/1gms/vocab - | time /u/nlp/packages/SRILM-1.5.6/bin/i686-m64_c/ngram-count -wbdiscount -interpolate -read - -debug 2 -order 3 -vocab $(*F).e.vocab -vocab-aliases $(*F).e.aliases -limit-vocab -read-with-mincounts -memuse -gt1min 1 -gt2min 3 -gt2max 100 -gt3min 300 -gt3max 10000 -lm $@) >& $@.log

#############################################
# Vocabulary files:
#############################################

%.aliases: %.vocab
	make_alias < $< > $@

%.e.vocab: %.e.phrases
	cat $< | ngram-count -text - -write-vocab $@ -order 1 -tolower

%.e.phrases: %.tables/phrase-table.gz %.$(FE)
	zcat $< | sed 's/ ||| /\t/g' | cut -f 2 | \
	remove_bad_english 2> $@.skip | grep -v '^$$' | \
	cat - $(*F).$(FE) | sort | uniq > $@

#############################################
# Phrase tables:
#############################################

### Split translation features and re-ordering features:
%.tables/phrase-table.gz: %.tables/merged.gz
	mkdir logs || true
	zcat $< | $(SCRIPTS)/split_po_tables $(*F).tables/phrase-table.gz $(*F).tables/$(LO_ID).gz $(MINP) $(LO_SZ) >& $@.log

### Generate table containing both phrase and lexicalized re-ordering probabilities for a given dev/test set:
### (needs to be run on 16G machines)
%.tables/merged.gz: %.$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN)
	mkdir $(*F).tables || true
	java -DShowPhraseRestriction=false -DkeepBadTokens=true -Xmx$(MEMSIZE) -Xms$(MEMSIZE) mt.train.CombinedFeatureExtractor $(XOPTS) $(SPLIT) -fCorpus $(TRAIN)/corpus.$F -eCorpus $(TRAIN)/corpus.$E -align $(TRAIN)/corpus.$(ALIGN) -fFilterCorpus $< -extractors moses -maxELen $(maxPLen) -exactPhiCounts $(exactPhiCounts) -endAtLine $(LINES) -maxFertility 5 $(LO_ARGS) 2> $@.log | gzip > $@

#############################################
# Phrase counts:
#############################################

COUNT_EXTRACT=java -DkeepBadTokens=true -Xmx$(MEMSIZE) -Xms$(MEMSIZE) mt.train.CombinedFeatureExtractor $(XOPTS) $(SPLIT) -fCorpus $(TRAIN)/corpus.$F -eCorpus $(TRAIN)/corpus.$E -align $(TRAIN)/corpus.$(ALIGN) -endAtLine $(LINES) -noAlign -exactPhiCounts false -maxFertility 5 -maxELen $(maxPLen) -extractors mt.train.CountFeatureExtractor -maxLen $(maxPLen) 

$(TESTID).tables/counts.gz: $(TESTID).$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN)
	$(COUNT_EXTRACT) -fFilterCorpus $< 2> $@.log | gzip > $@

#############################################
# Lexicalized reordering tables:
#############################################

LEX_EXTRACT=java -DkeepBadTokens=true -Xmx$(MEMSIZE) -Xms$(MEMSIZE) mt.train.CombinedFeatureExtractor $(XOPTS) $(SPLIT) -fCorpus $(TRAIN)/corpus.$F -eCorpus $(TRAIN)/corpus.$E -align $(TRAIN)/corpus.$(ALIGN) -phiFilter $(MINP) -endAtLine $(LINES) -noAlign -exactPhiCounts false -maxFertility 5 -maxELen $(maxPLen)

### for tuning set:
$(DEVID).tables/lo-word.%.gz: $(DEVID).$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN)
	$(LEX_EXTRACT) -fFilterCorpus $< -extractors mt.train.PhiFeatureExtractor:mt.train.ExperimentalLexicalReorderingFeatureExtractor -maxLen $(maxPLen) -pharaohLexicalizedModel $(*F) -phrasalLexicalizedModel false 2> $@.log | gzip > $@
$(DEVID).tables/lo-phrase.%.gz: $(DEVID).$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN)
	$(LEX_EXTRACT) -fFilterCorpus $< -extractors mt.train.PhiFeatureExtractor:mt.train.ExperimentalLexicalReorderingFeatureExtractor -maxLen $(maxPLen) -pharaohLexicalizedModel $(*F) -phrasalLexicalizedModel true  2> $@.log | gzip > $@
$(DEVID).tables/lo-hier.%.gz: $(DEVID).$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN)
	$(LEX_EXTRACT) -fFilterCorpus $< -extractors mt.train.PhiFeatureExtractor:mt.train.ExperimentalLexicalReorderingFeatureExtractor -maxLen 500        -pharaohLexicalizedModel $(*F) -phrasalLexicalizedModel true  2> $@.log | gzip > $@

### for test set:
$(TESTID).tables/lo-word.%.gz: $(TESTID).$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN)
	$(LEX_EXTRACT) -fFilterCorpus $< -extractors mt.train.PhiFeatureExtractor:mt.train.ExperimentalLexicalReorderingFeatureExtractor -maxLen $(maxPLen) -pharaohLexicalizedModel $(*F) -phrasalLexicalizedModel false 2> $@.log | gzip > $@
$(TESTID).tables/lo-phrase.%.gz: $(TESTID).$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN)
	$(LEX_EXTRACT) -fFilterCorpus $< -extractors mt.train.PhiFeatureExtractor:mt.train.ExperimentalLexicalReorderingFeatureExtractor -maxLen $(maxPLen) -pharaohLexicalizedModel $(*F) -phrasalLexicalizedModel true  2> $@.log | gzip > $@
$(TESTID).tables/lo-hier.%.gz: $(TESTID).$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN)
	$(LEX_EXTRACT) -fFilterCorpus $< -extractors mt.train.PhiFeatureExtractor:mt.train.ExperimentalLexicalReorderingFeatureExtractor -maxLen 500        -pharaohLexicalizedModel $(*F) -phrasalLexicalizedModel true  2> $@.log | gzip > $@

#############################################
# Pre-processing: 
# (remove unk words, etc.)
#############################################

### sgml with unknown words marked in the text:
%.mprep: %.prep %.unk
	dwdiff $(*F).unk $(*F).prep > $@ || true

### Handle unknown words (e.g., delete them):
%.prep: %.unk %.tables/phrase-table.gz
	rm -f $@
	$(PRE) $(*F).tables/phrase-table.gz $(*F).unk $@ >& $@.err

### Convert SGML file into raw text, and remove IBM classing:
%.unk: %.sgm
	cat $< | sgml2txt | ibm2noclass | lc > $@

%.counts.html: %.counts
	cat $< | counts2html $(TESTID) > $@
