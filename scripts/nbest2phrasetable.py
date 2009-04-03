#!/usr/bin/python

# nbest2phrasetable
#
# author:  Daniel Cer
##############################

import sys, re, math

if len(sys.argv) != 3:
	print >>sys.stderr, \
		"Usage:\n\t%s (source phrases) (n-best list) > phrase_table" % sys.argv[0]
	sys.exit(-1)

source_filename = sys.argv[1]; nbest_filename = sys.argv[2]
source_fh = open(source_filename); nbest_fh = open(nbest_filename)

# read n-best lists
trans_opts = []
for nbest_entry in nbest_fh:
	fields = nbest_entry.split(" ||| ")
	id = int(fields[0]); trans = fields[1]; score = float(fields[3])
	if (id == len(trans_opts)): trans_opts.append([(trans, score)])
	elif (id < len(trans_opts)): trans_opts[id].append((trans, score))
	else:
		raise "Are the id's out of order id:%d > len(trans_opts):%d" % \
			(id,len(trans_opts))  

nbest_fh.close()

sourcelines = source_fh.readlines(); source_fh.close()

sorted_source_id_pairs = sorted(zip(sourcelines, range(0, len(sourcelines))))

for (source_phrase, id) in sorted_source_id_pairs:
	for trans_opt in trans_opts[id]:
		if not trans_opt[0]: continue
		print "%s ||| %s ||| %f" % (source_phrase.strip(), trans_opt[0], math.exp(trans_opt[1]))
