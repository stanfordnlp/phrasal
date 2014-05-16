#!/usr/bin/perl
use strict;
use warnings;

die "Usage: $0 str_to_replace value_to_insert < input > output" if @ARGV != 2;

my $stringToFind = shift;
my $replacement = shift;

binmode(STDIN, ":utf8");
binmode(STDOUT, ":utf8");
binmode(STDERR, ":utf8");

my $replaced_already = 0;
while(<STDIN>) {
    if($replaced_already) { 
	print; 
    }
    else {
	my $ind = index($_, $stringToFind);
	if($ind == -1) {
	    print;
	}
	else {
	    my $before = substr($_, 0, $ind);
	    my $after  = substr($_, ($ind+length($stringToFind)));
	    my $newStr = $before . $replacement . $after;
	    print $newStr;
	    $replaced_already=1;
	}
    }
}
