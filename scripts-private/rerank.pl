#!/usr/bin/perl
use strict;
use warnings;

die "Usage: $0 weights < nbestIn > nbestOut" if scalar(@ARGV) != 1;

# True for all nbest formats: first columns are 
# [sentence-id] ||| [output-text] ||| [features] ||| [total-score]
# Other columns are not relevant for our purposes.

my $w = shift @ARGV;

my $weights = &readWeights($w);

my $temp = <STDIN>;
my @currLines = &parseLine($temp);
my $numTokensPerLine = undef;
$numTokensPerLine = scalar(@{$currLines[0]}) if(scalar(@currLines)!=0);

while(scalar(@currLines)!=0) {
    $temp=<STDIN>;
    my @nextLines = &parseLine($temp);

    if(scalar(@nextLines)!=0) {
	my $numTokensThisLine = scalar(@{$nextLines[0]});
	die "Unexpected number of tokens in line: $numTokensThisLine, expecting $numTokensPerLine"
	    if $numTokensThisLine != $numTokensPerLine;
    }
    
    if(scalar(@nextLines)==0 || $currLines[0]->[0] != $nextLines[0]->[0]) {
	# disagreeing next line or no next line
	&processLines(\@currLines, $weights);
	@currLines = ();
    }
    push @currLines, @nextLines;
}

sub processLines {
    my ($currLines, $weights) = @_;
    
    my @outputLines = ();
    foreach my $line (@$currLines) {
	my $raw = $line->[2];
	my @toks = split /\s+/, &trim($raw);
	die "Unexpected features: $raw" if scalar(@toks)%2 != 0;
	my $totalScore = 0;
	for(my $i=0; $i<scalar(@toks); $i+=2) {
	    my $name = $toks[$i];
	    my $val  = $toks[$i+1];
	    $name =~ s/:$//;
	    if(defined($weights->{$name})) {
		$totalScore += $weights->{$name} * $val;
	    }
	}
	$line->[3] = $totalScore;
	push @outputLines, $line;
    }
    @outputLines = sort { $b->[3] <=> $a->[3] } @outputLines;
    foreach my $l (@outputLines) {
	print join(" ||| ", @$l)."\n";
    }
}

sub trim {
    my ($str) = @_;
    $str =~ s/\s+$//;
    $str =~ s/^\s+//;
    return $str;
}

# Slightly unusual definition in that EOF is represented as empty list
# and typical case (single line read) is represented as single-element list
sub parseLine {
    my ($line) = @_;
    return () if !defined($line);
    $line =~ s/\s+$//;
    my @tokens = split / \|\|\| /, $line;
    return (\@tokens);
}

sub readWeights {
    my ($file) = @_;
    open IN, $file or die $!;
    my %weights = ();
    while(<IN>) {
	s/\s+$//;
	my @tokens = split /\s+/, $_;
	die "Unexpected line: $_" if scalar(@tokens) != 2;
	my ($name, $val) = @tokens;
	die "Repeated name: $name" if defined($weights{$name});
	$weights{$name} = $val;
    }
    close IN;
    return \%weights;
}


# ==> final_nbest <==
# 0 ||| on oct. 9 to 11 , held by tongji university and german tongji alumni " tongji forum " held in berlin , germany . ||| LM: -1.3488E2 LM2: -1.5126E2 LM3: -1.218E2 dm: -1.2947E1 nnlm0: -40.385 nnlm1: -32.913  ||| -1.2947E1 ||| 0-1 1-1 2-2 3-0 4-3 5-4 7-5 8-7 9-8 10-9 11-10 12-11 13-12 14-13 15-6 17-14 18-15 19-16 20-17 21-19 22-22 23-20 24-18 25-23 ||| 日 <r> on <r> 3 <r> (0) |R| 10 月 9 <r> oct. 9 <r> 0 <r> (0,1) (2) |R| 至 11 日 <r> to 11 <r> 4 <r> (0) (1) |R| , <r> , <r> 7 <r> (0) |R| 举办 <r> held <r> 15 <r> (0) |R| 由 <r> by <r> 8 <r> (0) |R| 同济 大学 <r> tongji university <r> 9 <r> (0) (1) |R| 和 <r> and <r> 11 <r> (0) |R| 德国 <r> german <r> 12 <r> (0) |R| 同济 <r> tongji <r> 13 <r> (0) |R| 校友会 <r> alumni <r> 14 <r> (0) |R| 的 " <r> " <r> 16 <r> (1) |R| 同济 <r> tongji <r> 18 <r> (0) |R| 论坛 " <r> forum " <r> 19 <r> (0) (1) |R| 举行 <r> held <r> 24 <r> (0) |R| 在 德国 柏林 <r> in berlin , germany <r> 21 <r> (0) (2) () (1) |R| 。 <r> . <r> 25 <r> (0)

# ==> train.wts <==
# LM2 -0.01186273076015231
