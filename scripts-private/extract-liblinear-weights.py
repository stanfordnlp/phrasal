#!/usr/bin/python2.7

import sys
import codecs

if len(sys.argv) != 4:
   print >>sys.stderr, "Usage:\n\t%s (feature index) (liblinear model) (output phrasal weights)\n" % sys.argv[0]
   sys.exit(-1)

indexFn = sys.argv[1]
liblinearModelFn = sys.argv[2]
decoderWeightsFn = sys.argv[3]

indexRev = {}
with codecs.open(indexFn, "r", "utf-8") as fh:
  for line in fh:
    toks = line.rstrip().split("\t")
    indexId = int(toks[0])
    name = toks[1]
    indexRev[indexId] = name

with codecs.open(liblinearModelFn, "r", "utf-8") as liblinearModelFh:
  with codecs.open(decoderWeightsFn, "w", "utf-8") as decoderWeightsFh:
    in_header = True
    index = 1 
    for line in liblinearModelFh:
      line = line.rstrip()
      if line == "w":
        in_header = False
        continue
      if in_header:
        continue
      if index in indexRev: 
         name = indexRev[index]
         print >>decoderWeightsFh, "%s %s" % (name, line)
      index += 1 
