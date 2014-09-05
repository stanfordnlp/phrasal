#!/u/nlp/bin/python2.7
#
# This script takes Moses formatted n-best lists augmented with the additional
# "very verbose" derivational column as input and then generates the XML 
# formatted n-best lists IBM wants subcontracting sites to submit for the 
# DARPA BOLT program. 
#
# If you generated n-best lists that don't include the "very verbose"
# derviational column, you can still generate IBM XML n-best lists using 
# make_IBM_XML_no_pp_scores.pl. However, the resulting n-best lists
# will have dummy values for the derivational scores
#
# Daniel Cer (danielcer@stanford.edu)
#
######################################################################

import sys
import codecs
import os
import re

import xml.sax.saxutils

if len(sys.argv) < 4:
  print >>sys.stderr, "Usage:\n\t%s [unzipped n-best list] [system name] [section name]" % os.path.basename(sys.argv[0])
  sys.exit(-1)
 
entries = {}
system_name = sys.argv[2]
set_name = sys.argv[3]
with codecs.open(sys.argv[1], 'r', 'utf-8') as fh:
  for line in fh:
    tokens = line.split(" ||| ")
    if tokens[0] in entries:
       entries[tokens[0]] += 1
    else:
       entries[tokens[0]] = 1

with codecs.open(sys.argv[1], 'r', 'utf-8') as ifh:
  with codecs.open("%s.xml"%sys.argv[3], 'w', 'utf-8') as ofh:
    print >>ofh, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
    print >>ofh, "<translations sysid=\"%s\" set=\"%s\">" % (system_name,set_name) 
    id = ""  
    for line in ifh:
      tokens = line.rstrip().split(" ||| ")
      if id != tokens[0]:
        if id != "":
          print >>ofh, "</nbest>" 
          print >>ofh, "</seg>"
        id = tokens[0]
        rank = 0
        #print "\r%s" %id
        print >>ofh, "<seg id=\"%s\">" % id
        print >>ofh, "<src>"
        src_toks = tokens[-2].split(" ")
        for tok_id in xrange(0, len(src_toks)):
          print >>ofh, " <tok id=\"%d\">%s</tok>" % \
            (tok_id, xml.sax.saxutils.escape(src_toks[tok_id]))
        print >>ofh, "</src>"
        print >>ofh, "<nbest count=\"%d\">" % entries[tokens[0]]
      print >>ofh, "<hyp rank=\"%d\" score=\"%s\">" % (rank, tokens[3])
      #print "tokens: ", tokens
      #for i in xrange(0, len(tokens)):
      #  print "%d:%s\n" % (i, tokens[i])
      #print "phrase_toks: ", tokens[-1] 
      
      #phrase_toks = tokens[-1].split(" |")
      phrase_toks = re.split(" \|(?=[\-0-9\.]+)", tokens[-1])
      for ptok in phrase_toks[1:]:
        sub_ptoks = ptok.split(" ")
        sub_ptoks[1] = sub_ptoks[1].replace("{", "").replace("}", "")
        print >>ofh, \
          "  <t score=\"%s\" srcidx=\"%s\" type=\"phrase\"> %s </t>" % \
          (sub_ptoks[0], sub_ptoks[1], xml.sax.saxutils.escape(" ".join(sub_ptoks[2:])))
      print >>ofh, "</hyp>"
      rank += 1 
    print >>ofh, "</nbest>" 
    print >>ofh, "</seg>"
    print >>ofh, "</translations>"



