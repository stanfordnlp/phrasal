#!/usr/bin/perl

use strict;
use warnings;

die "Usage: $0 count1 file1 [count2 file2 ...]"
    if scalar(@ARGV)==0 || scalar(@ARGV)%2 != 0;

while(scalar(@ARGV) != 0) {
    my $count = shift @ARGV;
    my $file = shift @ARGV;
    die "Expected count, but found string: $count" if $count !~ m/^\d+$/;
    die "Can't find file: $file" unless -e $file;
    for(my $i=0; $i<$count; $i++) {
	open IN, $file or die $!;
	while(<IN>) {
	    print;
	}
	close IN;
    }
}
