#!/usr/bin/env python
import sys
import csv
from collections import namedtuple,Counter

Ranking = namedtuple('Ranking', 'srclang,trglang,srcIndex,documentId,segmentId,judgeId,system1Number,system1Id,system2Number,system2Id,rank')

args = sys.argv[1:]
user_list = args[0].split(',')
rank_file = args[1]

# Insert all keys
rank_counter = Counter()
for i in xrange(len(user_list)):
    for j in xrange(len(user_list)):
        if i == j:
            continue
        u = int(user_list[i])
        v = int(user_list[j])
        edge = sorted([u,v])
        for k in xrange(27):
            edge2 = list(edge)
            edge2.append(k)
            edge2 = tuple(edge2)
            rank_counter[edge2] = 0

with open(rank_file) as infile:
    r = csv.reader(infile)
    r.next()
    for row in map(Ranking._make, r):
        u = int(row.system1Id)
        v = int(row.system2Id)
        src_id = int(row.srcIndex)
        edge = sorted([u,v])
        edge.append(src_id)
        edge = tuple(edge)
        rank_counter[edge] += 1

print 'src_id\tsystems\tcount'
for (u,v,src_id),count in rank_counter.iteritems():
    if count != 3:
        print '%d\t%d-%d\t%d' % (src_id,u,v,count)

