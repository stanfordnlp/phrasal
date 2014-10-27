#!/usr/bin/env python
import sys
import os
from os.path import basename
from csv import DictReader
from collections import defaultdict
from itertools import combinations


"""
Responsible for reading in WMT-formatted CSV files, and outputting pairwise ranks.
"""

def get_pairranks(rankList):
    ### Reading a list of rank and return a list of pairwise comparison {>, <, =}.
    result = []
    for pair in combinations(rankList, 2):
        if pair[0] == pair[1]:
            result.append('=')
        elif pair[0] > pair[1]:
            result.append('>')
        else:
            result.append('<')
    return result

def get_pairwise(names, ranks):
    """Takes a ranking task (list of systems, list of ranks) and returns the set of pairwise rankings."""
    pairname = [n for n in combinations(names, 2)]
    pairwise = get_pairranks(ranks)
    pair_result = []
    for pn, pw in zip(pairname, pairwise):
        pair_result.append((pn[0], pn[1], pw))
    return pair_result

def rankings(fh, num_systems):
    """Reads in a CSV file fh, returning each 5-way ranking."""

    ### Parsing csv file and return system names and rank(1-5) for each sentence
    sent_sys_rank = defaultdict(list)
    for i,row in enumerate(DictReader(fh)):
        sentID = int(row.get('segmentId'))
        judgeID = row.get('judgeId')
        systems = []
        ranks = []
        for num in xrange(1, num_systems+1):
            systems.append(row.get('system%dId' % num))
            ranks.append(int(row.get('system%drank' % num)))

        if not -1 in ranks:
            yield (systems, ranks, sentID, judgeID)

def pairs(fh, num_systems):
    """Reads in a CSV file fh, returning pairwise judgments."""
    for systems, ranks, sentID, judgeID in rankings(fh, num_systems):
        for pair in get_pairwise(systems, ranks):
            yield pair + (sentID, judgeID)
            
def parse_csv(fh):
    ### Parsing csv file and return system names and rank(1-5) for each sentence
    all_systems = []
    sent_sys_rank = defaultdict(list)
    for i,row in enumerate(DictReader(fh)):
        sentID = int(row.get('segmentId'))
        systems = []
        ranks = []
        for num in range(1, 6):
            if row.get('system%dId' % num) in all_systems:
                pass
            else:
                all_systems.append(row.get('system%dId' % num))
            systems.append(row.get('system%dId' % num))
            ranks.append(int(row.get('system%drank' % num)))
        if -1 in ranks:
            pass
        else:
            sent_sys_rank[sentID].append({'systems': systems, 'ranks': ranks})
    return all_systems, sent_sys_rank

def main():
    """
    """
    args = sys.argv[1:]
    if len(args) != 2:
        sys.stderr.write('Usage: python %s num_systems csv_file%s' % (basename(sys.argv[0]), os.linesep))
        sys.exit(-1)
    sys.stderr.write('Converting 1-indexing to 0-indexing' + os.linesep)
    print '%s,%s,%s,%s,%s' % ('system1', 'system2', 'cmp', 'segmentId', 'judgeId')
    num_systems = int(args[0])
    with open(args[1]) as infile:
        for pair in pairs(infile, num_systems):
            sys1 = pair[0]
            sys2 = pair[1]
            compare = pair[2]
            # Convert to 0-indexing
            seg_id = int(pair[3]) - 1
            judge_id = pair[4]
            print '%s,%s,%s,%d,%s' % (sys1,sys2,compare,seg_id,judge_id)
            
if __name__ == '__main__':
    main()
