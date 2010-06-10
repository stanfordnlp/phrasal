#!/usr/bin/perl -w

$JAVA="java -Dfile.encoding=utf-8 -cp \`cygpath -wp projects/core/classes/:projects/mt/classes/\`";
for($i=1; $i<=325; $i++) {
    $cmd = sprintf("$JAVA mt.classifyde.TranslationAlignment %d -origTAdir output/origWAs/ -npOutputDir output/WAs/NPs/ > output/WAs/align%03d.html 2> output/WAs/err/align%03d.err", $i, $i, $i);
    print $cmd,"\n";
    `$cmd`;
}
