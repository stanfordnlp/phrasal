use strict;
use warnings;

die "Usage: $0 old_nbest indices_of_new_1best output_nbest" if scalar(@ARGV)!=3;

my $in = shift @ARGV;
my $indices= shift @ARGV;
my $out = shift @ARGV;

open IN, $in or die $!;
open IND, $indices or die $!;
open OUT, ">$out" or die $!;

my $curr_nbest;
my $nbest_buffer = [];

my $sent_index=0;
while(<IND>) {
    chomp;
    my $index = $_;
    ($nbest_buffer, $curr_nbest) = &read_nbest(*IN, $nbest_buffer, $sent_index);
    if(scalar(@$curr_nbest) >= ($index+1)) {
	my $best = splice(@$curr_nbest, $index, 1);
	print OUT $best;
	foreach my $line (@$curr_nbest) {
	    print OUT $line;
	}
    }
    else {
	die "Expected at least ".($index+1)." translations in nbest for sentindex= $sent_index, but saw only ".scalar(@$curr_nbest);
    }
    $sent_index++;
}

die "Buffer not empty" if scalar(@$nbest_buffer)!=0;

close IND;
close IN;
close OUT;

sub read_nbest {
    my ($fh, $nbest_buffer, $sent_index) = @_;
    my @nbest= ();
    my $line;
    while($line = read_line($fh, $nbest_buffer)) {
	die "Unexpected undef line" if !defined($line);
	if($line =~ m/^\s*(\d+)\s+/) {
	    my $line_sent_index = $1;
	    if($line_sent_index == $sent_index) {
		push @nbest, $line;
	    }
	    else {
		unshift @$nbest_buffer, $line;
		last;
	    }
	}
	else {
	    die "Bad nbest line format: $line";
	}
    }

    return ($nbest_buffer, \@nbest);
}


sub read_line {
    my ($fh, $buffer) = @_;
    if(scalar(@$buffer)>0) {
	my $line = shift @$buffer;
	return $line;
    }
    else {
	my $line = <$fh>;
	return $line;
    }
}
