.SECONDARY:

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

# Build LMs (test):
test_lm: $(TESTID).flt_giga.lm.gz
test_lm_google: $(TESTID).flt_giga.lm.gz $(TESTID).rsc_google.lm.gz
test_lm_gale: $(TESTID).flt_giga.lm.gz $(TESTID).rsc_google.lm.gz $(TESTID).flt_bbn.lm.gz

# Build LMs (dev):
dev_lm: $(DEVID).flt_giga.lm.gz
dev_lm_google: $(DEVID).flt_giga.lm.gz $(DEVID).rsc_google.lm.gz
dev_lm_gale: $(DEVID).flt_giga.lm.gz $(DEVID).rsc_google.lm.gz $(DEVID).flt_bbn.lm.gz

# Build LMs + phrase tables (test):
test_extract: test_lm $(TESTID).tables/phrase-table.gz
test_extract_google: test_lm_google $(TESTID).tables/phrase-table.gz
test_extract_gale: test_lm_gale $(TESTID).tables/phrase-table.gz

# Build LMs + phrase tables (dev):
dev_extract: dev_lm $(DEVID).tables/phrase-table.gz
dev_extract_google: dev_lm_google $(DEVID).tables/phrase-table.gz
dev_extract_gale: dev_lm_gale $(DEVID).tables/phrase-table.gz

# Run MERT:
train: dev_extract $(DEVID).$(SYSID).$N.pmert/phrasal.final.ini
train_google: dev_extract_google $(DEVID).$(SYSID).$N.pmert/phrasal.final.ini
train_gale: dev_extract_gale $(DEVID).$(SYSID).$N.pmert/phrasal.final.ini

# Decode test data (with nbest list):
test: test_extract $(TESTID).$(DEVID).$(SYSID).f1best.post
test_google: test_extract_google $(TESTID).$(DEVID).$(SYSID).f1best.post
test_gale: test_extract_gale $(TESTID).$(DEVID).$(SYSID).f1best.post

# Decode test data (without nbest list):
nbest: test_extract $(TESTID).$(DEVID).$(SYSID).$N.$Nbest.1best.post
nbest_google: test_extract_google $(TESTID).$(DEVID).$(SYSID).$N.$Nbest.1best.post
nbest_gale: test_extract_gale $(TESTID).$(DEVID).$(SYSID).$N.$Nbest.1best.post

ltables: $(LTABLES_DEV) $(LTABLES_TEST)

# Java configuration:
JAVA_PHRASAL_ARGS=$(DEBUG_PHRASAL) -Xmx$(PMEMSIZE) -Xms$(PMEMSIZE) 
JAVA_PHRASAL_ARGS_N=$(DEBUG_PHRASAL) -Xmx$(PMEMSIZE_BM) -Xms$(PMEMSIZE_BM) 

# Moses Configuration
MOSES_ARGS=$(LOCAL_MOSES_ARGS)

#############################################
# Evaluation:
#############################################

### BLEU:
$(DEVID).%.bleu: $(DEVID).%
	cat $< | phrasal_sort | java mt.metrics.BLEUMetric $(DEV_REF)/ref{0,1,2,3} > $@
$(TESTID).%.bleu: $(TESTID).%
	cat $< | phrasal_sort | java mt.metrics.BLEUMetric $(TEST_REF)/ref{0,1,2,3} > $@

### TER:
$(DEVID).%.ter: $(DEVID).%
	cat $< | java mt.metrics.TERMetric $(DEV_REF)/ref{0,1,2,3} > $@
$(TESTID).%.ter: $(TESTID).%
	cat $< | java mt.metrics.TERMetric $(TEST_REF)/ref{0,1,2,3} > $@

### TER-BLEU:
$(DEVID).%.tb: $(DEVID).%
	cat $< | java -Xmx2g -Xms2g mt.metrics.LinearCombinationMetric $(DEV_REF)/ref{0,1,2,3} > $@
$(TESTID).%.tb: $(TESTID).%
	cat $< | java -Xmx2g -Xms2g mt.metrics.LinearCombinationMetric $(TEST_REF)/ref{0,1,2,3} > $@

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
$(TESTID).%.gale.xml: $(TESTID).% $(TESTID).$(IE)
	cat $<.1best.log | grep ^WeightConfig | perl -ne 'while(/:([\d\.E\-]+)/g) { print "$$1 " }' > .tmp.weights
	mtij.py $+ "`cat .tmp.weights`" stanford > $@
	rm .tmp.weights

%.release/out.sgm: %.post
	mkdir $(*F).release || true
	cat $(*F).post | empty2sgml $(TESTID).sgm stanford > $@ 2> $@.log

### Truecase and generate xml:
%.post.xml.scbleu: %.post.xml
	mteval-v13.pl --brevity-penalty shortest -s $(TESTID)-src.sgm -r $(TESTID)-ref.sgm -t $< -c -b > $@ 
%.post.xml.cbleu: %.post.xml
	mteval-v13.pl -s $(TESTID)-src.sgm -r $(TESTID)-ref.sgm -t $< -c -b > $@ 
%.post.xml: %.post
	cat $< | $(TC) $(TESTID)-src.sgm > $@ 2> $@.log

### Language specific postprocessing:
%.post: %
	(cat $< | $(POST)) 2> $@.err > $@

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

# Create Moses nbest list:
$(TESTID).$(DEVID).%.mbr.$Nbest: $(TESTID).$(DEVID).%.$Nbest
	nbest_sparse2dense `grep -A 1 '^\[weights-file\]' $(DEVID).$(*F).pmert/phrasal.best.ini | tail -1` $< uniq 2> $@.wts | sort -n > $@ 

#############################################
# 1-best and n-best output with Phrasal:
#############################################

# Use best MERT paramters to generate n-best list:
$(TESTID).$(DEVID).%.$Nbest: $(DEVID).%.pmert/phrasal.best.ini $(TESTID).$(IE)  $(TESTID).flt_giga.lm.gz
	$(SCRIPTS)/update_ini -f $(TESTID).$(DEVID).$(*F).$Nbest -n $N $(DEVID) $(TESTID) < $< > $<.$(TESTID)
	(java $(JAVA_PHRASAL_ARGS_N) -DUniqNBest=true mt.PseudoMoses -config-file $<.$(TESTID) -moses-n-best-list true < $(TESTID).$(IE) > $@.1best) 2> $@.1best.log

# Use final MERT paramters:
$(TESTID).$(DEVID).%.f1best: $(DEVID).%.pmert/phrasal.final.ini $(TESTID).$(IE) $(TESTID).flt_giga.lm.gz
	$(SCRIPTS)/update_ini $(DEVID) $(TESTID) < $< > $<.$(TESTID)
	(java $(JAVA_PHRASAL_ARGS) mt.PseudoMoses $<.$(TESTID) < $(TESTID).$(IE) > $@) 2> $@.log
$(DEVID).$(DEVID).%.f1best: $(DEVID).%.pmert/phrasal.final.ini $(DEVID).$(IE) $(DEVID).flt_giga.lm.gz
	$(SCRIPTS)/update_ini $(DEVID) $(DEVID) < $< > $<.$(DEVID)
	(java $(JAVA_PHRASAL_ARGS) mt.PseudoMoses $<.$(DEVID) < $(DEVID).$(IE) > $@) 2> $@.log

# Use best MERT paramters:
$(TESTID).$(DEVID).%.b1best: $(DEVID).%.pmert/phrasal.best.ini $(TESTID).$(IE) $(TESTID).flt_giga.lm.gz
	$(SCRIPTS)/update_ini $(DEVID) $(TESTID) < $< > $<.$(TESTID)
	(java $(JAVA_PHRASAL_ARGS) mt.PseudoMoses $<.$(TESTID) < $(TESTID).$(IE) > $@) 2> $@.log
$(DEVID).$(DEVID).%.b1best: $(DEVID).%.pmert/phrasal.best.ini $(DEVID).$(IE) $(DEVID).flt_giga.lm.gz
	$(SCRIPTS)/update_ini $(DEVID) $(DEVID) < $< > $<.$(DEVID)
	(java $(JAVA_PHRASAL_ARGS) mt.PseudoMoses $<.$(DEVID) < $(DEVID).$(IE) > $@) 2> $@.log

#############################################
# 1-best output with Moses (MBR or Viterbi):
#############################################

$(TESTID).%.1best: $(TESTID).%.ini $(TESTID).$(IE)
	$(MOSESDEC) $(MOSES_ARGS) -config $< < $(TESTID).$(IE) > $@ 2> $@.log

$(TESTID).%.mbr: $(TESTID).%.ini $(TESTID).$(IE) $(TESTID).flt_giga.lm.gz
	$(MOSESDEC) $(MOSES_ARGS) -config $< -mbr -mbr-size 200 -mbr-scale 1 < $(TESTID).$(IE) > $@ 2> $@.log

$(DEVID).%.mbr: $(DEVID).%.ini $(DEVID).$(IE) $(DEVID).flt_giga.lm.gz
	$(MOSESDEC) $(MOSES_ARGS) -config $< -mbr -mbr-size 200 -mbr-scale 1 < $(DEVID).$(IE) > $@ 2> $@.log

#############################################
# N-best output with Moses:
#############################################

$(DEVID).%.nbest: $(DEVID).%.mert/moses.ini $(DEVID).$(IE) $(DEVID).flt_giga.lm.gz
	$(MOSESDEC) $(MOSES_ARGS) -config $< -include-alignment-in-n-best -n-best-list $@ $N distinct < $(DEVID).$(IE) \
	> $@.1best 2> $@.log
$(TESTID).%.nbest: $(TESTID).%.ini $(TESTID).$(IE) $(TESTID).flt_giga.lm.gz
	$(MOSESDEC) $(MOSES_ARGS) -config $< -include-alignment-in-n-best -n-best-list $@ $N distinct < $(TESTID).$(IE) \
	> $@.1best 2> $@.log

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
	$(SCRIPTS_ROOTDIR)/training/absolutize_moses_model.pl $(DEVID).$< > $(DEVID).$<.abs
	$(MM) --nofilter-phrase-table --rootdir=$(SCRIPTS_ROOTDIR) --working-dir=$(DEVID).$(*F).mert/ --closest \
	$(DEVID).$(IE) $(DEV_REF)/ref $(MOSESDEC) $(DEVID).$<.abs > $@.log 2> $@.err

#############################################
# Phrasal MERT:
#############################################

# Maximize BLEU (n-best):
$(DEVID).%.$N.pmert/phrasal.final.ini: %.ini $D $(DEVID).flt_giga.lm.gz $(DEVID).$(IE) 
	$(SCRIPTS)/update_ini mt03 $(DEVID) < $< > $(DEVID).$<
	$(PM) $(NBEST_SIZE) --working-dir=$(DEVID).$(*F).$N.pmert \
	--phrasal-flags="$(PHRASAL_ARGS)" --java-flags="$(JAVA_PHRASAL_ARGS)" \
	--nbest=$N $(DEVID).$(IE) $(DEV_REF)/ref bleu $(DEVID).$< >& logs/$(DEVID).$(*F).$N.pmert.log

# Maximize METEOR (n-best):
$(DEVID).%.$N.meteor_pmert/phrasal.final.ini: %.ini $D $(DEVID).flt_giga.lm.gz $(DEVID).$(IE) 
	$(SCRIPTS)/update_ini mt03 $(DEVID) < $< > $(DEVID).$<
	$(PM) $(NBEST_SIZE) --working-dir=$(DEVID).$(*F).$N.meteor_pmert \
	--phrasal-flags="$(PHRASAL_ARGS)" --java-flags="$(JAVA_PHRASAL_ARGS)" \
	--nbest=$N $(DEVID).$(IE) $(DEV_REF)/ref meteor:0.5:2.5:0.4 $(DEVID).$< >& logs/$(DEVID).$(*F).$N.meteor_pmert.log

# Maximize METEOR+BLEU (n-best):
$(DEVID).%.$N.bm_pmert/phrasal.final.ini: %.ini $D $(DEVID).flt_giga.lm.gz $(DEVID).$(IE) 
	$(SCRIPTS)/update_ini mt03 $(DEVID) < $< > $(DEVID).$<
	$(PM) $(NBEST_SIZE) --working-dir=$(DEVID).$(*F).$N.bm_pmert \
	--phrasal-flags="$(PHRASAL_ARGS)" --java-flags="$(JAVA_PHRASAL_ARGS)" \
	--nbest=$N $(DEVID).$(IE) $(DEV_REF)/ref bleu+2meteor:0.95:0.5:0.5 $(DEVID).$< >& logs/$(DEVID).$(*F).$N.bm_pmert.log

# Maximize BLEU:
$(DEVID).%.pmert/phrasal.final.ini: %.ini $D $(DEVID).flt_giga.lm.gz $(DEVID).$(IE) 
	$(SCRIPTS)/update_ini mt03 $(DEVID) < $< > $(DEVID).$<
	$(PM) $(NBEST_SIZE) --working-dir=$(DEVID).$(*F).pmert \
	--phrasal-flags="$(PHRASAL_ARGS)" --java-flags="$(JAVA_PHRASAL_ARGS)" \
	$(DEVID).$(IE) $(DEV_REF)/ref bleu $(DEVID).$< >& logs/$(DEVID).$(*F).pmert.log

# Minimize TER:
$(DEVID).%.ter_pmert/phrasal.final.ini: %.ini $D $(DEVID).flt_giga.lm.gz $(DEVID).$(IE) 
	$(SCRIPTS)/update_ini mt03 $(DEVID) < $< > $(DEVID).$<
	$(PM) --working-dir=$(DEVID).$(*F).ter_pmert \
	--phrasal-flags="$(PHRASAL_ARGS)" --java-flags="$(JAVA_PHRASAL_ARGS)" \
	$(DEVID).$(IE) $(DEV_REF)/ref ter:$(BEAM_WIDTH):$(SHIFT_SIZE) $(DEVID).$< >& logs/$(DEVID).$(*F).ter_pmert.log

# Minimize TER-BLEU (tune BP only):
$(DEVID).%.$N.bt_pmert/phrasal.final.ini: %.ini $D $(DEVID).flt_giga.lm.gz $(DEVID).$(IE) 
	$(SCRIPTS)/update_ini mt03 $(DEVID) < $< > $(DEVID).$<
	$(PM) --working-dir=$(DEVID).$(*F).$N.bt_pmert \
	--phrasal-flags="$(PHRASAL_ARGS)" --java-flags="$(JAVA_PHRASAL_ARGS) -DSTARTING_POINTS=5 -DtuneLength=false" \
	--nbest=$N $(DEVID).$(IE) $(DEV_REF)/ref bleu-ter $(DEVID).$< >& logs/$(DEVID).$(*F).$N.bt_pmert.log

$(DEVID).%.bt_pmert/phrasal.final.ini: %.ini $D $(DEVID).flt_giga.lm.gz $(DEVID).$(IE) 
	$(SCRIPTS)/update_ini mt03 $(DEVID) < $< > $(DEVID).$<
	$(PM) --working-dir=$(DEVID).$(*F).bt_pmert \
	--phrasal-flags="$(PHRASAL_ARGS)" --java-flags="$(JAVA_PHRASAL_ARGS) -DSTARTING_POINTS=5 -DtuneLength=false" \
	$(DEVID).$(IE) $(DEV_REF)/ref bleu-ter $(DEVID).$< >& logs/$(DEVID).$(*F).bt_pmert.log

#############################################
# Language models:
#############################################

### Binarize LM:
%.lm.bin: %.lm.gz
	ngram -order 5 -lm $< -write-bin-lm $@ > $@.log

### Mixed model (google + giga):

%.mix1.lm.gz: %.flt_giga.lm.gz %.rsc_google.lm.gz
	ngram -lm $(*F).flt_giga.lm.gz -mix-lm $(*F).rsc_google.lm.gz -lambda $(LM_MIX1) -write-lm $@ -order 5

### BBN FOUO data:

%.flt_bbn.lm.gz: %.e.phrases %.e.class.phrases $(BBN)
	cat $(*F).e.phrases $(*F).e.class.phrases | sort | uniq | $(LM_FILTER_SCRIPT) $(BBN) 5 2> $@.log | gzip > $@

### Gigaword:

%.flt_giga.lm.gz: %.e.phrases %.e.class.phrases $(GIGA)
	cat $(*F).e.phrases $(*F).e.class.phrases | sort | uniq | $(LM_FILTER_SCRIPT) $(GIGA) 5 2> $@.log | gzip > $@

### Google LM:

%.rsc_google.lm.gz: %.flt_google.lm.gz
	(ngram -rescore-ngram $< -lm $(GOOGLE_LM) -write-lm $@ -count-lm -order 3 -vocab-aliases $(*F).e.aliases -limit-vocab -memuse -count-lm -vocab $(*F).e.vocab) >& $@.log
%.flt_google.lm.gz: %.e.vocab %.e.aliases
	(zcat $(GOOGLE_CT)/{2,3}gms/*.gz | cat $(GOOGLE_CT)/1gms/vocab - | time /u/nlp/packages/SRILM-1.5.6/bin/i686-m64_c/ngram-count -wbdiscount -interpolate -read - -debug 2 -order 3 -vocab $(*F).e.vocab -vocab-aliases $(*F).e.aliases -limit-vocab -read-with-mincounts -memuse -gt1min 1 -gt2min 3 -gt2max 100 -gt3min 200 -gt3max 10000 -lm $@) >& $@.log

#############################################
# Vocabulary files:
#############################################

%.aliases: %.vocab
	make_alias < $< > $@

%.e.vocab: %.e.phrases %.e.class.phrases
	cat $+ | ngram-count -text - -write-vocab $@ -order 1 -tolower

%.e.class.phrases: %.e.phrases
	(cat $< | eng_simpl_class.pl 1 0 | ibm2class > $@) 2> logs/$@.log

%.e.phrases: %.tables/phrase-table.gz %.$(FE)
	zcat $< | sed 's/ ||| /\t/g' | cut -f 2 | \
	remove_bad_english 2> $@.skip | grep -v '^$$' | \
	cat - $(*F).$(FE) | sort | uniq > $@

#############################################
# Phrase tables:
#############################################

### Split translation features and re-ordering features:
%.tables/phrase-table.gz: %.tables/merged.gz
	zcat $< | $(SCRIPTS)/split_po_tables $(*F).tables/phrase-table.gz $(*F).tables/$(LO_ID).gz $(MINP) $(LO_SZ) >& $@.log

### Generate table containing both phrase and lexicalized re-ordering probabilities for a given dev/test set:
### (needs to be run on 16G machines)
%.tables/merged.gz: %.$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN)
	mkdir $(*F).tables || true
	java -DShowPhraseRestriction=false -DkeepBadTokens=true -Xmx$(MEMSIZE) -Xms$(MEMSIZE) mt.train.CombinedFeatureExtractor $(XOPTS) $(SPLIT) -fCorpus $(TRAIN)/corpus.$F -eCorpus $(TRAIN)/corpus.$E -align $(TRAIN)/corpus.$(ALIGN) -fFilterCorpus $< -extractors mt.train.PharaohFeatureExtractor:mt.train.CountFeatureExtractor:mt.train.ExperimentalLexicalReorderingFeatureExtractor -maxELen $(maxPLen) -exactPhiCounts $(exactPhiCounts) -endAtLine $(LINES) -maxFertility 5 $(LO_ARGS) 2> $@.log | gzip > $@

#############################################
# Soft phrases:
#############################################

soft: $(DEVID).tables/soft-phrases.0.gz $(DEVID).tables/soft-phrases.1.gz $(DEVID).tables/soft-phrases.2.gz $(DEVID).tables/soft-phrases.3.gz $(DEVID).tables/soft-phrases.4.gz 

$(DEVID).tables/soft-phrases.%.gz: $(DEVID).$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN)
	java -DShowPhraseRestriction=false -DkeepBadTokens=true -Xmx$(MEMSIZE) -Xms$(MEMSIZE) mt.train.CombinedFeatureExtractor $(XOPTS) $(SPLIT) -fCorpus $(TRAIN)/corpus.$F -eCorpus $(TRAIN)/corpus.$E -align $(TRAIN)/corpus.$(ALIGN) -fFilterCorpus $< -maxConsistencyViolations $(*F) -extractors mt.train.PharaohFeatureExtractor -maxELen $(maxPLen) -exactPhiCounts $(exactPhiCounts) -endAtLine $(LINES) -maxFertility 5 2> $@.log | gzip > $@

$(TESTID).tables/soft-phrases.%.gz: $(TESTID).$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN)
	java -DShowPhraseRestriction=false -DkeepBadTokens=true -Xmx$(MEMSIZE) -Xms$(MEMSIZE) mt.train.CombinedFeatureExtractor $(XOPTS) $(SPLIT) -fCorpus $(TRAIN)/corpus.$F -eCorpus $(TRAIN)/corpus.$E -align $(TRAIN)/corpus.$(ALIGN) -fFilterCorpus $< -maxConsistencyViolations $(*F) -extractors mt.train.PharaohFeatureExtractor -maxELen $(maxPLen) -exactPhiCounts $(exactPhiCounts) -endAtLine $(LINES) -maxFertility 5 2> $@.log | gzip > $@

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
$(DEVID).tables/lo-word.%.gz: $(DEVID).$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN) $D
	$(LEX_EXTRACT) -fFilterCorpus $< -extractors mt.train.PhiFeatureExtractor:mt.train.ExperimentalLexicalReorderingFeatureExtractor -maxLen $(maxPLen) -pharaohLexicalizedModel $(*F) -phrasalLexicalizedModel false 2> $@.log | gzip > $@
$(DEVID).tables/lo-phrase.%.gz: $(DEVID).$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN) $D
	$(LEX_EXTRACT) -fFilterCorpus $< -extractors mt.train.PhiFeatureExtractor:mt.train.ExperimentalLexicalReorderingFeatureExtractor -maxLen $(maxPLen) -pharaohLexicalizedModel $(*F) -phrasalLexicalizedModel true  2> $@.log | gzip > $@
$(DEVID).tables/lo-hier.%.gz: $(DEVID).$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN) $D
	$(LEX_EXTRACT) -fFilterCorpus $< -extractors mt.train.PhiFeatureExtractor:mt.train.ExperimentalLexicalReorderingFeatureExtractor -maxLen 500        -pharaohLexicalizedModel $(*F) -phrasalLexicalizedModel true  2> $@.log | gzip > $@

### for test set:
$(TESTID).tables/lo-word.%.gz: $(TESTID).$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN) $D
	$(LEX_EXTRACT) -fFilterCorpus $< -extractors mt.train.PhiFeatureExtractor:mt.train.ExperimentalLexicalReorderingFeatureExtractor -maxLen $(maxPLen) -pharaohLexicalizedModel $(*F) -phrasalLexicalizedModel false 2> $@.log | gzip > $@
$(TESTID).tables/lo-phrase.%.gz: $(TESTID).$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN) $D
	$(LEX_EXTRACT) -fFilterCorpus $< -extractors mt.train.PhiFeatureExtractor:mt.train.ExperimentalLexicalReorderingFeatureExtractor -maxLen $(maxPLen) -pharaohLexicalizedModel $(*F) -phrasalLexicalizedModel true  2> $@.log | gzip > $@
$(TESTID).tables/lo-hier.%.gz: $(TESTID).$(FE) $(TRAIN)/corpus.$F $(TRAIN)/corpus.$E $(TRAIN)/corpus.$(ALIGN) $D
	$(LEX_EXTRACT) -fFilterCorpus $< -extractors mt.train.PhiFeatureExtractor:mt.train.ExperimentalLexicalReorderingFeatureExtractor -maxLen 500        -pharaohLexicalizedModel $(*F) -phrasalLexicalizedModel true  2> $@.log | gzip > $@

#############################################
# Pre-processing: 
# (remove unk words, etc.)
#############################################

NE=sed 's/^ *$$/null/'

%/.create:
	mkdir $(*F) && touch $@

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
