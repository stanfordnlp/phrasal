#!/u/nlp/bin/python2.7
# phrasal_very_verbose_to_xml.py
# Daniel Cer (danielcer@stanford.edu)

import sys
import codecs

import xml.sax.saxutils


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
        print >>ofh, "<seg id=\"%s\">" % id
        print >>ofh, "<src>"
        src_toks = tokens[5].split(" ")
        for tok_id in xrange(0, len(src_toks)):
          print >>ofh, " <tok id=\"%d\">%s</tok>" % \
            (tok_id, xml.sax.saxutils.escape(src_toks[tok_id]))
        print >>ofh, "</src>"
        print >>ofh, "<nbest count=\"%d\">" % entries[tokens[0]]
      print >>ofh, "<hyp rank=\"%d\" score=\"%s\">" % (rank, tokens[3])
      phrase_toks = tokens[6].split(" |")
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


