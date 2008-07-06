###########################################################################
# Makefile for GIZA.
# 
# Author: Michel Galley
###########################################################################

# Tell Makefile not to delete intermediate files.
.SECONDARY:

###########################################################################
# Program directories
###########################################################################

MOSES_SCRIPTS=/u/nlp/packages/moses-2008-05-18/trunk/scripts
TRAIN=$(MOSES_SCRIPTS)/training/train-factored-phrase-model.perl

###########################################################################
# Custom (e.g., language specific):
###########################################################################

### Data/language specific stuff:
# Put experiment-specific stuff here. Note that this file import is expected
# to define the following variables:
# F: source-language identifier (e.g., "zh")
# E: target-language identifier (e.g., "en")
# ALIGN: type of alignment (e.g., "grow-diag")
# TRAIN_OPTIONS: options to pass to Moses' training script
include Makefile.local

train: model/aligned.$(ALIGN)
giza: giza.$E-$F/$E-$F.A3.final.gz giza.$F-$E/$F-$E.A3.final.gz

###########################################################################
# Training:
###########################################################################

### (3) Alignment symmetrization:
model/aligned.$(ALIGN): giza.$E-$F/$E-$F.A3.final.gz giza.$F-$E/$F-$E.A3.final.gz
	mkdir -p model
	(time $(TRAIN) $(TRAIN_OPTIONS) --first-step 3 --last-step 3 > $@) 2> $@.err

### (2) Run GIZA:
giza.$E-$F/$E-$F.A3.final.gz giza.$F-$E/$F-$E.A3.final.gz: logs/step2
logs/step2: | corpus/$E.vcb corpus/$F.vcb corpus/$E.vcb.classes corpus/$F.vcb.classes corpus/$E-$F-int-train.snt corpus/$F-$E-int-train.snt
	(time $(TRAIN) $(TRAIN_OPTIONS) --first-step 2 --last-step 2 --parts 8 > $@) 2> $@.err
	rm -r -f giza.$E-$F/$E-$F.cooc giza.$F-$E/$F-$E.cooc corpus
	bzip2 logs/step2.err

### (1) Preprocessing:
corpus/$F.vcb corpus/$E.vcb corpus/$E-$F-int-train.snt corpus/$F-$E-int-train.snt corpus/$E.vcb.classes corpus/$F.vcb.classes: logs/step1
logs/step1: corpus.$E
	mkdir logs || true
	(time $(TRAIN) $(TRAIN_OPTIONS) --last-step 1 > $@) 2> $@.err
	rm -f corpus/$E.vcb.classes.cats corpus/$F.vcb.classes.cats

###########################################################################
# Corpus cleanup:
###########################################################################

corpus.$E: $(SOURCE_ROOT).$E $(SOURCE_ROOT).$F
	/user/mgalley/mt/mt-scripts/generic/clean-corpus-n.perl $(SOURCE_ROOT) $E $F corpus 1 99 2> $@.log
