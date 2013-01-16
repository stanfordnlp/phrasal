#!/usr/bin/python2.7

import sys
import codecs

if len(sys.argv) != 4:
   print >>sys.stderr, "Usage:\n\t%s (eval scored nbest) (out feature index) (out liblinear train)\n" % sys.argv[0]
   sys.exit(-1)

nbestFn = sys.argv[1]
indexFn = sys.argv[2]
trainFh = sys.argv[3]

with codecs.open(nbestFn, "r", "utf-8") as nbestFh:
  with codecs.open(indexFn, "w", "utf-8") as indexFh:
    index = {};
    with codecs.open(trainFh, "w", "utf-8") as trainFh:
      for line in nbestFh:
        fields = line.split(" ||| ")
        transId = int(fields[0])
        featureToks = fields[-3].split(" ")
        evalScore = float(fields[-1])*100
        print >>trainFh, evalScore,

        features = []

        # segBiasName = " ||| %d ||| " % transId

        #if segBiasName in index:  
        #   featId = index[segBiasName]
        #else:
        #   featId = len(index)+1
        #   index[segBiasName] = featId

        #features.append((featId, 1.0))

        for i in xrange(0, len(featureToks), 2):
          name = featureToks[i][:-1]
          value = float(featureToks[i+1]);
          if name in index:  
             featId = index[name]
          else:
             featId = len(index)+1
             index[name] = featId
             print >>indexFh, "%d\t%s" % (featId, name)
          features.append((featId, value))
       
        features.sort()
        for feature in features: 
          print >>trainFh, "%d:%f" % feature,
        print >>trainFh

