#!/usr/bin/perl
#
# Usage:
#
# ./construct_pinyin_table.pl < Unihan.txt > pinyin_table 2> log
#
# Author: Daniel Cer (Daniel.Cer@gmail.com)
##################################################################

use utf8;

binmode(STDIN, ":utf8");
binmode(STDOUT, ":utf8");
binmode(STDERR, ":utf8");

print "# Automatically generated from Unihan\n";
print "# Date: ".`date`;

$line = 0;
while (<STDIN>) { chomp; $line++;
  @fields = split /\s+/;
  next unless ($fields[1] eq "kMandarin");
  $char_hex = $fields[0];
  $char_hex =~ s/U\+//g;
  $char = chr(hex $char_hex);
  $pinyin = "";
  for ($i = 2; $i <= $#fields; $i++) {
     $pinyin .= " " if ($i > 2);
     $pinyin .= lc($fields[$i]); 
  }
  if ($h{$char}) {
     print stderr "$char already defined as $h{$char}, redefining as $pinyin\n";
  }
  $h{$char} = "$pinyin # line: $line"; 
}

foreach $char (sort keys %h) {
  print "$char $h{$char}\n";
}
