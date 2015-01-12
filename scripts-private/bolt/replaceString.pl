#!/usr/bin/perl
use strict;
use warnings;
use Getopt::Long;

my $replace_all = '';
GetOptions ('all' => \$replace_all);

die "Usage: $0 [--all] str_to_replace1 value_to_insert1 [str2 val2 ...] < input > output" 
    if scalar(@ARGV)==0 || scalar(@ARGV)%2 != 0;

my %repl = ();
while(scalar(@ARGV)>0) {
    my $from = shift @ARGV;
    my $to = shift @ARGV;
    $repl{$from} = $to;
}   

binmode(STDIN, ":utf8");
binmode(STDOUT, ":utf8");
binmode(STDERR, ":utf8");

while(<STDIN>) {
    foreach my $from (keys %repl) {
	my $newstr = $repl{$from};

	while(1) {
	    my $ind = index($_, $from);
	    last if($ind == -1);
	    
	    my $before = substr($_, 0, $ind);
	    my $after  = substr($_, ($ind+length($from)));
	    $_ = $before . $newstr . $after;
	    if(!$replace_all) {
		delete $repl{$from};
		last;
	    }
	}
    }
    print;
}
