#!/usr/bin/perl
use strict;
use warnings;


my $stringToFunction = 
{
    "makeTestSetSummary" => \&makeTestSetSummary,
    "prepareData" => \&prepareData,
    "compare" => \&compare,
    "mapLocalToIBM" => \&mapLocalToIBM,
    "setupProgressSetForPrepare" => \&setupProgressSetForPrepare,
};

if(@ARGV==0 || !defined($stringToFunction->{$ARGV[0]})) {
    die "Usage: $0 [tool-to-use] [arguments-for-tool]\n".
	"Examples of tools: ".join(",", sort keys %$stringToFunction);
}
else {
    my $fn = shift;
    &{$stringToFunction->{$fn}}(@ARGV);
}


sub prepareData {
    my @args = @_;
    
    die "Usage: $0 prepareData testSetSummaryFile outDir" if @args != 2;
    
    my $testSetSummaryFile = shift @args;
    my $outDir = shift @args;
    
    my $map = &readMap($testSetSummaryFile);

    &execCommand("mkdir -p $outDir");

    foreach my $localName (sort keys %$map) {

	my $outputDir = "$outDir/$localName";

	&execCommand("mkdir -p $outputDir");

	# check if processed source exists. if so, normalize it
	# if no processed source exists, quit with error
	if(exists($map->{$localName}->{"source"}->{"processed"})) {
	    my $files = $map->{$localName}->{"source"}->{"processed"};
	    die "Too many processed source files for $localName !" if @$files != 1;
	    my $sourceFile = $files->[0];
	    my $localSourceFile = "$outputDir/source.zh";
	    &execCommand("normalize_to_rbt.pl <$sourceFile >$localSourceFile");
	}
	else {
	    die "No processed version of source file for $localName found!";
	}
	# if processed ref exists, normalize and use it
	# if raw ref exists, convert it
	# if no ref exists, we're done
	
	if(exists($map->{$localName}->{"ref"}->{"processed"})) {
	    my $files = $map->{$localName}->{"ref"}->{"processed"};
	    my $refNum=1;
	    foreach my $refFile (sort @$files) {
		my $localRefFile = "$outputDir/ref$refNum";
		&execCommand("normalize_to_rbt.pl <$refFile >$localRefFile");	    
		$refNum++;
	    }
	}
	elsif(exists($map->{$localName}->{"ref"}->{"raw"})) {
	    my $files = $map->{$localName}->{"ref"}->{"raw"};
	    die "Too many raw ref files for $localName !" if @$files != 1;
	    my $rawRefFile = $files->[0];
	    &execCommand("extract_refs.pl $outputDir English 0 < $rawRefFile");
	}
	else {
	    # No ref, we're done
	}
    }
}

sub execCommand {
    my ($cmd) = @_;
    print "$cmd\n";
}

sub readMap {
    my ($f) = @_;

    my %m = ();
    open IN, $f or die $!;
    while(<IN>) {
	s/\s+$//;
	my @tokens = split /\s+/, $_;
	die if @tokens!=6;
	
	my ($localName, $ibmName, $attributes, $refOrSource, $rawOrProcessed, $file) = @tokens;
	
	if(!exists($m{$localName}{$refOrSource}{$rawOrProcessed})) {
	    $m{$localName}{$refOrSource}{$rawOrProcessed} = [$file];
	}
	else {
	    $m{$localName}{$refOrSource}{$rawOrProcessed} = [@{$m{$localName}{$refOrSource}{$rawOrProcessed}}, $file];
	}
    }
    close IN;
    return \%m;
}

sub readLocalToIBMMap {
    my ($f) = @_;

    my %m = ();
    open IN, $f or die $!;
    while(<IN>) {
	s/\s+$//;
	my @tokens = split /\s+/, $_;
	die if @tokens!=6;
	
	my ($localName, $ibmName, $attributes, $refOrSource, $rawOrProcessed, $file) = @tokens;
	
	if(exists($m{$localName})) {
	    my $prevValue = $m{$localName};
	    die "Unexpected inconsistency" if $prevValue ne $ibmName;
	}
	else {
	    $m{$localName} = $ibmName;
	}
    }
    close IN;
    return \%m;
}


# Usage: find data_from_ibm/ -type f | perl dataTool.pl makeTestSetSummary
#
# This function creates a testSetSummary file from a listing of the contents of BOLT-FOUO-Chinese-12172012.tgz
# The purpose of a testSetSummary file is to concisely list information for each set such as:
#   - The names we use for the sets at Stanford (brief names)
#   - The IBM-preferred names (verbose names)
#   - The files which make up each set, including file types (raw reference, etc.)
sub makeTestSetSummary {
    my @args = @_;

    die "Usage: $0 makeTestSetSummary <listing > testSetSummary" if @args != 0;

    while(<STDIN>) {
	s/\s+$//;
	my $fileName = $_;
	my $ibmName = $fileName;
	$ibmName =~ s|.*/||;
	$ibmName =~ s|\..*||;
	$ibmName =~ s/-(src|ref)$//;
	s|.*?/test/||;
	my ($pathPrefix,$rawOrProcessed,$c) = m/^(.*?)(processed|raw)(.*)$/;

	my $evalSetAttributes = $pathPrefix;
	$evalSetAttributes =~ s|/*$||;
	$evalSetAttributes =~ s!/!|!g;
	
	my $localName = $pathPrefix;
	$localName =~ s|/||g;
	$localName =~ s|\s||g;
	my $isref = ($fileName =~ m/ref/);
	my $refOrSource = $isref ? "ref" : "source";
	print "$localName  $ibmName  $evalSetAttributes  $refOrSource  $rawOrProcessed  $fileName\n";
    }
}

sub mapLocalToIBM {
    my @args = @_;

    die "Usage: $0 mapLocalToIBM testSetInfo < localName > ibmName\n" if @args != 1;
    
    my $testSetSummaryFile = shift @args;

    my $localToIBMMap = &readLocalToIBMMap($testSetSummaryFile);
    while(<STDIN>) {
	s/\s+$//;
	if(defined($localToIBMMap->{$_})) {
	    print $localToIBMMap->{$_}."\n";
	}
	else {
	    print $_."\n";
	}
    }
}


sub compare {
    my @args = @_;

    die "Usage: $0 compare path_to_latest_preparation path_to_previous_preparation\n".
	"The purpose of this script is to 'regression test' our data preparation tools." if @args != 2;

    my $da = shift @args;
    my $db = shift @args;
    
    my %a = &makeHash(&getFiles($da));
    my %b = &makeHash(&getFiles($db));
    
    foreach my $i(sort keys %a) {
	if(exists($b{$i})) {
	    print "Found in both: $i\n";
	    &diffRefs("$da/$i","$db/$i");
	    &diffSource("$da/$i","$db/$i");
	}
    }
}



sub diffRefs {
    my ($dirA, $dirB) = @_;
    my @filesA = sort(glob("$dirA/ref*"));
    my @filesB = sort(glob("$dirB/ref*"));
    if(scalar(@filesA) != scalar(@filesB)) {
	print "Error: mismatched number of ref files: ".join(",",@filesA)." vs. ".join(",",@filesB)."\n";
    }
    for(my $i=0; $i<scalar(@filesA); $i++) {
	&diffFiles($filesA[$i], $filesB[$i]);
    }
}

sub diffSource {
    my ($dirA, $dirB) = @_;
    my @filesA = sort(glob("$dirA/source*"));
    my @filesB = sort(glob("$dirB/source*"));
    if(scalar(@filesA) != scalar(@filesB)) {
	print "Error: mismatched number of source files: ".join(",",@filesA)." vs. ".join(",",@filesB)."\n";
    }
    for(my $i=0; $i<scalar(@filesA); $i++) {
	&diffFiles($filesA[$i], $filesB[$i]);
    }
}

sub diffFiles {
    my ($a,$b) = @_;
    #system("");
    open OUTPUT, "diff $a $b|" or die $!;
    my @lines = <OUTPUT>;
    close OUTPUT;
    if(scalar(@lines)==0) {
	print "Files $a and $b are identical.\n";
    }
    else {
	print "==================================================================================================\n";
	print "=============== Diff $a $b ==================\n";
	print "==================================================================================================\n";
	print @lines;
    }
}

sub makeHash {
    my @a = @_;
    my %h = ();
    foreach my $i (@a) {
	$h{$i} = 1;
    }
    return %h;
}

sub getFiles {
    my ($d) = @_;
    opendir (DIR, $d) || die "Error in opening dir $d\n";
    my @items = ();
    my $f;
    while( ($f = readdir(DIR))){
	push @items, $f unless $f =~ m/^(.|..)$/;
    }
    closedir(DIR);
    return @items;
}
