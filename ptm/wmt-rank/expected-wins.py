#!/usr/bin/env python
#
# Compute expected wins for pe vs. imt
# given the pairwise data.
#
# Author: Spence Green
#
import sys
import os
from os.path import basename,splitext
from csv import DictReader
from collections import defaultdict,Counter
import random
stderr = lambda x : sys.stderr.write(x + os.linesep)

args = sys.argv[1:]
if len(args) < 2:
    stderr('Usage: python %s filename props [props]' % (basename(sys.argv[0])))
    sys.exit(-1)

judge_files = args[1:]
seg2sys2interface = defaultdict(dict)
for filename in judge_files:
    with open(filename) as infile:
        subject,_ = splitext(basename(filename))
        for i,line in enumerate(infile):
            domain,valid = line.strip().split()
            label,interface = domain.split('=')
            seg2sys2interface[i][subject] = interface
    
# Semantics: first coordinate wins against second coordinate
wins = defaultdict(Counter)
n_eq = 0
n_ratings = 0
rframe = open(args[0] + '.frame','w') 
rframe.write('%s,%s,%s,%s,%s,%s,%s,%s%s' % ('response', 'ui_main', 'ui_opp', 'judgeId', 'segId', 'user_main', 'user_opp','index',os.linesep))
with open(args[0]) as infile:
    pair_index = 0
    for row in DictReader(infile):
        n_ratings += 1
        sys1 = row.get('system1')
        sys2 = row.get('system2')
        ranking = row.get('cmp')
        judge = row.get('judgeId')
        seg_id = int(row.get('segmentId'))
        # Convert to interface condition
        interface1 = seg2sys2interface[seg_id][sys1]
        interface2 = seg2sys2interface[seg_id][sys2]
        if ranking == '>':
            wins[interface2][interface1] += 1
        elif ranking == '<':
            wins[interface1][interface2] += 1
        else:
            n_eq += 1
        if interface1 != interface2:
#            if ranking == '=':
#                response = 0
#                users = [(sys1,interface1),(sys2,interface2)]
#                random.shuffle(users)
#                user_main = users[0][0]
#                ui_main = users[0][1]
#                user_opp = users[1][0]
#                ui_opp = users[1][1]
#                rframe.write('%d,%s,%s,%s,%d,%s,%s%s' % (response,ui_main,ui_opp,judge,seg_id,user_main,user_opp,os.linesep))
#            else:
            if ranking != '=':
                # First data point
                response = 1
                user_main = sys1 if ranking == '<' else sys2
                user_opp = sys2 if ranking == '<'  else sys1
                ui_main = interface1 if ranking == '<' else interface2
                ui_opp = interface2 if ranking == '<' else interface1
                rframe.write('%d,%s,%s,%s,%d,%s,%s,%d%s' % (response,ui_main,ui_opp,judge,seg_id,user_main,user_opp,pair_index,os.linesep))
                
                # Second data point
                response = 0
                user_main = sys2 if ranking == '<' else sys1
                user_opp = sys1 if ranking == '<'  else sys2
                ui_main = interface2 if ranking == '<' else interface1
                ui_opp = interface1 if ranking == '<' else interface2
                rframe.write('%d,%s,%s,%s,%d,%s,%s,%d%s' % (response,ui_main,ui_opp,judge,seg_id,user_main,user_opp,pair_index,os.linesep))
                pair_index += 1
                
rframe.close()
conditions = list(wins.keys())
assert len(conditions) == 2
condition_a = conditions[0]
condition_b = conditions[1]
wins_a = wins[condition_a][condition_b]
wins_b = wins[condition_b][condition_a]
denom = wins_a + wins_b

ew_a = float(wins_a) / float(denom)
print 'Expected wins %s > %s: %.3f' % (condition_a, condition_b, ew_a)
ew_b = float(wins_b) / float(denom)
print 'Expected wins %s > %s: %.3f' % (condition_b, condition_a, ew_b)


#wins_imt = wins['imt']['pe']
#denom = wins['imt']['pe'] + wins['pe']['imt']
#print wins['imt']['imt'],wins['imt']['pe']
#print wins['pe']['pe'],wins['pe']['imt']
#ew_imt = float(wins_imt) / float(denom)
#print 'Expected wins imt > pe: %.3f (%d/%d)' % (ew_imt, wins_imt, denom)
#print 'Num ties: %d / %d (%.2f%%)' % (n_eq, n_ratings, 100.0 * float(n_eq) / float(n_ratings))
    
