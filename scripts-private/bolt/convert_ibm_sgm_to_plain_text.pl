#!/usr/bin/perl
# Example usage: convert_ibm_sgm_to_plain_text.pl --meta MyMetadataFile < SgmFile  > PlainTextOutputFile

# Read sgm data from stdin, print to stdout, optionally write metadata to auxiliary file

use open qw(:std :utf8);
use strict;
use warnings;
use Getopt::Long;


# declare the perl command line flags/options we want to allow
my %options=();

my $metadata_file = undef;
my $apply_gzip = 0;
my $guess_genre_regexp_list_file = undef;
my $guess_genre_regexps= undef;


my $result = GetOptions(
    "meta=s" => \$metadata_file, 
    "z" => \$apply_gzip,
    "guessGenreUsingRegexpList=s" => \$guess_genre_regexp_list_file,
) or die $!;

binmode STDIN, ":utf8";
binmode STDOUT, ":utf8";

if(defined($metadata_file)) {
    my $open_symbol = $apply_gzip ? "| gzip -c >" : ">";
    open M, "${open_symbol}$metadata_file" or die $!;
}

my %regexp_to_genre = ();
if(defined($guess_genre_regexp_list_file)) {
    open IN, $guess_genre_regexp_list_file or die "Could not open file: $guess_genre_regexp_list_file";
    while(<IN>) {
	s/\s+$//;
	my ($genre, $regexp) = split /\t/, $_, 2;
	$regexp_to_genre{$regexp} = $genre;
    }
    close IN;
}

my @tag_stack = ();

my $attributes_regexp = '(?:\s+[^=]+?=[\'\"][^\'\"]+?[\'\"])*';

my @attribute_pairs_stack = ();

while(<STDIN>) {
    if(scalar(@tag_stack)==0) {
	# Should be either a DOC or EOF
	if($_ !~ m|^<DOC |) {
	    die "Unexpected line: $_";
	}
	else {
	    m/^<DOC(${attributes_regexp})\s*>\s*$/ or die "Badly formatted line: $_";
	    my @doc_attribute_pairs = &get_attribute_pairs($1);	    
	    push @tag_stack, "DOC";
	    push @attribute_pairs_stack, [@doc_attribute_pairs];
	}
    }
    else {
	# Should be either a <post>, <seg>...</seg>, </post> or </DOC>
	if($_ =~ m!^<(post|body|text|GALE_P2)(${attributes_regexp})>\s*$!) {
	    my ($tag_name, $attributes_string) = ($1,$2);
	    my @attribute_pairs = &get_attribute_pairs($attributes_string);
	    push @tag_stack, "$tag_name";
	    push @attribute_pairs_stack, [@attribute_pairs];
	}
	elsif($_ =~ m!^<seg(${attributes_regexp})>(.*)</seg>\s*!) {
	    my ($attributes_string, $text) = ($1,$2);
	    my @seg_attributes = &get_attribute_pairs($attributes_string);
	    push @attribute_pairs_stack, [@seg_attributes];
	    push @tag_stack, "seg";

	    if(defined($metadata_file)) {
		my @additional_attribute_strings = ();
		if(defined($guess_genre_regexp_list_file)) {
		    push @additional_attribute_strings, sprintf("presumed_genre=%s", &guess_genre(\%regexp_to_genre, \@attribute_pairs_stack));
		}
		my @printable_attributes = &make_attributes_strings(\@tag_stack, \@attribute_pairs_stack);
		print M join("\t", (@printable_attributes, @additional_attribute_strings))."\n";
		print &trim($text)."\n";
	    }

	    pop @attribute_pairs_stack;
	    pop @tag_stack;
	}
	elsif(m!^</(DOC|post|body|text|GALE_P2)>\s*$!) {
	    my $close_tag_name = $1;
	    die if $tag_stack[$#tag_stack] ne $close_tag_name;
	    pop @tag_stack;
	    pop @attribute_pairs_stack;
	}
	else {
	    die "Unexpected line: $_\nExpected </DOC> or <seg>...</seg>";
	}
    }
}

if(defined($metadata_file)) {
    close M or die $!;
}

sub make_attributes_strings {
    my ($tag_stack, $attribute_pairs_stack)= @_;
    my @attr_strings = ();
    for(my $i=0; $i<scalar(@$tag_stack); $i++) {
	my $tag_name = $tag_stack->[$i];
	die if !defined($attribute_pairs_stack->[$i]);
	foreach my $attr_pair (@{$attribute_pairs_stack->[$i]}) {
	    my ($name, $val) = @$attr_pair;
	    push @attr_strings, sprintf("%s:%s=%s", $tag_name, $name, $val);
	}
    }
    return @attr_strings;
}

sub guess_genre {
    my ($regexp_to_genre, $attribute_pairs_stack) = @_;
    my $genre = undef;
    foreach my $pairs (@$attribute_pairs_stack) {
	foreach my $pair (@$pairs) {
	    my ($name,$val)= @$pair;
	    if($name eq "docid") {
		$genre = &guess_genre_from_docid($regexp_to_genre, $val);
	    }
	}
    }
    if(!defined($genre)) {
	warn "Did not find an attribute for guessing genre\n";
	$genre = "undef";
    }

    return $genre;
}

# A bunch of ad hoc rules
sub guess_genre_from_docid {
    my ($regexp_to_genre, $docid) = @_;
    my $genre = undef;
    foreach my $re (sort keys %$regexp_to_genre) {
	if($docid =~ m/$re/) {
	    $genre = $regexp_to_genre->{$re};
	}
    }
    if(!defined($genre)) {
	warn "Genre not defined for docid=$docid\n";
	$genre = "undef";
    }
    return $genre;
}

sub get_attribute_pairs {
    my ($str) = @_;
    my @pairs = ();
    while($str =~ m/\s+([^=]+)\=[\'\"]([^\'\"]+)[\'\"]/g) {
	my ($name, $val) = map {&trim($_)} ($1,$2);	
	push @pairs, [$name,$val];
    }
    return @pairs;
}

sub trim {
    my ($str) = @_;
    $str =~ s/^\s+//;
    $str =~ s/\s+$//;
    return $str;
}
