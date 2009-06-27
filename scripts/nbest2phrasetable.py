#!/usr/bin/python

# nbest2phrasetable
#
# author:  Daniel Cer
# author:  Spence Green
##############################

import sys, re, math, codecs

if len(sys.argv) != 4:
	print >>sys.stderr, \
		"Usage:\n\t%s (source phrases) (n-best list) (pt-name)" % sys.argv[0]
	sys.exit(-1)

source_filename = sys.argv[1]
nbest_filename = sys.argv[2]
pt_filename = sys.argv[3]

source_fh = codecs.open(source_filename,encoding='utf-8')
nbest_fh = codecs.open(nbest_filename,encoding='utf-8')

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

sourcelines = source_fh.readlines()
source_fh.close()
sorted_source_id_pairs = sorted(zip(sourcelines, range(0, len(sourcelines))))

#ar_stripper = re.compile('^\d+$|[\'\",.!?:;\[\]-]',re.U)
en_stripper = re.compile('^\d+$|^\.|\.$|\s\.\s|\'(?!s)|[\",!?:;\[\]]')

p_any_ar = re.compile(u'[\u0600-\u06FF]+',re.U)
p_all_en = re.compile(u'[\u0000-\u007F\s]+$',re.U)

NEW_PT = codecs.open(pt_filename,'w',encoding='utf-8')
for (source_phrase, id) in sorted_source_id_pairs:
	for trans_opt in trans_opts[id]:
		if not trans_opt[0]:
			continue

		score = math.exp(trans_opt[1])
		if score < 1e-08:
			continue

		source_phrase = source_phrase.strip()
		m = p_all_en.match(source_phrase)
		if m or source_phrase == '' or len(source_phrase.split()) > 5:
			continue

		m = p_any_ar.search(trans_opt[0])
		en_trans = trans_opt[0].strip()
		if m or en_trans=='':
			continue

		NEW_PT.write('%s ||| %s ||| I-I ||| I-I ||| %e \n' % (source_phrase, en_trans, score))

NEW_PT.close()
