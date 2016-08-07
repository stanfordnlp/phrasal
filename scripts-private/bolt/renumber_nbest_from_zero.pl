#!/usr/bin/perl
use strict;
use warnings;

die "Usage: $0 < nbests_in_order > nbests_with_renumbered_ids" if @ARGV != 0;

my $current_input_id = -1;
my $current_output_id = undef;

while(<STDIN>) {
    m/^(\d+)(\s+.+\s+)$/ or die "Bad line: $_";
    my ($id,$remainder) = ($1,$2);
    if($id == $current_input_id) {
	# no update
    }
    else {
	$current_input_id = $id;
	if(defined($current_output_id)) {
	    $current_output_id++;
	}
	else {
	    $current_output_id=0;
	}
    }
    print "${current_output_id}${remainder}";
}
