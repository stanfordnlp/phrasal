#!/usr/bin/env python
#
# Converts the output of ans2csv.sh to a csv with a global ranking.
#
#
import sys
import csv
import os
from collections import namedtuple,defaultdict,Counter
from argparse import ArgumentParser

# Input format
AnswerRow = namedtuple('AnswerRow', 'srclang,trglang,srcIndex,documentId,segmentId,judgeId,system1Number,system1Id,system2Number,system2Id,rank')

# Output format
RankRow = namedtuple('RankRow', 'src_id user_id rank')

class Ranking:
    def __init__(self, src_id, judge_id, sys1_id, sys2_id, rank):
        self.src_id = int(src_id)
        self.judge_id = judge_id
        self.sys1_id = int(sys1_id)
        self.sys2_id = int(sys2_id)
        self.rank = int(rank)

    def get_template_str(self):
        if self.sys1_id < self.sys2_id:
            return '%d-%d-%d' % (self.src_id,
                                 self.sys1_id,
                                 self. sys2_id)
        else:
            return '%d-%d-%d' % (self.src_id,
                                 self.sys2_id,
                                 self.sys1_id)
        
    def __str__(self):
        return '[%s: src:%d sys1:%d sys2:%d rank:%d]' % (self.judge_id,
                                                         self.src_id,
                                                         self.sys1_id,
                                                         self.sys2_id,
                                                         self.rank)

# Ranking -> to an index
g_rank_index = {}
def get_ranking_id(ranking):
    global g_rank_index
    rank_str = ranking.get_template_str()
    if g_rank_index.has_key(rank_str):
        return g_rank_index[rank_str]
    else:
        g_rank_index[rank_str] = len(g_rank_index.keys())
        return g_rank_index[rank_str]

    
def parse_answer_file(answer_file, src_lang, tgt_lang):
    """
    Returns the following data structures:
      src -> list of rankings
      judgeId -> list of rankings
      rankingId -> list of rankings

    Args:
    Returns:
    Raises:
    """
    src2rank = defaultdict(list)
    judge2rank = defaultdict(list)
    rank_dict = defaultdict(list)
    
    with open(answer_file) as infile:
        seen_header = False
        n_rows = 0
        for row in map(AnswerRow._make, csv.reader(infile)):
            if not seen_header:
                seen_header = True
                continue
            if not (row.srclang == src_lang and row.trglang == tgt_lang):
                continue
            n_rows += 1
            ranking = Ranking(row.srcIndex,
                              row.judgeId,
                              row.system1Id,
                              row.system2Id,
                              row.rank)
            ranking_id = get_ranking_id(ranking)
            src2rank[ranking.src_id].append(ranking)
            judge2rank[ranking.judge_id].append(ranking)
            rank_dict[ranking_id].append(ranking)
    sys.stderr.write('Read: %d rows%s' % (n_rows, os.linesep))
    return src2rank,judge2rank,rank_dict


def weight_judges(judge2rank, rank_dict, do_non_expert):
    weights = {}
    if do_non_expert:
        sys.stderr.write('NOT YET IMPLEMENTED')
    else:
        for judge_id in judge2rank.keys():
            weights[judge_id] = 1.0
    return weights


def do_sort(n_tgts, tgt_cmp):
    """ Performs a quicksort on the list of target ids
    using the supplied comparison function.

    Args:
    Returns:
    Raises:
    """
    id_list = sorted(range(n_tgts), cmp=tgt_cmp)

    # Set ties by running over the sorted list
    tie_with_prev = [False]*n_tgts
    for i in xrange(len(id_list)):
        if i < len(id_list)-1:
            if tgt_cmp(id_list[i], id_list[i+1]) == 0:
                # Two items are equal
                tie_with_prev[i+1] = True
            
    return id_list,tie_with_prev


def make_rows(id_list, tie_with_prev):
    """ Converts the sorted id list to a list
    of RankedRow namedtuples for output.

    Args:
    Returns:
    Raises:
    """
    row_list = []
    rank = 0
    for i,sys_id in enumerate(id_list):
        if not tie_with_prev[i]:
            rank = i + 1
        row_list.append(RankRow(src_id=0,
                                user_id=sys_id,
                                rank=rank))
    return row_list


def sort_tgts(ranking_list, judge_weights):
    """ Use the ranking list to build a total ordering
    of the ranked translations (indicated by Ranking.sys{1,2}_id

    Args:
    Returns:
    Raises:
    """
    # Get the list of unique translation ids
    tgt_ids = []
    for ranking in ranking_list:
        tgt_ids.append(ranking.sys1_id)
        tgt_ids.append(ranking.sys2_id)
    tgt_ids = set(tgt_ids)

    # Build integer -> tgt_id map
    id2tgt_dict = {}
    tgt2id_dict = {}
    for i,tgt_id in enumerate(tgt_ids):
        id2tgt_dict[i] = tgt_id
        tgt2id_dict[tgt_id] = i

    #print id2tgt_dict
    
    # Build the comparison chart
    # TODO: This chart implements a mean,
    # which means that an equality pairwise decision can
    # be computed when no humans actually chose equality as
    # the ranking. This probably isn't right. Implement other tie-breaking
    # methods?
    cmp_chart = []
    eq_counter = Counter()
    for i in xrange(len(tgt_ids)):
        cmp_chart.append([0.0]*len(tgt_ids))
    for ranking in ranking_list:
        weight = judge_weights[ranking.judge_id]
        id_sys1 = tgt2id_dict[ranking.sys1_id]
        id_sys2 = tgt2id_dict[ranking.sys2_id]
        id_tuple = tuple(sorted([id_sys1, id_sys2]))
        if ranking.rank == 1:
            cmp_chart[id_sys1][id_sys2] += -weight 
            cmp_chart[id_sys2][id_sys1] += weight
            eq_counter[id_tuple] -= weight
        elif ranking.rank == 2:
            cmp_chart[id_sys1][id_sys2] += weight
            cmp_chart[id_sys2][id_sys1] += -weight
            eq_counter[id_tuple] -= weight
        elif ranking.rank == 11:
            # Equality: special case
            # If the majority chooses equality, then we
            # say that the pairs are equal.
            eq_counter[id_tuple] += weight
        else:
            raise RuntimeError('Unknown ranking: ' + str(ranking.rank))
    # Now set equality relations
    for key in eq_counter.keys():
        if eq_counter[key] > 0.0:
            id_sys1 = key[0]
            id_sys2 = key[1]
            cmp_chart[id_sys1][id_sys2] = 0
            cmp_chart[id_sys2][id_sys1] = 0
    # Finally, convert all cells to int for comparison
    for i in xrange(len(tgt_ids)):
        for j in xrange(len(tgt_ids)):
            if cmp_chart[i][j] > 0.0:
                cmp_chart[i][j] = 1
            elif cmp_chart[i][j] < 0.0:
                cmp_chart[i][j] = -1
            else:
                cmp_chart[i][j] = 0

    #print cmp_chart
    
    # Now sort the indices using quicksort
    id_list,tie_with_prev = do_sort(len(tgt_ids),
                                    lambda x,y:cmp_chart[x][y])
    # Map back to system ids
    id_list = map(lambda x:id2tgt_dict[x], id_list)

    return make_rows(id_list, tie_with_prev)


def rank(answer_file, src_lang, tgt_lang, do_non_expert):
    """ Reads the input file and applies ranking. Results
    are printed to stdout.

    Args:
    Returns:
    Raises:
    """
    # Build data structures
    src2rank, judge2rank, rank_dict = parse_answer_file(answer_file,
                                                        src_lang,
                                                        tgt_lang)
    # Setup weighting of judges
    judge_weights = weight_judges(judge2rank, rank_dict, do_non_expert)
    
    # Iterate over each source sentence and rank
    # Write to stdout
    write_header = True
    csv_out = csv.writer(sys.stdout)
    for src_id in sorted(src2rank.keys()):
        row_list = sort_tgts(src2rank[src_id], judge_weights)
        for row in row_list:
            if write_header:
                csv_out.writerow(list(row._fields))
                write_header = False
            # sort_tgts does not set the src_id field
            row = row._replace(src_id=src_id)
            columns = [x for x in row._asdict().itervalues()]
            csv_out.writerow(columns)


def main():
    desc='Converts the output of ans2csv.sh to a global ranking.'
    parser = ArgumentParser(description=desc)
    parser.add_argument('-n','--non_expert',
                        dest='do_weight',
                        action='store_true',
                        default=False,
                        help='Apply non-expert weighting a la CCB (2009).')
    parser.add_argument('src_lang',
                        help='Source language to rank')
    parser.add_argument('tgt_lang',
                        help='Target language to rank')
    parser.add_argument('answer_csv',
                        help='Output of ans2csv.sh')
    args = parser.parse_args()

    rank(args.answer_csv, args.src_lang, args.tgt_lang, args.do_weight)

if __name__ == '__main__':
    main()
