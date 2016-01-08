# Phrasal: A statistical machine translation system

Phrasal is licensed under the GPL (v3+). For details, please see the file LICENSE.txt in the root directory of this software package.

Copyright (c) 2007-2016 The Board of Trustees of The Leland Stanford Junior University. All Rights Reserved.

## Installation

We use [Gradle](http://gradle.org) to build Phrasal. Gradle will
install all dependencies.  If you are on OS X, the easiest way to get
Gradle is to install Homebrew and then to type `brew install gradle`.

### Linux / Mac OS X

These instructions assume you are using the `bash` shell, which is
usually the default shell.

1. Switch to the root of the Phrasal repository and execute: `gradle installDist`

1. Set PHRASAL_HOME: `export PHRASAL_HOME=``pwd`` `

1. Set CLASSPATH: `export CLASSPATH=$PHRASAL_HOME/build/install/phrasal/lib/*`

1. (Optional) Build Eclipse project files by executing: `gradle eclipse`.

1. (Optional, requires g++, [JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html)) Build the KenLM loader: `gradle compileKenLM`.

1. (Optional, requires g++, JDK, and Boost) Build the KenLM language model estimation tools: `gradle compileKenLMtools`.

### Windows

Follow the Linux instructions above. Then be sure to execute `gradle startupScripts` to generate a .bat file.

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

See [the user guide](http://www-nlp.stanford.edu/wiki/Software/Phrasal) for complete installation and configuration instructions. The guide also contains a tutorial for building an MT system from raw text.

## Support

We have 3 mailing lists for Phrasal, all of which are shared with other Stanford JavaNLP
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
