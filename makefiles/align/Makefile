include Makefile.local.$(ID)

MOSES_SCRIPTS=/u/nlp/packages/moses-2008-05-18/trunk/scripts
TRAIN=$(MOSES_SCRIPTS)/training/train-factored-phrase-model.perl

TRAIN_OPTIONS=$(GIZA) --root-dir . --f $F --e $E --corpus corpus --scripts-root-dir=$(MOSES_SCRIPTS) --factor-delimiter="|||" --parallel --alignment $(ALIGN)

SYM_DATA=model/aligned.$(ALIGN) model/aligned.$E model/aligned.$F
MERGED_DATA=giza.$E-$F/$E-$F.A3.gz giza.$F-$E/$F-$E.A3.gz
SPLIT_DATA=$(SPLIT_DATA_E) $(SPLIT_DATA_F)

SPLIT_DATA_F=$(patsubst %.$E,%.$F,$(SPLIT_DATA_E))

ALIGNED_EF_DATA=$(patsubst %/noclass.$E,%.out/training.$E-$F.A3,$(SPLIT_DATA_E))
ALIGNED_FE_DATA=$(patsubst %/noclass.$E,%.out/training.$F-$E.A3,$(SPLIT_DATA_E))

.SECONDARY:

all: $(SYM_DATA)
sym: $(SYM_DATA)
split: $(SPLIT_DATA)

#######################################
# Split data before word alignment:
#######################################

# lowercase, remove control characters, and remove classing
PRE=sed -e 's/[[:cntrl:]]/ /g'
CLASS=sed 's/\(\$$[^ ][^ ]*\)_([^ ][^ ]*)/\1/g'
NOCLASS=sed 's/\$$[^ ][^ ]*_(\([^ ][^ ]*\))/\1/g'

%/noclass.$F: %/clean.$F.gz
	zcat $< | $(NOCLASS) > $@
%/noclass.$E: %/clean.$E.gz
	zcat $< | $(NOCLASS) > $@

%/class.$F: %/clean.$E.gz
	zcat $(*F)/clean.$F.gz | $(CLASS) > $@
%/class.$E: %/clean.$E.gz
	zcat $(*F)/clean.$E.gz | $(CLASS) > $@

#%/clean.$E.gz %/clean.$F.gz: %/clean.$E.gz.log
#	echo temp target
%/clean.$E.gz: %/tok.$E.gz %/tok.$F.gz
	clean-corpus-n-gz.perl $(*F)/tok $E $F $(*F)/clean 1 125 >& $@

### Data in 6 splits (BBN approach):

split%/tok.$E.gz:
	mkdir split$(*F) || true
	cat `cat lists.$(ID)/list.clean lists.$(ID)/list.other$(*F) | perl -pe 's=^XXXX=$(DATA)='` | $(PRE) | gzip > $@
split%/tok.$F.gz:
	mkdir split$(*F) || true
	cat `cat lists.$(ID)/list.clean lists.$(ID)/list.other$(*F) | perl -pe 's=^XXXX=$(DATA)=' | sed 's/$Efiles/$Ffiles/g; s/\.$E\./.$F./g; s/\.$E$$/\.$F/g'` | $(PRE) | gzip > $@

good/tok.$E.gz:
	mkdir good || true
	cat `cat lists.$(ID)/list.clean | perl -pe 's=^XXXX=$(DATA)='` | $(PRE) | gzip > $@
good/tok.$F.gz:
	mkdir good || true
	cat `cat lists.$(ID)/list.clean | perl -pe 's=^XXXX=$(DATA)=' | sed 's/$Efiles/$Ffiles/g; s/\.$E\./.$F./g; s/\.$E$$/\.$F/g'` | $(PRE) | gzip > $@

#######################################
# Merge data after word alignment:
#######################################

giza.$E-$F/$E-$F.A3.final.gz: $(ALIGNED_EF_DATA)
	mkdir giza.$E-$F || true
	cat $+ | gzip > $@

giza.$F-$E/$F-$E.A3.final.gz: $(ALIGNED_FE_DATA)
	mkdir giza.$F-$E || true
	cat $+ | gzip > $@

#######################################
# Alignment symmetrization:
#######################################

model/aligned.$(ALIGN).reversed: giza.$E-$F/$E-$F.A3.final.gz giza.$F-$E/$F-$E.A3.final.gz
	mkdir model || true
	(time $(TRAIN) $(TRAIN_OPTIONS) --first-step 3 --last-step 3 > $@.log) 2> $@.err
	mv model/aligned.$(ALIGN) model/aligned.$(ALIGN).reversed

model/aligned.$(ALIGN): model/aligned.$(ALIGN).reversed
	cat $< | reverse-align > $@

model/aligned.$E: $(SPLIT_DATA_E)
	cat $+ > $@

model/aligned.$F: $(SPLIT_DATA_F)
	cat $+ > $@
