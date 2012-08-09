#
#
# Various statistical functions
#
from scipy import stats


def paired_diff_t_test(a,b):
    assert len(a) == len(b)
    diffs = [a[i]-b[i] for i in xrange(len(a))]
    x_d = stats.tmean(diffs)
    s_d = stats.tstd(diffs)
    n = len(diffs)
    dof = n-1
    t_d = x_d / (s_d / n)

    # sf() is the survival function (1-cdf)
    pval = stats.t.sf(abs(t_d), dof)

    print
    print 't-statistic:\t%.4f' % (t_d)
    print 'dof:\t%d' % (dof)
    print 'p-value:\t%.4f' % (pval)
