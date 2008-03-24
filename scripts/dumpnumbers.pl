#!/usr/bin/perl
#
# Segmentation independent extraction of numbers from a text
#
# Usage:
#
#    ./dumpnmbers.pl < source > numbers 2> non-numbers
#  
# Author Daniel Cer (Daniel.Cer@gmail.com)
#################################################################

use utf8;

binmode(STDIN, ':utf8');
binmode(STDOUT, ':utf8');
binmode(STDERR, ':utf8');

# 一四四一 ==> 1441
#
# 二百八十三 ==> 283
# 2.6亿 ==> 26,000 , 634万 ==> 6,340,000
# 百分之十一点一 ==> 11.1

$zh_numchars 
          = "負负". # - 
            "點点\\.".  
            "０0零〇".
            "１1一壹".
            "２2二貳贰兩两".
            "３3三參叄叁".
            "４4四肆".
            "５5五伍".
            "６6六陸陆".
            "７7七柒".
            "８8八捌".
            "９9九玖".
            "十拾".       # 10 
            "廿卄".       # 20 / 二十
            "卅".         # 30 / 三十  
            "卌".         # 40 / 四十
            "百佰".       # 100
            "千仟".       # 1,000
            "萬万".       # 10,000
            "億亿".       # 100,000,000
            "兆".         # 1,000,000,000,000
            "京".         # 10,000,000,000,000,000
            "第".         # -st, -nd, -rd, -th
            "分之".       # X 分之 Y ==> Y / X
            "個个";       # classifiers 


$en_numchars = 
            "0123456789\\.,eE%-";
            
$numchars = $zh_numchars.$en_numchars;

$ws = 1;
while(<>) { chomp;
  @chars = split //;
  foreach $char (@chars) {
     if ($char =~ /[$numchars]/) {
       print $char; 
       $ws = 0;
     } else {
         print stderr "$char";
         if (!$ws) {
           print " ";
           $ws = 1;
         }
     } 
  } 
  print stderr "\n";
  print "\n";
}
