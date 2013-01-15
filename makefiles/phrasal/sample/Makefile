.SECONDARY:

.PHONY: test check

###########################################
# Makefile for generating phrase tables
# and LMs filtered against a dev/test set.
###########################################

### Make sure JAVANLP_HOME is defined:
JNLP=$(JAVANLP_HOME)
SCRIPTS=$(JNLP)/projects/mt/scripts
ESCRIPTS=$(JNLP)/projects/mt/external_scripts

### Default parameters for entries defined in Makefile.local:
JAVA=java
PM=phrasal-mert.pl
TC=en_truecaser
POSTTC=cat
POST=remove_unk
LM_FILTER_SCRIPT=filter_lm_unigram
N=200
OPT_METRIC=bleu
FILTER=fast
IE=prep
FE=unk
# Feature extractor threads. Mert and decoding threads set
# separately
THREADS=-threads 4

### Corpus/genre/language specific stuff here:
include Makefile.local

### No default target:
all:
	echo "No default target; please refer to http://nlp.stanford.edu/nlpwiki/MTPipelineJavaNLP for a list of valid targets."

### Target to create directories:
D=logs/.create $(DEVID).tables/.create $(TESTID).tables/.create
$D:
	mkdir $(DEVID).tables $(TESTID).tables logs 2> /dev/null || true

### Java configuration:
JAVA_MERT_ARGS=$(DEBUG_PHRASAL) -Xmx$(MMEMSIZE) -Xms$(MMEMSIZE)
JAVA_PHRASAL_ARGS=$(DEBUG_PHRASAL) -Xmx$(PMEMSIZE) -Xms$(PMEMSIZE)

#############################################
# Main targets
#############################################

### Build LMs + phrase tables (test):
test_extract: $(TESTID).tables/phrase-table.gz

### Build LMs + phrase tables (dev):
dev_extract: $(DEVID).tables/phrase-table.gz

### Run MERT:
train: dev_extract $(DEVID).$(SYSID).$N.pmert/phrasal.final.ini

### Decode test data:
nbest: train test_extract $(TESTID).$(DEVID).$(SYSID).$N.$Nbest_ibm
mbr: $(TESTID).$(DEVID).$(SYSID).$N.mbr.$Nbest.s1.post
sgml: $(TESTID).$(DEVID).$(SYSID).$N.mbr.$Nbest.s1.final.xml

### Generate nbest in format expected for GALE:
gale: nbest sgml

#############################################
# Post-processing:
#############################################

### Generate uniq'ed nbest list:
%best_ibm: %best
	(cat $+ | nbest_sort | nbest2uniq | nbest2ibm > $@) 2> $@.log

### Truecase and generate xml:
%.final.xml: %.post.xml
	cat $< | perl -pe 's/<doc docid="(.*)">/<DOC docid="$$1">/ig' > $@
%.post.xml: %.post $(TESTID).sgm
	cat $< | $(TC) $(TESTID).sgm 2> $@.log | $(POSTTC) > $@ 

### Language specific postprocessing:
%.post: %
	(cat $< | $(POST)) 2> $@.err > $@

#############################################
# MBR:
#############################################

MBR_SCALE=1

### MBR:
$(TESTID).$(DEVID).%.mbr.$Nbest.s$(MBR_SCALE): $(TESTID).$(DEVID).%.mbr.$Nbest
	mbr -s $(MBR_SCALE) -n 4 -w $<.wts -i $< > $@ 2> $@.log

### Create Moses nbest list (no duplicates):
$(TESTID).$(DEVID).%.mbr.$Nbest: $(TESTID).$(DEVID).%.$Nbest
	nbest_sparse2dense `grep -A 1 '^\[weights-file\]' $(DEVID).$(*F).pmert/phrasal.best.ini | tail -1` $< uniq  2> $@.wts | perl -pe 's/([^|])\|([^|])/$1$2/g' | sort -s -k 1n -k 10nr -t '|' > $@

#############################################
# Decode test data:
#############################################

### Use best MERT paramters to generate n-best list:
$(TESTID).$(DEVID).%.$Nbest: $(DEVID).%.pmert/phrasal.best.ini $(TESTID).$(IE) $(TESTID).f.vocab
	$(SCRIPTS)/update_ini -f $(TESTID).$(DEVID).$(*F).$Nbest -n $N $(DEVID) $(TESTID) < $< > $(TESTID).$(DEVID).$(*F).ini
	($(JAVA) $(JAVA_PHRASAL_ARGS) edu.stanford.nlp.mt.Phrasal -config-file $(TESTID).$(DEVID).$(*F).ini -moses-n-best-list true < $(TESTID).$(IE) > $@.1best) 2> $@.1best.log

### Find best set of parameters:
$(DEVID).%.pmert/phrasal.best.ini: $(DEVID).%.pmert/phrasal.final.ini
	rm $@ || true
	eval `link-best-ini $(DEVID).$(*F).pmert`

#############################################
# MERT:
#############################################

$(DEVID).%.$N.pmert/phrasal.final.ini: %.ini $D $(DEVID).$(IE) $(DEVID).f.vocab
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
	cat $(*D)/$(*F).e.phrases $(*D)/$(*F).e.class.phrases | sort | uniq | $(LM_FILTER_SCRIPT) $(GIGA) 5 2> $@.log | gzip > $@

#############################################
# Vocabulary files:
#############################################

### List all words in foreign input:
%.f.vocab: %.$(IE)
	cat $+ | ngram-count -text - -write-vocab $@ -order 1 -unk

### List all words in phrase table:
%.e.vocab: %.e.phrases
	cat $+ | ngram-count -text - -write-vocab $@ -order 1 -tolower -unk

### List all phrases in phrase table:
%.e.phrases: %.tables/phrase-table.gz %.$(FE)
	zcat $< | sed 's/ ||| /\t/g' | cut -f 2 | \
	remove_bad_english 2> $@.skip | grep -v '^$$' | \
	cat - $(*D)/$(*F).$(FE) | sort | uniq > $@

#############################################
# Phrase tables:
#############################################

### Split translation features and re-ordering features:
ifneq ($(FILTER),fast)
%.tables/phrase-table.gz: %.tables/filtered.gz
	zcat $< | $(SCRIPTS)/split_po_tables $(*D)/$(*F).tables/phrase-table.gz $(*D)/$(*F).tables/$(LO_ID).gz $(LO_SZ) $(DEP) >& $@.log
%.tables/filtered.gz: %.tables/merged.gz
	zcat $< | $(SCRIPTS)/filter_po_tables $(*D)/$(*F).tables/filtered.gz /dev/null $(MINP) 0 >& $@.log
else
%.tables/phrase-table.gz: %.tables/merged.gz
	zcat $< | $(SCRIPTS)/filter_po_tables $(*D)/$(*F).tables/phrase-table.gz $(*D)/$(*F).tables/$(LO_ID).gz $(MINP) $(LO_SZ) >& $@.log
endif

### Generate table containing both phrase and lexicalized re-ordering probabilities for a given dev/test set:
### (needs to be run on 16G machines)
%.tables/merged.gz: %.$(FE) $(TRAIN)/aligned.$F $(TRAIN)/aligned.$E $(TRAIN)/aligned.$(ALIGN)
	mkdir $(*D)/$(*F).tables 2> /dev/null || true
	$(JAVA) $(DEBUG_PHRASAL) -Xmx$(MEMSIZE) -Xms$(MEMSIZE) edu.stanford.nlp.mt.train.PhraseExtract $(THREADS) $(XOPTS) $(SPLIT) -fCorpus $(TRAIN)/aligned.$F -eCorpus $(TRAIN)/aligned.$E -align $(TRAIN)/aligned.$(ALIGN) -fFilterCorpus $< -maxELen $(maxPLen) -endAtLine $(LINES) $(LO_ARGS) 2> $@.log | gzip > $@

############################################
# Pre-processing: 
# (remove unk words, etc.)
#############################################

### Replace empty lines with 'null':
NE=sed 's/^ *$$/null/'

### Handle unknown words (e.g., delete them):
%.prep: %.unk %.tables/phrase-table.gz
	rm -f $@
	$(PRE) $(*D)/$(*F).tables/phrase-table.gz $(*D)/$(*F).unk $@.tmp >& $@.err 
	cat $@.tmp | $(NE) > $@
	rm -f $@.tmp

### Convert SGML file into raw text, and remove IBM classing:
%.unk: %.sgm
	cat $< | ruby -w /u/nlp/data/gale/bin/sgml-to-text.rb | ibm2noclass | tr 'A-Z' 'a-z' > $@

#############################################
# Consistency checks:
#############################################

### Check that Makefile.local is correct:
check:
	cat Makefile.local | $(JAVANLP_HOME)/projects/mt/makefiles/phrasal/scripts/check-local-makefile

#############################################
# All rules not needed in core Makefile 
#############################################

### Off by default.
#include Makefile.research
