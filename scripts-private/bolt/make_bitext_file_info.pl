#!/usr/bin/perl

# Attempt to guess the genre by applying hacky manually written regexps to the file path.
# Needless to say this is highly tailored to the particular file structure
# In our case we are targeting IBM-produced files for BOLT

use strict;
use warnings;

die "Usage: $0 ldc_catalog_id_to_genres input_files_list output_info_list" if @ARGV != 3;

my $ldc_list_file = shift @ARGV;
my $input_file = shift @ARGV;
my $output_file = shift @ARGV;


my %genre_to_regexps = (
    "bc" => ['[_\.]bc\.'],
    "bn" => ['[_\.]bn\.'],
    "chat" => ['-CHT-'],
    "df"  => ['Webforum'],
    "gov" => ['\.HKhansards\.'],
    "legal" => ['\.HKLaws\.', '\.HKLaw\.'],
    "lex" => ['\.dict\.'],
    "misc" => ['ldc_(propernames|cedict|orgs)'],
    "ng"  => ['[_\.]ng\.'],
    "nw" => ['\.HKnews\.', 'XinhuaNews_', 'wsj\.', 'gigaw\.', '[_\.]nw\.'],
    "sms" => ['-SMS-'],
    "un" => ['UN_EC_'],
    "web" => ['[_\.\-]we?b\.'],
    "wl" => ['[_\.]wl\.'],
    );

my %ldc_id_to_genre = %{&load_ldc_list($ldc_list_file)};

open IN, $input_file or die $!;
open OUT, ">$output_file" or die $!;
my $file_id = 0;
while(<IN>) {
    s/\s+$//;
    my @file_names = split /\s+/, $_;
    die unless scalar(@file_names)==2;
    my $en_file_name = $file_names[0];
    my $zh_file_name = $file_names[1];

    my @possible_genres_by_regexp = ();
    my @possible_genres_by_ldc_id = ();
    foreach my $genre(sort keys %genre_to_regexps) {
	foreach my $regexp (@{$genre_to_regexps{$genre}}) {
	    my $re = qr/$regexp/;
	    if($en_file_name =~ $re) {
		#print "regexp match\n";
		push @possible_genres_by_regexp, $genre;
		#print OUT "regexp match: $genre\n";
	    }
	}
    }
    
    foreach my $catalog_id (sort keys %ldc_id_to_genre) {
	if(&string_contains_ldc_id($en_file_name, $catalog_id)) {
	    # This catalog id is a match
	    #print "ldc match\n";
	    foreach my $genre (@{$ldc_id_to_genre{$catalog_id}}) {
		push @possible_genres_by_ldc_id, $genre;
		#print OUT "ldc match: $genre\n";
	    }
	}
    }

    my @possible_genres = ();

    @possible_genres_by_regexp = sort @possible_genres_by_regexp;
    @possible_genres_by_ldc_id = sort @possible_genres_by_ldc_id;

    my $no_ldc_genres = (scalar(@possible_genres_by_ldc_id)==0);
    my $no_regexp_genres = (scalar(@possible_genres_by_regexp)==0);

    if($no_ldc_genres && $no_regexp_genres) {
	@possible_genres = ("undef");
    }
    elsif($no_ldc_genres) {
	@possible_genres = @possible_genres_by_regexp;
    }
    elsif($no_regexp_genres) {
	@possible_genres = @possible_genres_by_ldc_id;
    }
    elsif(&is_subset(\@possible_genres_by_regexp, \@possible_genres_by_ldc_id)) 
    {
	# Take the more specific version based on regexp analysis
	# Sometimes the LDC collections are split by genre and then
	# the genre is marked in the file name.
	@possible_genres = @possible_genres_by_regexp;
    }
    else {
	warn "Warning: genre mismatch between regexp analysis (".join(",",@possible_genres_by_regexp).") and LDC (".join(",",@possible_genres_by_ldc_id)."). Assuming regexp is right.";
	@possible_genres = @possible_genres_by_regexp;
    }

    my %info = ();

    $info{"target_file_name"} = $en_file_name;
    $info{"source_file_name"} = $zh_file_name;
    $info{"presumed_genres"} = join(",", @possible_genres);
    $info{"file_id"} = $file_id;

    &write_key_val_pairs(\%info, *OUT);

    $file_id++;
}
close OUT;
close IN;

sub write_key_val_pairs {
    my ($pairs, $fh) = @_;
    my @strings = ();
    foreach my $key (sort keys %$pairs) {
	push @strings, sprintf("%s=%s", $key, $pairs->{$key});
    }
    print $fh join("\t", @strings)."\n";
}

sub string_contains_ldc_id {
    my ($s, $id) = @_;
    my $index = index($s, $id);
    if($index != -1) {
	# check that $id is not merely a prefix of the embedded LDC id
	if(length($s) == $index+length($id) || substr($s, $index+length($id),1) !~ m/\d/) {
	    return 1;
	}
    }
    return 0;
}

# Assumes set of strings
sub is_subset {
    my ($a, $b) = @_;
    foreach my $k (@$a) {
	my $found = 0;
	foreach my $k2 (@$b) {
	    if($k eq $k2) {
		$found=1;
		last;
	    }
	}
	if(!$found) { return 0; }
    }
    return 1;
}

sub load_ldc_list {
    my ($f) =@_;
    open IN, $f or die $!;
    my %map = ();
    while(<IN>) {
	s/\s+$//;
	my @tokens = split /\s+/, $_;
	die if scalar(@tokens) != 2;
	if(defined($map{$tokens[0]})) {
	    push @{$map{$tokens[0]}}, $tokens[1];
	}
	else {
	    $map{$tokens[0]}=[$tokens[1]];
	}
    }
    close IN;
    return \%map;
}
