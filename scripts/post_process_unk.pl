#!/usr/bin/perl

use ChineseNumbersU8;

###################################################
# Script to post-process C->E MT output.
#
# Unknown type  Action 
# ------------  ------
# number        conversion
# other         drop
#
# Author: Michel Galley (mgalley@stanford.edu)
#         Daniel Cer (Daniel.Cer@gmail) 
#  
###################################################

use utf8;

require 'num_chars.pl';

$CONVERSION_TABLE = "/u/nlp/scr/data/gale/resources/char_to_pinyin.txt";
$NAMED_ENTITY_TABLE = "/u/nlp/scr/data/gale/resources/LDC2005T34.tgtt";

binmode(STDIN,":utf8");
binmode(STDOUT,":utf8");
binmode(STDERR,":utf8");

open cfh, $CONVERSION_TABLE or die;
binmode(cfh, ':utf8');
while (<cfh>) { chomp;
  s/#.*$//g;
  next if (/^\s*$/);
  @fields = split /\s+/;
  $pref_pinyin = $fields[1];
  $pref_pinyin =~ s/[0-9]//g;
  $prefered_pinyin{$fields[0]} = $pref_pinyin;
}
close cfh;

open nfh, $NAMED_ENTITY_TABLE or die;
binmode(nfh, ':utf8');
while (<nfh>) { chomp;
  @fields = split /\s+\|\|\|\s+/;
  $zh = $fields[0];
  $en = $fields[1];
  next if ($names{$zh}); # prefer the first entry
  $names{$zh} = $en;
}
close nfh;

sub to_pinyin {
  my ($w) = @_;
  my ($first, @chars, $chr_idx, $cnt_chars, $p);
  @words = split /\s+/, $w;
  $first = 1;
  @chars = split //, $w;
  $chr_idx = 0; $cnt_chars = @chars;
  foreach $char (@chars) {
    $p .= " " if ($cnt_chars == 3 && $chr_idx == 1);
    $p .= " " if ($cnt_chars == 4 && $chr_idx == 2);
    if ($prefered_pinyin{$char}) {
      $p .= $prefered_pinyin{$char};
    } else {
      $p .= " ";
    }
    $chr_idx++;
  }
  return $p;
}

$line = 0;

$SCRIPTS_DIR = $0;
$SCRIPTS_DIR =~ s/\/[^\/]*$//;
if ($SCRIPTS_DIR eq $0) {
  $SCRIPTS_DIR = `which $0`;
  $SCRIPTS_DIR =~ s/\/[^\/]*$//;
}


while(<STDIN>) { chomp; $line++;
	my @w = split /\s+/;
	my @w2;
	foreach my $w (@w) {
		if($w =~/[^\$\[\]\/A-Za-z0-9,.()'"%&;:+-]/) {
      if ($w =~ /^[年$numchars]+$/) {
        $num = ChineseNumbers->ChineseToEnglishNumber($w, 'arabic');
        if ($w =~ /^百分之/) {
          $num = ($num*100)."%"; 
        }
        push @w2, $num;
        print STDERR "number-conversion($line): '$w'->$num\n";
      } elsif ($names{$w}) {
        push @w2, $names{$w};
        print STDERR "name-table conversion($line): '$w'->$names{$w}\n";
      } else {
        $pinyin = to_pinyin($w); 
			  print STDERR "rough $pinyin ($line): $w\n";
      }
		} else {
			push @w2, $w;
		}
	}
	print join(' ',@w2)."\n";
}
