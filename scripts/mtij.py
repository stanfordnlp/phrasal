#!/usr/bin/env python
# author: Daniel Cer
# Usage: $0 (n-best list) (source text) (weights) (engine)> (xml n-best list)
import sys; from itertools import izip; import re
f = open(sys.argv[1]); k = (open(sys.argv[2])).readlines(); c= []; oi=-1
for l in f: 
 i=int(l[0:l.find("|")]) 
 if oi!=i: c.append([])
 c[i].append(l)

print "<!DOCTYPE translations SYSTEM \"translations.dtd\">\n<translations \
weights=\"%s\">" % sys.argv[3] 
for (v, y, q) in izip(c, k, xrange(0, len(c))):
 t=re.split("\\s+", y.rstrip())
 print "<tr engine=\"%s\">" % sys.argv[4]
 print "<s id=\"%d\"> %s</s>"%(q, ''.join(["<w> %s </w>"%o for o in t]))
 for (j,z) in izip(v, xrange(0, len(v))): 
  x=re.split("\\s+\\|+\\s+",j); d=re.split("\\s+",x[1]);e=re.split("\\s+",\
  x[-1].strip())
  print "<hyp r=\"%d\" c=\"0\"><t>"%z,
  for eu in e: 
   ev=eu[eu.find("=")+1:].split("-"); eu = eu[:eu.find("=")]
   print "<p al=\"%s\">%s </p>"%(eu,''.join(" %s"%s for s in d[int(ev[0]):(\
   len(ev)==2 and int(ev[1])+1 or int(ev[0])+1)])),
  d=re.split("\\s+", x[2]); ds=""
  print "</t><sco> %s </sco></hyp>"%' '.join([dsi for dsi in d if \
  dsi.find(":")==-1])
 print "</tr>"
print "</translations>" 
