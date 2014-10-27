#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Project: Appraise evaluation system
 Author: Christian Federmann <cfedermann@gmail.com>

usage: python compute_agreement_scores.py [-h] [--processes PROCESSES]
                                          [--inter] [--intra] [--verbose]
                                          results-file

Computes agreement scores for the given results file in WMT format.

positional arguments:
  results-file          Comma-separated results file in WMT format.

optional arguments:
  -h, --help            Show this help message and exit.
  --processes PROCESSES
                        Sets the number of parallel processes.
  --inter               Compute inter-annotator agreement.
  --intra               Compute intra-annotator agreement.
  --verbose             Display additional information on kappa values.

"""
from __future__ import print_function, unicode_literals

import argparse
from collections import defaultdict
from csv import DictReader
from itertools import combinations
from multiprocessing import Pool, cpu_count

PARSER = argparse.ArgumentParser(description="Computes agreement scores " \
  "for the given results file in WMT format.")
PARSER.add_argument("results_file", type=file, metavar="results-file",
  help="Comma-separated results file in WMT format.")
PARSER.add_argument("--processes", action="store", default=cpu_count(),
  dest="processes", help="Sets the number of parallel processes.", type=int)
PARSER.add_argument("--inter", action="store_true", default=False,
  dest="inter_annotator_agreement", help="Compute inter-annotator agreement.")
PARSER.add_argument("--intra", action="store_true", default=False,
  dest="intra_annotator_agreement", help="Compute intra-annotator agreement.")
PARSER.add_argument("--verbose", action="store_true", default=False,
  dest="verbose", help="Display additional information on kappa values.")
PARSER.add_argument("--points", action="store_true", default=False,
  dest="points", help="Display total number of data points in output table.")
PARSER.add_argument("--nsystems", action='store', type=int, default=5, dest='num_systems',help='Number of systems in the input file.')


def compute_agreement_scores(data):
    """
    Computes agreement scores for the given data set.
    """
    # Make triples accessible by item id.
    _by_items = defaultdict(list)
    for _unused_coder_name, item, labels in data:
        _by_items[item].append(labels) # We only need the labels here.
    
    try:
        identical_cnt = 0
        comparable_cnt = 0
        ties_cnt = 0
        ties_total = 0
        
        for item_labels in _by_items.values():
            ties_total += len(item_labels)
            
            for individual_label in item_labels:
                if '=' in individual_label:
                    ties_cnt += 1
            
            # cfedermann: combinations() throws a ValueError if x does not
            # contain two or more elements when using Python 2.6;  hence we
            # check length of x before using it ;)
            if len(item_labels) > 1:
                for first_label, second_label in combinations(item_labels, 2):
                    if first_label == second_label:
                        identical_cnt += 1
                    comparable_cnt += 1
        
        return (identical_cnt, comparable_cnt, ties_cnt, ties_total)
    
    except:
        from traceback import print_exc
        print_exc()


if __name__ == "__main__":
    args = PARSER.parse_args()
    
    if not args.inter_annotator_agreement and \
      not args.intra_annotator_agreement:
        print("Defaulting to --inter mode.")
        args.inter_annotator_agreement = True

    num_systems = args.num_systems
    results_data = defaultdict(lambda: defaultdict(list))
    for i, row in enumerate(DictReader(args.results_file)):
        language_pair = '{0}-{1}'.format(row.get('srclang'), row.get('trglang'))
        segment_id = int(row.get('srcIndex'))
        judge_id = row.get('judgeId')
        
        # Filter out results where a user decided to "skip" ranking.
        systems = [row.get('system%dId' % (y+1)) for y in range(num_systems)]
        rankings = [int(x) for x in \
          [row.get('system%drank' % (y+1)) for y in range(num_systems)]]
        if all([x == -1 for x in rankings]):
            continue
        
        # Compute individual ranking decisions for this users.
        for a, b in combinations(range(num_systems), 2):
            _c = judge_id
            _i = '{0}.{1}.{2}'.format(segment_id, systems[a], systems[b])
            
            if rankings[a] < rankings[b]:
                _v = '{0}>{1}'.format(systems[a], systems[b])
            elif rankings[a] > rankings[b]:
                _v = '{0}<{1}'.format(systems[a], systems[b])
            else:
                _v = '{0}={1}'.format(systems[a], systems[b])
            
            # print('Appending', language_pair, segment_id, _c, _i, _v)
            
            # Append ranking decision in Artstein and Poesio format.
            results_data[language_pair][segment_id].append((_c, _i, _v))
    
    # We allow to use multi-processing.
    pool = Pool(processes=args.processes)
    print('Language pair        pA     pE     kappa  ',
      end='' if args.verbose or args.points else '\n')
    if args.points:
        print('Points   ', end='' if args.verbose else '\n')
    if args.verbose:
        print('(agree, comparable, ties, total)')
    
    # Use the following order to remain consistent with previous WMTs.
    language_pairs = ('Czech-English', 'English-Czech', 'German-English',
      'English-German', 'Spanish-English', 'English-Spanish',
      'French-English', 'English-French', 'Russian-English',
      'English-Russian', 'English-Hindi', 'Hindi-English', 'English-Arabic')
    
    for language_pair in language_pairs:
        segments_data = results_data[language_pair]
        scores = []
        handles = []
        
        for segment_id, _judgements in segments_data.items():
            # Collect judgements on a per-coder-level.
            _coders = defaultdict(list)
            for _c, _i, _l in _judgements:
                _coders[_c].append((_c, _i, _l))
            
            # Inter-annotator agreement is computed for all items.
            if args.inter_annotator_agreement:
                # Pool compute_agreement_scores() call and save handle.
                handle = pool.apply_async(compute_agreement_scores,
                  args=(_judgements,), callback=scores.append)
                handles.append(handle)
                continue
                
            # Intra-annotator agreement is solely computed on items for which
            # an annotator has generated two or more annotations.
            elif args.intra_annotator_agreement:
                # Check that we have at least one annotation item with two or
                # more annotations from the current coder.
                for _coder, _coder_judgements in _coders.items():
                    _items = defaultdict(list)
                    for _, _i, _l in _coder_judgements:
                        _items[_i].append(_l)
                    
                    # If no item has two or more annotations, skip coder.
                    if all([len(x)<2 for x in _items.values()]):
                        continue
                    
                    # We rename the judgements for the current coder s.t. we
                    # can compute intra-annotator agreement scores from
                    # inter-annotator agreement data ;)
                    renamed_judgements = []
                    for _i, _ls in _items.items():
                        for d in range(len(_ls)):
                            _c = '{0}-{1}'.format(_coder, d)
                            renamed_judgements.append((_c, _i, _ls[d]))
                    
                    # Pool compute_agreement_scores() call and save handle.
                    handle = pool.apply_async(compute_agreement_scores,
                      args=(renamed_judgements,), callback=scores.append)
                    handles.append(handle)
        
        # Block until all async computation processes are completed.
        while any([not x.ready() for x in handles]):
            continue
        
        # Compute average scores, normalising on per-item level.
        average_scores = []
        for i in range(4):
            average_scores.append(sum([x[i] for x in scores]))
        
        _identical = average_scores[0]
        _comparable = average_scores[1]
        _ties = average_scores[2]
        _ties_total = average_scores[3]
        
        # Compute p(A) probability.
        pA = _identical / float(_comparable or 1)
        
        # Compute p(E) empirically, based on the number of observed ties.
        pTies = _ties / float(_ties_total or 1)
        pNoTies = 1.0 - pTies
        pE = pTies**2 + (pNoTies/2.0)**2 + (pNoTies/2.0)**2
        
        # Compute kappa score.
        kappa = (pA - pE) / float(1.0 - pE)
        
        # Display results for current language pair.
        print('{0:>20} {1: 0.3f} {2: 0.3f} {3: 0.3f}'.format(language_pair,
          pA, pE, kappa), end='' if args.verbose or args.points else '\n')
        
        if args.points:
            print(' {0:>8}'.format(_comparable), end='' if args.verbose else '\n')
        
        if args.verbose:
            print(' {0:>8} {1:>8} {2:>8} {3:>8}'.format(*average_scores[:4]))
