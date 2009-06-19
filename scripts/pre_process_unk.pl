#!/usr/bin/perl
BEGIN { push @INC, "$ENV{JAVANLP_HOME}/projects/mt/scripts" }

use ChineseNumbersU8;

###################################################
# Script to post-process C->E MT output.
#
# Unknown type  Action 
# ------------  ------
# number        conversion
# other         drop
#
# Author:   Daniel Cer (Daniel.Cer@gmail) 
#           Michel Galley (mgalley@stanford.edu)
#           Pi-Chuan Chang (pichuan@stanford.edu)
#  
###################################################

use utf8;

require 'num_chars.pl';

$CONVERSION_TABLE = "/u/nlp/scr/data/gale/resources/char_to_pinyin.txt";
$NAMED_ENTITY_TABLE = "/u/nlp/scr/data/gale/resources/LDC2005T34.tgtt";
$FIRST_NAME_TABLE = "/scr/nlp/data/gale2/NameTransliteration/firstName.list";

binmode(STDIN,":utf8");
binmode(STDOUT,":utf8");
binmode(STDERR,":utf8");

use arg_utils qw(&get_args &get_opts);

my %opts = get_opts(['t',0,'Chinese Name Transliteration will be used'],
                    ['n',0,'NIST: using a different conversion table!'],
                    ['e',0,'NIST: using extended conversion table!!'],
                    ['i',0,'output index mapping of input and output']);

my %args = get_args();

if ($opts{t} == 1) {
    print STDERR "-t is on : using Chinese Name Transliteration\n";
} else {
    print STDERR "-t is off: NOT using Chinese Name Transliteration\n";
}
if ($opts{n} == 1) {
    $CONVERSION_TABLE = "/scr/nlp/data/gale2/NameTransliteration/conversion.list";
    print STDERR "-n is on : for NIST (using a different pinyin conversion table: $CONVERSION_TABLE)\n";

    if ($opts{e} == 1) {
        $CONVERSION_TABLE = "/scr/nlp/data/gale2/NameTransliteration/conversion.extended.list";
        print STDERR "-e is on : overriding pinyin conversion table to : $CONVERSION_TABLE)\n";
    } else {
        print STDERR "-e is off: using current conversion table: $CONVERSION_TABLE\n";
    }
} else {
    print STDERR "-n is off: using Unihan pinyin conversion table: $CONVERSION_TABLE\n";
}

if ($opts{i}==1) {
    print STDERR "-i is on : output index mapping between input/output\n";
}


open fnt, $FIRST_NAME_TABLE or die;
binmode(fnt, ':utf8');
while(<fnt>) {
    chomp;
    s/^\s+//;
    s/\s+$//;
    $firstName{$_} = 1;
    #print STDERR $_,"\n";
}
close fnt;

open rct, $CONVERSION_TABLE or die;
binmode(rct, ':utf8');
while(<rct>) {
    chomp;
    s/^\s+//;
    s/\s+$//;
    if (/^\#/) { next; }
    @toks = split(/\s+/);
    $restrictChar{$toks[0]} = 1;
    #print STDERR $toks[0],"\n";
}
close rct;



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

sub checkChars {
    $w = shift;
    foreach $i (0..length($w)-1) {
        $c = substr $w, $i, 1;
        if(not defined $restrictChar{$c} or $restrictChar{$c} != 1) {
            return 0;
        }
    }
    return 1;
}

$line = 0;

$SCRIPTS_DIR = $0;
$SCRIPTS_DIR =~ s/\/[^\/]*$//;
if ($SCRIPTS_DIR eq $0) {
  $SCRIPTS_DIR = `which $0`;
  $SCRIPTS_DIR =~ s/\/[^\/]*$//;
}

if (@ARGV < 2) {
   print stderr "Usage:\n\t$0 (phrase-table) (source text)\n";
   exit -1;
}

$phrase_tbl = $ARGV[0]; @source_txts = @ARGV; shift @source_txts;

if ($phrase_tbl =~ /.gz$/) {
print STDERR "Loading $phrase_tbl via zcat\n";
open fh, "zcat $phrase_tbl |" or die;
} else {
print STDERR "Loading $phrase_tbl\n";
open fh, $phrase_tbl or die;
}
binmode(fh, ':utf8');
$line=0;
while (<fh>) { chomp; $line++;
  print stderr "line > $line\n" if (!($line % 1000000));
  @fields = split /\|\|\|/;
  $src_phrs = $fields[0];
  @src_tokens = split /\s+/, $src_phrs;
  foreach $token (@src_tokens) { $known_words{$token} = 1; }
}
print "done\n";
close fh;

#foreach $word (keys %known_words) {
#  print stderr "-$word\n";
#}

for ($src_i = 0; $src_i <= $#source_txts; $src_i += 2) {
  $source_txt = $source_txts[$src_i];
  $osource_txt = $source_txts[$src_i+1]; 
  print STDERR "Reading from source file : $source_txt\n";
  if (-e $osource_txt) {
     print stderr "$osource_txt exists, skipping $source_txt => $osource_txt\n";
     next;
  }
  print STDERR "Writing to target file : $osource_txt\n";
  
  open fh, $source_txt or die "Can't open $source_txt\n";
  binmode(fh, ':utf8');

  print STDERR "Writing to target file : $osource_txt\n";
  if ($opts{i} == 1) {
      print STDERR "Writing index to file : $osource_txt.idx\n";
  }
  
  open fh, $source_txt or die "Can't open $source_txt\n";
  binmode(fh, ':utf8');
  open ofh, ">$osource_txt" or die "Can't open $osource_txt for writing";
  binmode(ofh, ':utf8');
  
  if ($opts{i} == 0) {
      print stderr "$source_txt => $osource_txt\n";
  } elsif ($opts{i} == 1) {
      open oifh, ">$osource_txt.idx" or die "Can't open $osource_txt.idx for writing";
      binmode(oifh, ':utf8');
      print stderr "$source_txt => $osource_txt (mapping idx: $osource_txt.idx\n";
  }

  while(<fh>) { 
      chomp; $line++;
      my @w = split /\s+/;
      my @w2;
      my @index;
      my $prevW2idx = 0;
      foreach $widx (0..$#w) {
          $w = $w[$widx];
          $fc = substr $w, 0, 1;
          if ($known_words{$w}) {
              push @w2, $w; 
              @toks=split(/ /,$w);
              $size = $#toks;
              @indices=$prevW2idx..($prevW2idx+$size);
              push @index, "$widx:".join(",",@indices);
              $prevW2idx += $size+1;
          } elsif($w =~/^([0-9]+)\(ord\)$/) {
              $num = ChineseNumbers->ChineseToEnglishNumber("第$1", 'arabic');
              print STDERR "ord: '$w' -> '$num'\n";
              push @w2, $num;
              @toks=split(/ /,$num);
              $size = $#toks;
              @indices=$prevW2idx..($prevW2idx+$size);
              push @index, "$widx:".join(",",@indices);
              $prevW2idx += $size+1;
          } elsif($w =~/^[\$\[\]\/A-Za-z0-9,.()\'\"%&;:+-?=@]*$/) {
              print STDERR "latin: '$w' (keeping)\n";
              push @w2, $w;
              @toks=split(/ /,$w);
              $size = $#toks;
              @indices=$prevW2idx..($prevW2idx+$size);
              push @index, "$widx:".join(",",@indices);
              $prevW2idx += $size+1;
          } elsif ($w =~ /^[年$numchars]+$/) {
              $w_orig = $w;
              $prefix = "";
              $suffix = "";
              if ($w =~ /几$/) {
                  $w =~ s/几$//;
                  $prefix = "more than ";
              }
              if ($w =~ /年$/) {
                  $w =~ s/年$//;
                  $suffix = " years"; 
              }
              $w =~ s/\x{25cb}/0/g; # fix the weird zero like character
              $num = ChineseNumbers->ChineseToEnglishNumber($w, 'arabic');
              if ($w =~ /^百分之/) {
                  $num = ($num*100)."%"; 
              }
              if ($num eq '') {
                  print STDERR "skipping number(?): $w_orig\n";
                  next;
              }
              $full_num = $prefix.$num.$suffix;
              push @w2, $full_num;
              @toks=split(/ /,$full_num);
              $size = $#toks;
              @indices=$prevW2idx..($prevW2idx+$size);
              push @index, "$widx:".join(",",@indices);
              $prevW2idx += $size+1;
              print STDERR "number-conversion($line): '$w_orig'->'$full_num'\n";
          } elsif ($names{$w}) {
              $name = $names{$w};
              if ($name =~ /, the$/) {
                  $name =~ s/, the$//;
                  $name = "the $name";
              }
              push @w2, $name;
              @toks=split(/ /,$name);
              $size = $#toks;
              @indices=$prevW2idx..($prevW2idx+$size);
              push @index, "$widx:".join(",",@indices);
              $prevW2idx += $size+1;
              print STDERR "name-table conversion($line): '$w'->'$name'\n";
          } elsif ($opts{t} == 1 
                   and (length($w) == 2 or length($w) == 3 )
                   and (defined $firstName{$fc} and $firstName{$fc}==1)
                   and (($opts{n} == 1 and checkChars($w) == 1)
                        or $opts{n} == 0)) {
              $pinyin = to_pinyin($w);
              print STDERR "Guessing $w to be a Chinese name: '$w' --> '$pinyin'\n";
              if ($pinyin =~ /^\s*$/) {
                  print STDERR "  skipping $w - no pinyin available\n";
                  next;
              }
              push @w2, $pinyin;
              @toks=split(/ /,$pinyin);
              $size = $#toks;
              @indices=$prevW2idx..($prevW2idx+$size);
              push @index, "$widx:".join(",",@indices);
              $prevW2idx += $size+1;
          } else {
              print "Dropping $w\n"; 
              #$pinyin = to_pinyin($w); 
              #if ($pinyin =~ /^\s*$/) {
              #   print STDERR "skipping $w - no pinyin available\n";
              #}
              #push @w2, $pinyin;
              #print STDERR "rough pinyin($line): $w->'$pinyin'\n";
          }
      }
      
      if ($#w2+1 == 0) {
          foreach my $w (@w) {
              $pinyin = to_pinyin($w);
              push @w2, $pinyin;
              @toks=split(/ /,$pinyin);
              $size = $#toks;
              @indices=$prevW2idx..($prevW2idx+$size);
              push @index, "$widx:".join(",",@indices);
              $prevW2idx += $size+1;
          }
          print STDERR "Transliterating the whole line: ".join(' ',@w)." --> ".join(' ',@w2)."\n";
      }
      print ofh join(' ',@w2)."\n";
      if ($opts{i} == 1) {
          print oifh join(' ',@index)."\n";
      }
  }
  close fh;
  close ofh;
  close oifh;
}
