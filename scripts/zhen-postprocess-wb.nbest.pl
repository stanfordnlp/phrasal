#!/usr/bin/perl

while (<>) {
  chomp;
  s/[\n\r]/ /g;
  s/ +/ /g;
  @f = split /\s*\|\|\|\s*/;
  $_ = $f[1];

############################################################
# C-E post processing script optimized for Dev12_wb tune and Dev10_wb dev
############################################################

############## v1:
# BLEU = 17.325, 52.816/22.922/11.929/6.592 (BP=0.986, ration=0.986 44950/45570) (Dev12_wb tune baseline)
# BLEU = 15.305, 51.370/21.188/10.457/5.369 (BP=0.973, ration=0.974 46562/47814) (Dev10_wb dev baseline)
#
# BLEU = 17.575, 53.766/23.579/12.375/6.866 (BP=0.970, ration=0.971 44227/45570) (best on Dev12_wb tune)
# BLEU = 15.479, 52.424/21.839/10.852/5.588 (BP=0.954, ration=0.955 45647/47814) (best on Dev10_wb dev)

# add a space at the beginning and end of lines, so we can always match with space on both sides
s=^= =g;
s=$= =g;

# mid-sentence-final punctuation, such as "- - ":
if(1) {
s/(-\s+){3,}/- /;
s/(\*+[ \t]+)+//;
}
#BLEU = 17.325, 52.836/22.931/11.934/6.595 (BP=0.986, ration=0.986 44933/45570)

# remove unmatched quote at the end
#if(1) {
#s/^([^"]+)\s*"\s*$/$1 \n/;
#}
#BLEU = 17.312, 52.837/22.924/11.928/6.593 (BP=0.985, ration=0.986 44912/45570)

# Sentence-final punctuation, such as "? .":
s/(\:|\?)\s*\.\s*$/$1 \n/;
# BLEU = 17.325, 52.836/22.931/11.934/6.595 (BP=0.986, ration=0.986 44933/45570)
#s/ [\,\;]\s*$/ \. \n/;
# BLEU = 17.287, 52.839/22.883/11.900/6.569 (BP=0.986, ration=0.986 44933/45570)

s/ - /-/g;
# BLEU = 17.541, 53.582/23.485/12.329/6.839 (BP=0.972, ration=0.972 44308/45570)

s/ , [eua]h / /g;
# BLEU = 17.545, 53.636/23.520/12.346/6.850 (BP=0.971, ration=0.971 44258/45570)

s/^\s*1 , /1 . /;
s/^\s*2 , /2 . /;
s/^three , /3 . /;
s/^four , /4 . /;
s/^five , /5 . /;
s/^six , /6 . /;
# BLEU = 17.552, 53.640/23.529/12.351/6.855 (BP=0.971, ration=0.971 44258/45570)

s/tantamount /the same as /;
s/ wimp / coward /;
s/viet nam /vietnam /g;
s/chief executive officer /ceo /;
# BLEU = 17.575, 53.766/23.579/12.375/6.866 (BP=0.970, ration=0.971 44227/45570)

# s/ \$ / yuan /;
# BLEU = 17.576, 53.777/23.579/12.375/6.866 (BP=0.970, ration=0.971 44227/45570)

# percent
s/([\d]+)% /$1 % /g;
# BLEU = 17.575, 53.766/23.579/12.375/6.866 (BP=0.970, ration=0.971 44227/45570)
# weird, clearly matches ref1 better, but no BLEU improvements

# north korea
s/ dprk / north korea /g;
# BLEU = 17.607, 53.843/23.639/12.384/6.866 (BP=0.971, ration=0.971 44255/45570)
# s/ the un / kim jong-un /g;
# BLEU = 17.613, 53.872/23.663/12.387/6.864 (BP=0.971, ration=0.971 44255/45570)

# ibm number error (often -- not if plain number, yes if zip code or telephone
s/(\d+),(\d+)/$1$2/g;
# BLEU = 17.614, 53.874/23.660/12.387/6.866 (BP=0.971, ration=0.971 44255/45570)

# [cdm] Don't have a comma initially, and if it is followed by a date, make it a preposition
s/^ *, +((?:january|february|march|april|may|june|july|august|september|october|november|december) +\d)/on $1/g;
s/^ *, +(january|february|march|april|may|june|july|august|september|october|november|december)/in $1/g;
s/^ *,//;

# [cdm] ruijie
s/ rui jie / ruijie /g;

# [cdm] south korea
s/ rok / south korea /g;

# subtract the space at the beginning and end of lines, and any other additional spaces
s=  += =g;
s=^ +==g;
s= +$==g;

# # Prevent empty lines, which make the official NIST scoring script crash:
# s=^\s*$=.\n=g;
  chomp;
  $f[1] = $_;
  if ($f[1] =~ /^\s*$/) {
      $f[1] = "EMPTY";
  }
  @fo = ($f[0], $f[1], $f[3]);
  $l = join " ||| ", @fo;
  print "$l\n";
}
