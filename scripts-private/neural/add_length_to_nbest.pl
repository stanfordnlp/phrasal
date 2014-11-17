#!/usr/bin/perl

use strict;
use warnings;

die "Usage: $0 <nbest >nbest_with_len" if scalar(@ARGV !=0);

while(<STDIN>) {
    if(m/^(.*?\|\|\|)(.+?)(\|\|\|\s+)(.*\s*)/) {
	my ($left, $text, $middle, $right) = ($1,$2,$3,$4);
	my $count = &count($text);
	printf ("%s%s%s len: %d %s", $left, $text, $middle, $count, $right);
    }
    else {
	die "Bad line: $_";
    }
}

sub count {
    my ($text) = @_;
    $text =~ s/^\s+//;
    $text =~ s/\s+$//;
    my @words = split /\s+/, $text;
    my $count = scalar(@words);
    return $count;
}
