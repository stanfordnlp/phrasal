#/usr/bin/perl
use strict;
use warnings;

die "Usage: $0 merged_verbose_nbest_list test_set_regions lang site system date output_root_dir" if @ARGV != 7;

my ($nbest, $regions_file, $lang, $site, $system, $date, $output_root) = @ARGV;

my @regions = &get_region_info($regions_file);
@regions = sort {$a->{start_index} <=> $b->{start_index}} @regions;

my $SCRIPTS_DIR = $ENV{"JAVANLP_HOME"}."/projects/mt/scripts-private";

# Step through each line of the nbest list and whenever you finish reading the stuff that is associated with the current region, process the region
# More specifically, for loop over regions, for each region read until you are past the region, and then print out to a temp file and run the processor

&exec_or_die("mkdir -p $output_root");
my $temp_file = "${output_root}/__make_package.temp";

die unless -e $nbest;

open TEMP, ">$temp_file" or die $!;
open IN, "zcat -f $nbest|" or die $!;

my $region_index=0;
my ($start, $end, $stanford_name, $ibm_name, $part) = (
    $regions[$region_index]->{start_index}, 
    $regions[$region_index]->{end_index}, 
    $regions[$region_index]->{stanford_name},
    $regions[$region_index]->{ibm_name},
    $regions[$region_index]->{part}
    );
print "\nProcessing $ibm_name $part ($stanford_name)...\n";

while(1) {
    my $line = <IN>;
    my $input_exists = (defined($line) ? 1 : 0);
    my $one_based_index;
    if($input_exists) {
	$one_based_index = &get_sentence_index_from_nbest_line($line)+1;
	die "Unexpected one-based index $one_based_index seen: this index is less than the minimum expected, which is $start\n" 
	    if $one_based_index < $start;
	print "\r${one_based_index}   ";
    }
    if(!$input_exists || $one_based_index > $end) {
	close TEMP;
	# This is the first thing we don't want to include
	# Process the existing tempfile and then reopen it and write this line to it
	my ($output_dir, $output_file) = ("${output_root}/$lang/$site/$system/$ibm_name/$part/$date", "$ibm_name.nbest");
	&exec_or_die("mkdir -p $output_dir");
	#print "\nConverting $ibm_name $part ($stanford_name)...\n";
	&exec_or_die("$SCRIPTS_DIR/phrasal_very_verbose_to_xml.py $temp_file $site.$system.$date $ibm_name 200");
	&exec_or_die("bzip2 -c $ibm_name.xml > $output_dir/$output_file.xml.bz2 && rm $ibm_name.xml");
	if($input_exists) {
	    $region_index++;
	    ($start, $end, $stanford_name, $ibm_name, $part) = (
		$regions[$region_index]->{start_index}, 
		$regions[$region_index]->{end_index}, 
		$regions[$region_index]->{stanford_name},
		$regions[$region_index]->{ibm_name},
		$regions[$region_index]->{part}
		);
	    print "\nProcessing $ibm_name $part ($stanford_name)...\n";
	    open TEMP, ">$temp_file" or die $!;
	}
	else {
	    last;
	}
    }
    
    my $segment_id = $one_based_index - $start;
    $line =~ s/^\d+/$segment_id/;
    print TEMP $line;
}

close IN;
close TEMP;

sub get_sentence_index_from_nbest_line {
    my ($line) = @_;
    $line =~ m/^(\d+)\s+/ or die $!;
    return $1;
}

sub exec_or_die {
    my ($cmd) = @_;
    system($cmd)==0 or die "Error running command: $cmd";
}

sub get_region_info {
    my ($file) = @_;
    open IN, $file or die $!;
    while(<IN>) {
	s/\s+$//;
	my @tokens = split /\s+/, $_;
	die if scalar(@tokens) != 5;
	my ($stanford_name, $ibm_name, $part_name, $start_index, $end_index) = @tokens;
	
	my $hash = {
	    stanford_name => $stanford_name,
	    ibm_name => $ibm_name,
	    part => $part_name,
	    start_index => $start_index,
	    end_index => $end_index,
	};
	push @regions, $hash;
    }
    close IN;
    return @regions;
}



# where 

# $lang = arabic | chinese 
# $site = CU | IBM | RWTH ...etc. 
# $system = DTM2 | ForSyn | Chart etc. 

# $part = dev | syscomtune | test 
# current : where the current version being used for system combination (a symlink to a dated version. 
# $date = 06022014 (for this run including system combination 

# $set depends on language: 
# For arabic: 
# $set = GALE-DEV10-arabic-text-wb | BOLT-LDC2012E30-DEV12-chinese | LDC2012E124-P1R6-cmn | LDC2013E92-BOLT-P1-Progress-cmn | LDC2013E83-BOLT-P2R2-cmn-SMS-CHT 

# For Chinese: 
# $set = GALE-DEV10-chinese-text-wb | BOLT-LDC2012E30-DEV12-arabic | LDC2012E124-P1R6-arz | LDC2013E92-BOLT-P1-Progress-arz | LDC2014E28-BOLT-P2-cmn-SMS-CHT 

#	my ($stanford_name, $ibm_name, $part_name, $start_index, $end_index) = @tokens;
