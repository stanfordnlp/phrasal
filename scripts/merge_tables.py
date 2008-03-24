#!/usr/bin/python

############################################################################
# merge_tables.pl
#
# author: Daniel Cer
############################################################################

import sys
import re
from itertools import izip


if len(sys.argv) != 3:
	print >>sys.stderr, "Usage:\n\t%s (phrase table) (additional table) > merged_table" % sys.argv[0]
	sys.exit(-1)

phrasetablefilename = sys.argv[1];
othertablefilename = sys.argv[2];

phrasetablefh  = open(phrasetablefilename);
othertablefh = open(othertablefilename);

fieldSpliter = re.compile(r'\s*\|\|\|\s*');

line = 1
for (pline,oline) in izip(phrasetablefh, othertablefh):
	pfields = fieldSpliter.split(pline.rstrip())
	ofields = fieldSpliter.split(oline.rstrip())
	if pfields[0:2] != ofields[0:2]:
		print >>sys.err, "Mismatch %s != %s" % (pfields[0:2], ofields[0:2]) 
		sys.exit(-1)

	print " ".join(pfields), ofields[-1]
	line += 1
