#!/usr/bin/perl

use strict;
use warnings;

die "Usage: $0 <nbest > trans" if scalar(@ARGV) != 0;

my $prev_id = -1;

while(<STDIN>) {
    if(m/^(\d+)\s+/) {
	my $id = $1;
	if($id != $prev_id) {
	    &printTrans($_);
	    $prev_id = $id;
	}
    }
    else {
	die "Malformed line: $_";
    }
}


sub printTrans {
    my ($line) = @_;
    if($line =~ m/^.*?\|\|\|\s*(.*?)\s*\|\|\|.*/) {
	my $trans = $1;
	print $trans."\n";
    }
    else {
	die "Malformed line: $line";
    }
}
