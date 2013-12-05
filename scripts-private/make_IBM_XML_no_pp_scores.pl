#!/usr/bin/perl

if ($#ARGV != 3) {
   print stderr "Usage:\n\t$0 [unzipped nbest list] [chinese source file] [system name] [section name]\n";
   exit -1;
}

sub escape_xml {
  my ($line) = (@_);
  $line =~ s/&/&amp;/g;
  $line =~ s/"/&quot;/g;
  $line =~ s/'/&apos;/g;
  $line =~ s/</&lt;/g;
  $line =~ s/>/&gt;/g;
  return $line;
}

$sys_name = $ARGV[2];
$sect_name = $ARGV[3];

open fh, $ARGV[1] or die "can't open $ARGV[1]";
while(<fh>) {
  chomp;
  push @zh, $_;
}
close fh;


open fh, $ARGV[0] or die "can't open $ARGV[0]";
print "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
print "<translations sysid=\"$sys_name\" set=\"$sect_name\">\n";
$id = -1;
$rank = -1;
while(<fh>) {
  chomp;
  @fields = split / \|\|\| /; 
  $text = $fields[1];
  $align = $fields[4];
  if ($id != $fields[0]) {
     if ($id != -1) {
       print "</nbest>\n";
       print "</seg>\n";
     }
     $id = $fields[0];
     print "<seg id=\"$id\">\n";
     print "<src>\n";
     @zh_toks = split /\s+/, $zh[$id];
     for ($i = 0; $i <= $#zh_toks; $i++) {
         print "  <tok id=\"$i\">".escape_xml($zh_toks[$i])."</tok>\n";
     }
     print "</src>\n";
     print "<nbest count=\"200\">\n";
     $rank = 0;
  }
  print "<hyp rank=\"$rank\" score=\"$fields[3]\">\n";
  @af = split /\s/, $align;
  @tw = split /\s+/, $text;
  $target_idx = 0;
  for ($j = 0; $j <= $#af; $j++) {
    @f = split /=/, $af[$j];
    $src_idx = $f[0];
    $trg_max  = $f[1];
    print "  <t score=\"0\" srcidx=\"$src_idx\" type=\"phrase\"> "; 
    for ( ; $target_idx <= $trg_max; $target_idx++) {
      print escape_xml($tw[$target_idx])." "; 
    }
    print "</t>\n"; 
  }
  print "<\/hyp>\n";
  $rank++;
}
close fh;

if ($id != -1) {
  print "</nbest>\n";
  print "</seg>\n";
}
print "</translations>";
