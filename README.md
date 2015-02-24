# Phrasal: A statistical machine translation system

## Installation

Both ant and [Gradle](http://gradle.org) installation build scripts are provided. We strongly recommend that you use Gradle, which will fetch dependencies and build a jar for you.

The Phrasal decoder requires that you also install [Stanford CoreNLP](http://nlp.stanford.edu/software/corenlp.shtml). We recommend that you clone and build [the latest repository from Github](https://github.com/stanfordnlp/CoreNLP).

Some advanced Phrasal features have external dependencies. If you build Phrasal with Gradle, these dependencies will be retrieved for you. Otherwise, a script to fetch and configure these dependencies is included in `scripts/get-dependencies.sh`.

### Linux

1. Set the CORENLP_HOME environment variable to the root of the CoreNLP repository, which should have been built.

2. `gradle build`

3. (Optional) Build Eclipse project files by typing: `gradle eclipse`.

4. (Optional, requires [JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html)) Build the KenLM loader: `gradle compileKenLM`.

### Windows

We recommend installation of Cygwin. 

### Stanford NLP cluster

1. Ensure that JAVANLP_HOME is set.

2. `ant all`


## License

Phrasal is licensed under the GPL. For details, please see the file LICENSE.txt in the root directory of this software package.

Copyright (c) 2007-2014 The Board of Trustees of The Leland Stanford Junior University. All Rights Reserved.


## Contributors

    * Daniel Cer (original author)
    * Michel Galley
    * Spence Green
    * John Bauer
    * Chris Manning


## Documentation / User Guide

The [user guide](http://www-nlp.stanford.edu/wiki/Software/Phrasal) for complete installation and configuration instructions. The guide also
contains a tutorial for building an MT system from raw text.

## Support

We have 3 mailing lists for Phrasal, all of which are shared with other JavaNLP
tools (with the exclusion of the parser). 

Each address is at @lists.stanford.edu:

java-nlp-user: This is the best list to post to in order to ask questions, make
announcements, or for discussion among JavaNLP users. You have to subscribe to 
be able to use it. Join the list via this webpage or by emailing 
java-nlp-user-join@lists.stanford.edu. (Leave the subject and message body 
empty.) You can also look at the list archives.

java-nlp-announce: This list will be used only to announce new versions of 
Stanford JavaNLP tools. So it will be very low volume (expect 1-3 message a 
year). Join the list via via this webpage or by emailing 
java-nlp-announce-join@lists.stanford.edu. (Leave the subject and message 
body empty.)

java-nlp-support: This list goes only to the software maintainers. It's a good 
address for licensing questions, etc. For general use and support questions, 
please join and use java-nlp-user. You cannot join java-nlp-support, but you 
can mail questions to java-nlp-support@lists.stanford.edu.

