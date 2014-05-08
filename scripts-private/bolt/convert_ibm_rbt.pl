#!/usr/bin/perl
#
# Description:  Remove IBM's class based 
# annotation of numbers, dates, ordinals, 
# urls, etc.
#
# Apply normalization as requested.
#
# Author: Daniel Cer (danielcer@stanford.edu)
# Author: Mike Kayser (mkayser@stanford.edu)
#############################################

use open qw(:std :utf8);
use strict;
use warnings;
use Getopt::Long;

# declare the perl command line flags/options we want to allow
my %options=();

my $use_translation = 0;
my $use_original = 0;
my $remove_fffa = 0;
my $keep_fffa = 0;
my $apply_lower_case = 0;
my $rbt_bitext_file = undef;

#Maybe this is a little unorthodox, but for complete transparency
# we require the user to explicitly specify what to do to the 
# file, which means we have explicit flags for every possible
# action (e.g. "keepTrans" versus "keepOrig"). We do not
# provide a default behavior so one of these must be specified.
# Likewise, one of "removeFFFA" and "keepFFFA" must be present.

my $result = GetOptions("keepTrans" => \$use_translation,
			"keepOrig"  => \$use_original,
			"removeFFFA" => \$remove_fffa,
			"keepFFFA" => \$keep_fffa,
			"applyLowerCase" => \$apply_lower_case,
			"rbtBitext=s" => \$rbt_bitext_file,
    ) or die $!;

# Now we have to test for consistency and test the user
# specified any necessary options.
if(
    ( $use_translation &&  $use_original) ||
    (!$use_translation && !$use_original)
    )
{
    die "You must specify exactly one of: --keepTrans, --keepOrig";
}

if(
    ( $remove_fffa &&  $keep_fffa) ||
    (!$remove_fffa && !$keep_fffa)
    )
{
    die "You must specify exactly one of: --removeFFFA, --keepFFFA";
}

binmode STDIN, ":utf8";
binmode STDOUT, ":utf8";

if(defined($rbt_bitext_file)) {
    open RBT_SRC, ">${rbt_bitext_file}.src" or die $!;
    binmode RBT_SRC, ":utf8";

    open RBT_TRG, ">${rbt_bitext_file}.trg" or die $!;
    binmode RBT_TRG, ":utf8";
}

while (<STDIN>) { 
    chomp;
    #s/[[:cntrl:]]/ /g;
    if(defined($rbt_bitext_file)) {
	while(m/\$\S+\_\(([^|]+)\|\|([^)]*)\)/g) {
	    my ($orig, $trans) = ($1,$2);
	    # Always normalize target, since this has to coexist with
	    # our bitext, which is always normalized
	    $orig = &normalize_text($orig, $remove_fffa, $apply_lower_case);
	    $trans = &normalize_text($trans, 1, 1);
	    print RBT_SRC $orig."\n";
	    print RBT_TRG $trans."\n";
	}
    }
    if($use_translation) {
	s/\$\S+\_\(([^|]+)\|\|([^)]*)\)/$2/g;
    }
    else {
	s/\$\S+\_\(([^|]+)\|\|([^)]*)\)/$1/g;
    }
    s/\$\S+\_\(([^|)]+)\)/$1/g;
    while (/\s(\S+)\@\@(\S+)\s/) { 
	    s/\s(\S+)\@\@(\S+)\s/ $1 $2 /g; 
    }
    my $output = &normalize_text($_, $remove_fffa, $apply_lower_case);
    print "$output\n";
}

if(defined($rbt_bitext_file)) {
    close RBT_SRC;
    close RBT_TRG;
}


# Normalize a line of text according to flags
# Note that if all flags are off this is a no-op.
sub normalize_text {
    my ($str, $remove_fffa, $apply_lower_case) = @_; 

    if($remove_fffa) {
	$str =~ s/\x{FFFA}/ /g;
    }

    if($apply_lower_case) {
	$str = lc $str;
    }

    return $str;
}
