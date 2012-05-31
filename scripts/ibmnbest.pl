#!/usr/bin/perl
$last_id =  "<<NONE>>";
while(<>) {
   chomp;
   @f = split / \|\|\| /;
   if ($last_id ne $f[0]) {
     if ($last_id ne "<<NONE>>") {     
       @st = sort {$h{$b} <=> $h{$a}} keys %h;
       foreach $t (@st) {
         if ($t ne "<Empty Sequence>") {
           print "$last_id ||| $t ||| $h{$t}\n";
         } else {
           print "$last_id ||| $t ||| -999\n";
         }
       }
     }
     $last_id = $f[0];
     undef %h;
   }
   if (not defined $h{$f[1]} or  $h{$f[1]} < $f[3]) {
     $h{$f[1]} = $f[3];
   }
}

@st = sort {$h{$b} <=> $h{$a}} keys %h;
foreach $t (@st) {
  if ($t ne "<Empty Sequence>") {
    print "$last_id ||| $t ||| $h{$t}\n";
  } else {
    print "$last_id ||| $t ||| -999\n";
  }
}
