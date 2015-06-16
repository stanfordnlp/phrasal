# Phrasal: A statistical machine translation system

Phrasal is licensed under the GPL. For details, please see the file LICENSE.txt in the root directory of this software package.

Copyright (c) 2007-2015 The Board of Trustees of The Leland Stanford Junior University. All Rights Reserved.

## Installation

[Gradle](http://gradle.org) installation build scripts are provided. Gradle will fetch dependencies and build a jar for you.

Phrasal depends on [Stanford CoreNLP](http://nlp.stanford.edu/software/corenlp.shtml). We recommend that you clone and build [the latest repository from Github](https://github.com/stanfordnlp/CoreNLP).

### Linux / MacOS

1. Set the CORENLP_HOME environment variable to the root of the CoreNLP repository, which contains a Gradle script. Phrasal's Gradle script will execute the CoreNLP script for you.

2. `gradle installDist`

3. (Optional) Build Eclipse project files by typing: `gradle eclipse`.

4. (Optional, requires g++, [JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html)) Build the KenLM loader: `gradle compileKenLM`.

5. (Optional, requires g++, JDK, and Boost) Build the KenLM language model estimation tools: `gradle compileKenLMtools`.

### Windows

Follow the Linux instructions above. Then be sure to run `gradle startupScripts` to generate a .bat file for running Phrasal.

### Stanford NLP cluster

We used to commit some third-party dependencies to the repository, and for others we had a bash script that would try to wget the rest. The script would fail when URLs for archived versions of libraries would change (this was especially true for jetty). Now we use Gradle to fetch the dependencies and build the system.

1. Download and unpack [Gradle](http://gradle.org) into your home directory, or into /u/nlp/packages.

2. Add Gradle to PATH: `export PATH="$PATH":/path/to/gradle/bin`

3. Set reference to JavaNLP: `export CORENLP_HOME=/u/username/NLP-HOME/javanlp/projects/core`

4. `gradle installDist`

6. Update CLASSPATH: `export CLASSPATH=/u/username/NLP-HOME/phrasal/build/install/phrasal/lib/*`

## Citation

If you use Phrasal for research, then please cite the following paper:

```
@inproceedings{Green2014,
 author = {Spence Green and Daniel Cer and Christopher D. Manning},
 title = {Phrasal: A Toolkit for New Directions in Statistical Machine Translation},
 booktitle = {In Proceddings of the Ninth Workshop on Statistical Machine Translation},
 year = {2014}
}
```

## Documentation / User Guide

The [user guide](http://www-nlp.stanford.edu/wiki/Software/Phrasal) for complete installation and configuration instructions. The guide also
contains a tutorial for building an MT system from raw text.

## Support

We have 3 mailing lists for Phrasal, all of which are shared with other JavaNLP
tools (with the exclusion of the parser). 

Each address is at @lists.stanford.edu:

*java-nlp-user* -- This is the best list to post to in order to ask questions, make
announcements, or for discussion among JavaNLP users. You have to subscribe to 
be able to use it. Join the list via this webpage or by emailing 
java-nlp-user-join@lists.stanford.edu. (Leave the subject and message body 
empty.) You can also look at the list archives.

*java-nlp-announce* -- This list will be used only to announce new versions of 
Stanford JavaNLP tools. So it will be very low volume (expect 1-3 message a 
year). Join the list via via this webpage or by emailing 
java-nlp-announce-join@lists.stanford.edu. (Leave the subject and message 
body empty.)

*java-nlp-support* -- This list goes only to the software maintainers. It's a good 
address for licensing questions, etc. For general use and support questions, 
please join and use java-nlp-user. You cannot join java-nlp-support, but you 
can mail questions to java-nlp-support@lists.stanford.edu.
