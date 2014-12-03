#!/usr/bin/perl
use strict;
use warnings;

die "Usage: $0 N < input-kbests > nbests-of-length-N" if @ARGV != 1;

my $n = shift @ARGV;
my $currid = -1;
my $count = undef;

while(<STDIN>) {
    m/^(\d+)(\s+.+\s+)$/ or die "Bad line: $_";
    my ($id,$remainder) = ($1,$2);
    if($id == $currid) {
	if($count < $n) {
	    print;
	    $count++;
	}
    }
    else {
	$currid = $id;
	$count = 1;
	print;
    }
}
