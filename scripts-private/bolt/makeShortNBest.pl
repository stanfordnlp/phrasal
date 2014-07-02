#!/usr/bin/perl
use strict;
use warnings;

die "Usage: $0 input output" if @ARGV != 2;

my $in = shift;
my $out = shift;

open IN, $in or die $!;
open OUT, ">$out" or die $!;

while(<IN>) {
    my @tokens = split /\|\|\|/, $_;
    print OUT join("|||", @tokens[0..3])."\n";
}

close IN;
close OUT;
