#!/usr/bin/perl

# TODO: mkayser needs to comment this!
# Using a formatted list of test set descriptions, this script
# generates each test set, applying some basic preprocessing.

use strict;
use warnings;

die "$0 output_dir < test_sets_description >commands_to_run" if @ARGV != 1;

my ($output_dir) = @ARGV;

my %src_files = (); # Source tok and info files

while(<STDIN>) {
    s/\s+$//;
    my @tokens = split /\s+/, $_;
    my ($concise_name,$src_or_ref,$format,$files)= @tokens;

    # Process RBT (rule-based-translations) for source files
    # Copy ref files using concise name
    # Get metadata, including guessed genre info, from sgm files
    my @files_list = split /,/, $files;
    my $file_num = 1;
    foreach my $file (@files_list) {
	my $suffix = scalar(@files_list)>1 ? ".$file_num" : "";

	if($src_or_ref eq "src" && $format eq "tok") {
	    my $output_file = "${output_dir}/$concise_name.src$suffix";
	    &exec_command("cat $file | perl ~/javanlp/projects/mt/scripts-private/bolt/convert_ibm_rbt.pl --keepOrig --removeFFFA --applyLowerCase --rbtBitext ${output_dir}/$concise_name.rbt_bitext$suffix > $output_file\n");
	    if(!defined($src_files{$concise_name})) {
		$src_files{$concise_name} = [[],[]];
	    }
	    push @{$src_files{$concise_name}->[0]}, $output_file;
	}
	elsif($src_or_ref eq "src" && $format eq "sgm") {
	    my $meta_file = "${output_dir}/$concise_name.seg_info$suffix";
	    &exec_command("cat $file | perl ~/javanlp/projects/mt/scripts-private/bolt/convert_ibm_sgm_to_plain_text.pl --meta $meta_file --guessGenreUsingRegexpList manual_lists/docid_regexp_to_genre > /dev/null");
	    if(!defined($src_files{$concise_name})) {
		$src_files{$concise_name} = [[],[]];
	    }
	    push @{$src_files{$concise_name}->[1]}, $meta_file;
	    
	}
	elsif($src_or_ref eq "ref" && $format eq "tok") {
	    &exec_command("cat $file | perl ~/javanlp/projects/mt/scripts-private/bolt/convert_ibm_rbt.pl --keepOrig --removeFFFA --applyLowerCase > ${output_dir}/$concise_name.ref$suffix");
	}
	else {
	    warn "warning: skipping line: $_\n";
	    last;
	}
	$file_num++;
    }

}

foreach my $key (sort keys %src_files) {
    my ($tok_files, $meta_files) = @{$src_files{$key}};
    die if !defined($tok_files) or !defined($meta_files);
    my $merged_meta_file = sprintf("%s/%s.seg_info", $output_dir, $key);
    if(scalar(@{$tok_files})==0 or scalar(@{$meta_files})==0) {
	warn "Tokenized and meta files not both defined for set: $key\n";
    }
    else {
	my @m = ();
	foreach my $meta_file (@$meta_files) {
	    open M, $meta_file or die $!;
	    my @this_m = <M>;
	    push @m, @this_m;
	    close M;
	}
	
	my @t = ();
	foreach my $tok_file (@$tok_files) {
	    open T, $tok_file or die $!;
	    my @this_t = <T>;
	    push @t, @this_t;
	    close T;
	}

	die "Mismatch between #lines in metadata files and #lines in tokenized source files: ".join(",",@$meta_files)."   ".join(",",@$tok_files) if scalar(@m) != scalar(@t);
	
	my @lengths = map {&get_length($_);} @t;
	
	open M, ">$merged_meta_file" or die $!;
	for(my $i=0; $i<scalar(@m); $i++) {
	    my $new_line = $m[$i];
	    $new_line =~ s/\s+$//;
	    $new_line .= "\t"."src_tok_count=".$lengths[$i]."\n";
	    print M $new_line;
	}
	close M;
    }
}

sub get_length {
    my ($line) = @_;
    $line =~ s/\s+$//;
    $line =~ s/^\s+//;
    my @tokens = split /\s+/, $line;
    return scalar(@tokens);    
}

sub exec_command {
    my ($cmd) = @_;
    print $cmd."\n";
    system($cmd)==0 or die $!;
}
