#!/usr/bin/perl
# usage: make_html_analysis.pl [unzipped nbest list] [chinese source file] [references] [output prefix]


open "alignment info: $ARGV[0]\n";
open fh, $ARGV[0] or die;
$last_id = -1;
while(<fh>) {
  chomp;
  @fields = split / \|\|\| /; 
  if ($last_id != $fields[0]) {
    $last_id = $fields[0];
    $text = $fields[1];
    $align = $fields[4];
    $trans[$fields[0]] = $text;
    $transAlign[$fields[0]] = $align;
  }
}
close fh;

open fh, $ARGV[1] or die;
while(<fh>) {
  chomp;
  push @zh, $_;
}
close fh;

open fh, $ARGV[2] or die;
while(<fh>) {
  chomp;
  s/\$ num \_ \( [^)]+ ([\S]+) \)/\1/g;
  push @ref, $_;
}
close fh;

#for ($i = 0; $i <= 10; $i++) {
#   $sim = 
#}

$page = 1;
print "size: $#transAlign\n";
print "ofh: $ARGV[3]\n";
open ofh, ">$ARGV[3].$page.html" or die;
for ($i = 0; $i <= $#transAlign; $i++) {
  if ($i % 25 == 0) {
     print ofh "<strong>Page: </strong>";
     for ($j = 1; $j <= $linkcnt; $j++) {
       print ofh "<a href=\"$ARGV[3].$j.html\">$j</a>&nbsp;";
     }
     print ofh "<br/>";
     close ofh;
     open ofh, ">$ARGV[3].$page.html" or die;
     $page++;
     $linkcnt = int(($#transAlign+1)/25);
     print ofh "<strong>Page: </strong>";
     for ($j = 1; $j <= $linkcnt; $j++) {
       print ofh "<a href=\"$ARGV[3].$j.html\">$j</a>&nbsp;";
     }
     print ofh "<br/>";
  }
  print ofh "<strong> id: </strong> $i <br/>\n";
  print ofh "<strong> Translation: </strong> $trans[$i] <br/>\n";
  print ofh "<strong> Source: </strong> $zh[$i] <br/>\n";
  print ofh "<strong> Ref: </strong> $ref[$i] <br/>\n";
  print ofh "<table border=\"1\" style=\"border: 1px solid black\">";
  @tw = split /\s+/, $trans[$i];
  @sw = split /\s+/, $zh[$i];
  print ofh "<tr><td>Source\\Translation</td>\n";
  @af = split /\s/, $transAlign[$i];
  $idx = -1;
  undef %am;
##  print "$transAlign[$i]\n";
  for ($j = 0; $j <= $#af; $j++) {
    @f = split /=/, $af[$j];
    @ff = split /-/, $f[0];
    if ($#ff == 0) {
      $ff[1] = $ff[0];
    } 
##    print "<br/> $af[$j] (idx: $idx, f[1]: $f[1], ff[0]: $ff[0], ff[1]: $ff[1]): <br/> \n";
    for ($a = $ff[0]; $a <= $ff[1]; $a++) {
      for ($b = $idx+1; $b <= $f[1]; $b++) {
         $am{"$a,$b"} = 1;
##         print "am(s: $a, t: $b) =1 <br/>\n";
      } 
    }
    $idx = $f[1];
  }
  for ($j = 0; $j <= $#tw; $j++) {
    print ofh "<td>$tw[$j]</td>\n"
  }
  print ofh "</tr>\n";
  for ($j = 0; $j <= $#sw; $j++) {
    print ofh "<tr>\n";
    print ofh "<td> $sw[$j] </td>\n";
    for ($k = 0; $k <= $#tw; $k++) {
       if ($am{"$j,$k"}) {
         print ofh "<td style=\"background-color: black; color: black;\"> X  </td>";
       } else { 
         print ofh "<td> &nbsp; </td>";
       }
    }
    print ofh "</tr>\n";
  }
  print ofh "</table>\n";
  print ofh "<br/><br/>\n";
}
close ofh;
