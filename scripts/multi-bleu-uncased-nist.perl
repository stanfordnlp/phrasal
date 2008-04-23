#!/usr/bin/perl -w

## this file is from http://www.statmt.org/wmt06/shared-task/multi-bleu.perl
## -- william
## uncased version, with NIST-style tokenization
## -- michel

use strict;

if (!scalar(@ARGV)) {
  print STDERR "Syntax: multi-bleu.perl [ref-stem] < [system-output]
If one reference translation: ref-stem is filename
If multiple reference translations: ref-stem[0,1,2,...] is filename\n"; 
}

my $stem = $ARGV[0];
my @REF;
my $ref=0;
while(-e "$stem$ref") {
    &add_to_ref("$stem$ref",\@REF);
    $ref++;
}
&add_to_ref($stem,\@REF) if -e $stem;
die("did not find any reference translations at $stem") unless scalar @REF;

sub NormalizeText {
    my ($norm_text) = @_;
 
# language-independent part:
    $norm_text =~ s/<skipped>//g; # strip "skipped" tags
    $norm_text =~ s/-\n//g; # strip end-of-line hyphenation and join lines
    $norm_text =~ s/\n/ /g; # join lines
    $norm_text =~ s/&quot;/"/g;  # convert SGML tag for quote to "
    $norm_text =~ s/&amp;/&/g;   # convert SGML tag for ampersand to &
    $norm_text =~ s/&lt;/</g;    # convert SGML tag for less-than to >
    $norm_text =~ s/&gt;/>/g;    # convert SGML tag for greater-than to <
 
# language-dependent part (assuming Western languages):
    $norm_text = " $norm_text ";
    #$norm_text =~ tr/[A-Z]/[a-z]/ unless $preserve_case;
    $norm_text =~ s/([\{-\~\[-\` -\&\(-\+\:-\@\/])/ $1 /g;   # tokenize punctuation
    $norm_text =~ s/([^0-9])([\.,])/$1 $2 /g; # tokenize period and comma unless preceded by a digit
    $norm_text =~ s/([\.,])([^0-9])/ $1 $2/g; # tokenize period and comma unless followed by a digit
    $norm_text =~ s/([0-9])(-)/$1 $2 /g; # tokenize dash when preceded by a digit
    $norm_text =~ s/\s+/ /g; # one space only between words
    $norm_text =~ s/^\s+//;  # no leading space
    $norm_text =~ s/\s+$//;  # no trailing space
 
    return $norm_text;
}

sub add_to_ref {
    my ($file,$REF) = @_;
    my $s=0;
    open(REF,$file);
    while(<REF>) {
       chomp;
       tr/A-Z/a-z/;
       push @{$$REF[$s++]}, NormalizeText($_);
    }
    close(REF);
}

my(@CORRECT,@TOTAL,$length_translation,$length_reference);
my $s=0;
while(<STDIN>) {
    chomp;
    tr/A-Z/a-z/;
		$_ = NormalizeText($_);
    my @WORD = split;
    my %REF_NGRAM = ();
    my $length_translation_this_sentence = scalar(@WORD);
    my ($closest_diff,$closest_length) = (9999,9999);
    foreach my $reference (@{$REF[$s]}) {
#      print "$s $_ <=> $reference\n";
       my @WORD = split(/ /,$reference);
       my $length = scalar(@WORD);
       if (abs($length_translation_this_sentence-$length) < $closest_diff) {
           $closest_diff = abs($length_translation_this_sentence-$length);
           $closest_length = $length;
#          print "$i: closest diff = abs($length_translation_this_sentence-$length)<BR>\n";
       }
       for(my $n=1;$n<=4;$n++) {
           my %REF_NGRAM_N = ();
           for(my $start=0;$start<=$#WORD-($n-1);$start++) {
              my $ngram = "$n";
              for(my $w=0;$w<$n;$w++) {
                  $ngram .= " ".$WORD[$start+$w];
              }
              $REF_NGRAM_N{$ngram}++;
           }
           foreach my $ngram (keys %REF_NGRAM_N) {
              if (!defined($REF_NGRAM{$ngram}) || 
                  $REF_NGRAM{$ngram} < $REF_NGRAM_N{$ngram}) {
                  $REF_NGRAM{$ngram} = $REF_NGRAM_N{$ngram};
#                 print "$i: REF_NGRAM{$ngram} = $REF_NGRAM{$ngram}<BR>\n";
              }
           }
       }
    }
    $length_translation += $length_translation_this_sentence;
    $length_reference += $closest_length;
    for(my $n=1;$n<=4;$n++) {
       my %T_NGRAM = ();
       for(my $start=0;$start<=$#WORD-($n-1);$start++) {
           my $ngram = "$n";
           for(my $w=0;$w<$n;$w++) {
              $ngram .= " ".$WORD[$start+$w];
           }
           $T_NGRAM{$ngram}++;
       }
       foreach my $ngram (keys %T_NGRAM) {
           $ngram =~ /^(\d+) /;
           my $n = $1;
#          print "$i e $ngram $T_NGRAM{$ngram}<BR>\n";
           $TOTAL[$n] += $T_NGRAM{$ngram};
           if (defined($REF_NGRAM{$ngram})) {
              if ($REF_NGRAM{$ngram} >= $T_NGRAM{$ngram}) {
                  $CORRECT[$n] += $T_NGRAM{$ngram};
#                 print STDERR "gram=<$ngram> r=$REF_NGRAM{$ngram} h=$T_NGRAM{$ngram}\n";
              }
              else {
                  $CORRECT[$n] += $REF_NGRAM{$ngram};
#                 print "$i e correct2 $REF_NGRAM{$ngram}<BR>\n";
              }
           }
       }
    }
    $s++;
}
my $brevity_penalty = 1;
if ($length_translation<$length_reference) {
    $brevity_penalty = exp(1-$length_reference/$length_translation);
}
my $bleu_prec = exp((my_log( $CORRECT[1]/$TOTAL[1] ) +
										 my_log( $CORRECT[2]/$TOTAL[2] ) +
										 my_log( $CORRECT[3]/$TOTAL[3] ) +
										 my_log( $CORRECT[4]/$TOTAL[4] ) ) / 4);
my $bleu = $brevity_penalty * $bleu_prec;

printf "BLEU = %.4f, BLEU-prec = %.4f, %.1f/%.1f/%.1f/%.1f (BP=%.3f, ration=%.3f) sent=%d/%d\n",
    100*$bleu,
    100*$bleu_prec,
    100*$CORRECT[1]/$TOTAL[1],
    100*$CORRECT[2]/$TOTAL[2],
    100*$CORRECT[3]/$TOTAL[3],
    100*$CORRECT[4]/$TOTAL[4],
    $brevity_penalty,
    $length_translation / $length_reference,
		$s, scalar @REF;

sub my_log {
  return -9999999999 unless $_[0];
  return log($_[0]);
}
