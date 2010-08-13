include Makefile.local.$(ID)

SYM_DATA=model/aligned.$(ALIGN) model/aligned.$E model/aligned.$F
SPLIT_DATA=$(SPLIT_DATA_E) $(SPLIT_DATA_F)

SPLIT_DATA_F=$(patsubst %.$E,%.$F,$(SPLIT_DATA_E))

SPLIT_DATA_A=$(patsubst %/noclass.$E,%.out/training*.align,$(SPLIT_DATA_E))

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

%/noclass.$F: %/clean.$E.gz
	zcat $(*F)/clean.$F.gz | $(NOCLASS) > $@
%/noclass.$E: %/clean.$E.gz
	zcat $(*F)/clean.$E.gz | $(NOCLASS) > $@

%/class.$F: %/clean.$E.gz
	zcat $(*F)/clean.$F.gz | $(CLASS) > $@
%/class.$E: %/clean.$E.gz
	zcat $(*F)/clean.$E.gz | $(CLASS) > $@

%/clean.$E.gz: %/tok.$E.gz %/tok.$F.gz
	clean-corpus-n-gz.perl $(*F)/tok $E $F $(*F)/clean 1 125 >& $@.log

### Data in 6 splits (BBN approach):

split%/tok.$E.gz:
	mkdir split$(*F) || true
	cat `cat lists.$(ID)/list.clean lists.$(ID)/list.other$(*F) | perl -pe 's=^=$(DATA)/='` | $(PRE) | gzip > $@
split%/tok.$F.gz:
	mkdir split$(*F) || true
	cat `cat lists.$(ID)/list.clean lists.$(ID)/list.other$(*F) | perl -pe 's=^=$(DATA)/=' | sed 's/$Efiles/$Ffiles/g; s/\.$E\./.$F./g; s/\.$E$$/\.$F/g'` | $(PRE) | gzip > $@

good/tok.$E.gz:
	mkdir good || true
	cat `cat lists.$(ID)/list.clean | perl -pe 's=^=$(DATA)/='` | $(PRE) | gzip > $@
good/tok.$F.gz:
	mkdir good || true
	cat `cat lists.$(ID)/list.clean | perl -pe 's=^=$(DATA)/=' | sed 's/$Efiles/$Ffiles/g; s/\.$E\./.$F./g; s/\.$E$$/\.$F/g'` | $(PRE) | gzip > $@

#######################################
# Alignment symmetrization:
#######################################

model/aligned.$(ALIGN): $(SPLIT_DATA_A)
	cat $+ > $@

model/aligned.$E: $(SPLIT_DATA_E)
	cat $+ | tr '[A-Z]' '[a-z]' > $@

model/aligned.$F: $(SPLIT_DATA_F)
	cat $+ | tr '[A-Z]' '[a-z]'  > $@
