#!/usr/bin/env bash
#
# Collect counts from a large corpus broken into many files.
# Uses English tokenization options.
#
if [ $# -lt 1 ]; then
    echo Usage: `basename $0` file "[files]"
    exit -1
fi

mkdir -p counts

#EXEC="bash -x"
EXEC="nlpsub -pbackground -qshort -c2 bash -x"

EN_TOK="java -server -XX:+UseCompressedOops -XX:MaxPermSize=2g edu.stanford.nlp.process.PTBTokenizer -preserveLines -lowerCase -options ptb3Escaping=false,asciiQuotes=true"

SCRIPT_DIR=${JAVANLP_HOME}/projects/mt/ptm/scripts/
fixnl="python2.7 ${SCRIPT_DIR}/mt/cleanup_txt.py"
count="python2.7 ${SCRIPT_DIR}/wrangle/token_counts.py"

throttle(){
	qjobs=`qstat -u spenceg | grep spenceg | wc -l`
	while [ $qjobs -gt 20 ]
	do
		sleep 10
		qjobs=`qstat -u spenceg | grep spenceg | wc -l`
	done
}

for infile in $*
do
    echo $infile
    outfile=$(basename "$infile")
		ext="${infile##*.}"
		CAT=cat
		if [ $ext == "gz" ]; then
			CAT=zcat
		fi
    echo "$CAT $infile | sed -e 's/[[:cntrl:]]/ /g' | $fixnl | $EN_TOK | $count > counts/"$outfile".counts" > "$outfile".sh
    $EXEC "$outfile".sh
    throttle
done
