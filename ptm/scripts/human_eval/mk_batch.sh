#!/usr/bin/env bash
#
# Make uploadinfo file from a batch specification.
#
if [ $# -ne 2 ]; then
    echo Usage: `basename $0` batch_specs server_info
    exit -1
fi

classpath=${JAVANLP_HOME}/projects/mt/ptm/maise/lib

java -Xmx300m -cp $classpath CreateBatches serverInfo=$2 batchInfo=$1 templateLoc=maintaskfiles/

# Update the URL in the generated question file.
qpath=`dirname $2`
for qfile in `ls ${qpath}/*.question`
do
    echo Updating $qfile
    cat $qfile | perl -ne 's/www\.YOUR-HOST\.com\/PATH\/TO\/YOUR\/HOSTING\/LOCATION/nlp\.stanford\.edu\/spenceg/g; print' > "$qpath"/file.tmp
    mv -f "$qpath"/file.tmp $qfile
done
