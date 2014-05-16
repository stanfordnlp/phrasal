#!/usr/bin/perl
use strict;
use warnings;


die "Usage: $0 indices_file input_file output_file num_lines_per_selection" if @ARGV != 4;

my $indicesFile = shift;
my $inputFile = shift;
my $outputFile = shift;
my $numLinesPerSelection = shift;


print STDERR "Reading indices...\n";
open INDICES, $indicesFile or die "Cannot open indices file: $indicesFile";
my %inputIndexToOutputIndex = ();
my $outputIndex=0;
while(<INDICES>) {
    s/\s+$//;
    my $index = $_;
    $inputIndexToOutputIndex{$index} = $outputIndex;
    $outputIndex++;
}
close INDICES;

print STDERR "Reading input lines...\n";
open IN, "zcat -f $inputFile|" or die "Cannot open input file $inputFile";

my @selectedTextRegions=();

my $inputIndex=0;
while(scalar(keys(%inputIndexToOutputIndex))>0) {
    my @currentLines = ();
    for(my $i=0; $i<$numLinesPerSelection; $i++) {
	# Note: It is slightly inefficient here to populate the array when that is usually unnecessary.
	my $line = <IN>;
	if(!defined($line)) { die "Unexpected end of file. There are still indices to extract from the input!"; }
	push @currentLines, $line;
    }
    if(defined($inputIndexToOutputIndex{$inputIndex})) {
	# place all lines associated with this index in the appropriate position to prepare for output
	my $outputIndex = $inputIndexToOutputIndex{$inputIndex};
	$selectedTextRegions[$outputIndex]= join("", @currentLines);
	delete $inputIndexToOutputIndex{$inputIndex};
    }
    else {
    }
    $inputIndex++;
}
close IN;


print STDERR "Printing selected lines...\n";
open OUT, ">$outputFile" or die "Cannot open output file $outputFile";
foreach my $textRegion(@selectedTextRegions) {
    print OUT $textRegion;
}
close OUT;
