#!/usr/bin/env python
#
# Implements various edit distance metrics
#

def levenshtein(s1, s2, is_token):
    """ Computes Levenshtein edit distance.

    Args:
      is_token -- compute token level distance
    Returns:
    Raises:
    """

    if is_token:
        s1 = s1.strip().split()
        s2 = s2.strip().split()

    # TODO: Don't need to store the whole chart
    # Just the last row
    chart = [[0]*len(s2) for x in xrange(len(s1))]
    chart[0] = range(len(s2))
    for i in xrange(len(s1)):
        chart[i][0] = i

    for j in xrange(1,len(s2)):
        for i in xrange(1,len(s1)):
            if s1[i] == s2[j]:
                chart[i][j] = chart[i-1][j-1]
            else:
                c_insert = chart[i-1][j] + 1
                c_delete = chart[i][j-1] + 1
                c_substitute = chart[i-1][j-1] + 1
                chart[i][j] = min(c_insert, c_delete, c_substitute)

    return chart[len(s1)-1][len(s2)-1]


def dameraulevenshtein(seq1, seq2, is_token):
    """Calculate the Damerau-Levenshtein distance between sequences.

    This distance is the number of additions, deletions, substitutions,
    and transpositions needed to transform the first sequence into the
    second. Although generally used with strings, any sequences of
    comparable objects will work.

    Transpositions are exchanges of *consecutive* characters; all other
    operations are self-explanatory.

    This implementation is O(N*M) time and O(M) space, for N and M the
    lengths of the two sequences.

    >>> dameraulevenshtein('ba', 'abc')
    2
    >>> dameraulevenshtein('fee', 'deed')
    2

    It works with arbitrary sequences too:
    >>> dameraulevenshtein('abcd', ['b', 'a', 'c', 'd', 'e'])
    2
    """

    if is_token:
        seq1 = seq1.strip().split()
        seq2 = seq2.strip().split()
    
    # codesnippet:D0DE4716-B6E6-4161-9219-2903BF8F547F
    # Conceptually, this is based on a len(seq1) + 1 * len(seq2) + 1 matrix.
    # However, only the current and two previous rows are needed at once,
    # so we only store those.
    oneago = None
    thisrow = range(1, len(seq2) + 1) + [0]
    for x in xrange(len(seq1)):
        # Python lists wrap around for negative indices, so put the
        # leftmost column at the *end* of the list. This matches with
        # the zero-indexed strings and saves extra calculation.
        twoago, oneago, thisrow = oneago, thisrow, [0] * len(seq2) + [x + 1]
        for y in xrange(len(seq2)):
            delcost = oneago[y] + 1
            addcost = thisrow[y - 1] + 1
            subcost = oneago[y - 1] + (seq1[x] != seq2[y])
            thisrow[y] = min(delcost, addcost, subcost)
            # This block deals with transpositions
            if (x > 0 and y > 0 and seq1[x] == seq2[y - 1]
                and seq1[x-1] == seq2[y] and seq1[x] != seq2[y]):
                thisrow[y] = min(thisrow[y], twoago[y - 2] + 1)
    return thisrow[len(seq2) - 1]

