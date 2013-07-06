#!/usr/bin/env bash

CP=/home/rayder441/sandbox/javanlp/projects/core/classes:/home/rayder441/sandbox/javanlp/projects/mt/classes:/home/rayder441/sandbox/javanlp/projects/mt/lib/*:/home/rayder441/sandbox/javanlp/projects/more/classes:/home/rayder441/sandbox/javanlp/projects/mt/lib-research/*:

# Add the static content for debugging
#CP="$CP":/home/rayder441/sandbox/javanlp/projects/mt/src-research/


java -Xmx1g -Xms1g -server -cp "$CP" edu.stanford.nlp.mt.tools.service.PhrasalService -d
