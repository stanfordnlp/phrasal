use strict;
use warnings;

die "Usage: $0 ref1 [ref2...] < transfile" if @ARGV == 0;

my @refStats;
foreach my $refFile (@ARGV) {
    open IN, $refFile;
    my $stats = &get_stats(*IN);
    close IN;
    push @refStats, $stats;
}

my $mergedRefStats = &merge_stats(@refStats);
my $transStats = &get_stats(*STDIN);

die "Mismatch" unless $transStats->{num_sentences} == $refStats[0]->{num_sentences};

my $transToRefRatio = $transStats->{average_length} / $mergedRefStats->{average_length};
printf("%.2f %.2f %.2f\n", 
       $transStats->{average_length},
       $mergedRefStats->{average_length},
       $transToRefRatio);

sub get_stats {
    my ($fh) = @_;
    my $stats = {
	total_length => 0,
	num_sentences => 0,
    };
    while(<$fh>) {
	s/\s+$//;
	s/^\s+//;
	my @tokens = split /\s+/, $_;
	my $len = scalar(@tokens);
	$stats->{total_length} += $len;
	$stats->{num_sentences} += 1;
    }
    $stats->{average_length} = $stats->{total_length} / $stats->{num_sentences};
    return $stats;
}

sub merge_stats {
    my @stats= @_;
    my $merged_stats = {
	total_length => 0,
	num_sentences => 0,
    };
    foreach my $stat (@stats) {
	die "Mismatch" if $stat->{num_sentences} != $stats[0]->{num_sentences};
	$merged_stats->{total_length} += $stat->{total_length};	
	$merged_stats->{num_sentences} += $stat->{num_sentences};
    }
    $merged_stats->{average_length} = 
	$merged_stats->{total_length} / $merged_stats->{num_sentences};
    return $merged_stats;    
}
