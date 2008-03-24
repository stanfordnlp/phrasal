#!/usr/bin/perl

use utf8;

binmode(STDIN,":utf8");
binmode(STDOUT,":utf8");
binmode(STDERR,":utf8");

if (@ARGV != 3) {
   print stderr "Usage:\n\t$0 (source.vcb) (target.vcb) (.t)\n";
   exit -1;
}

print stderr "reading $ARGV[0]....\n";

open srcfh, $ARGV[0] or die;
binmode(srcfh, ':utf8');

while (<srcfh>) { chomp; @fields = split /\s+/; $srcw{$fields[0]} = $fields[1]; }
close srcfh;

$srcw{0} = "<<<NULL>>>";

print stderr "reading $ARGV[1]....\n";
open trgfh, $ARGV[1] or die;
binmode(trgfh, ':utf8');
while (<trgfh>) { chomp; @fields = split /\s+/; $trgw{$fields[0]} = $fields[1]; }
close trgfh;
$trgw{0} = "<<<NULL>>>";

print stderr "reading $ARGV[2]....\n";
open tfh, $ARGV[2] or die;
binmode(tfh, ':utf8');
while (<tfh>) { chomp; $line++; 
  print stderr "line > $line\n" if (!($line % 1000));
  @fields = split /\s+/;
  $pairs{"$srcw{$fields[0]}\t$trgw{$fields[1]}"} = $fields[2];
}
close tfh;

print stderr "sorting....\n";
@keys = sort {$pairs{$b} <=> $pairs{$a}} keys %pairs;

foreach $key (@keys) {
  print "$key\t$pairs{$key}\n";
}
