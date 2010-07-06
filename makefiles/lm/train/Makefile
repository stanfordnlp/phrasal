COUNT=ngram-count

LOWER=-tolower
LMOPTS=-order 5 -kndiscount -interpolate -debug 2 $(LOWER)

all: ulm
ulm: ../releases/$(LMID).$(FID).unk.lm.bin
lm: ../releases/$(LMID).$(FID).lm.bin
counts: counts/$(LMID).counts.gz
allcounts: $(LMS)

.SECONDARY:

#### Build composite LMs:

../releases/$(LMID).$(FID).lm.gz: counts/$(LMID).counts.gz
	make-big-lm -read $< -lm $@ $(LMOPTS) $(LMFILTER) -debug 2 -name counts/$(LMID) 2> logs/$(LMID).$(FID).log

../releases/$(LMID).$(FID).unk.lm.gz: counts/$(LMID).counts.gz
	make-big-lm -read $< -lm $@ $(LMOPTS) $(LMFILTER) -unk -debug 2 -name counts/$(LMID) 2> logs/$(LMID).$(FID).unk.log

counts/$(LMID).counts.gz: $(LMSRC)
	ngram-merge -write counts/$(LMID).counts.gz $+ > logs/$(LMID).log

## Build separate count files:

%.counts.gz: ../input/%.tok.gz
	(time $(COUNT) $(LMOPTS) -text $< -write $@ -sort) 2> logs/$@.log

## Create tokenized data:

../input/%.tok.gz:
	make -C ../input -f Makefile.$(LANG) $(*F).tok.gz

### Binarize LM:

%.lm.bin: %.lm.gz
	ngram -order 5 -lm $< -write-bin-lm $@ > $@.log


