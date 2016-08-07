#!/usr/bin/perl
use strict;
use warnings;

die "Usage: $0 out_prefix sentences_per_split < nbests" if @ARGV != 2;

my $output_prefix = shift @ARGV;
my $num_per_split = shift @ARGV;
my $start_id_of_curr_file = -1;
my $fh=undef;

while(<STDIN>) {
    m/^(\d+)(\s+.+\s+)$/ or die "Bad line: $_";
    my ($id,$remainder) = ($1,$2);
    if($id % $num_per_split == 0 && $start_id_of_curr_file != $id) {
	$start_id_of_curr_file = $id;
	#open new file
	if(defined($fh)) { close $fh; }
	my $fname = sprintf("%s.%03d",$output_prefix, int($id/$num_per_split));
	open $fh, ">$fname" or die $!;
    }
    print $fh $_;
}
