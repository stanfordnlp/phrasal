#change this
SRILM_ROOT=/scr/mgalley/research/current/dtu/joshua-2008-05/srilm
SRILM_LIBS=$(SRILM_ROOT)/lib/i686-m64
SRILM_INC=$(SRILM_ROOT)/include

#change this
JAVA_INC=/u/nlp/packages/java/jdk1.6-current-x86_64/include
JAVA_INC_OS=$(JAVA_INC)/linux

#### input file: srilm.h, srilm.c, and srilm_java.i

#java
java: clean libsrilm.so

libsrilm.so: srilm.o srilm_java_wrap.o
	g++ -fPIC -shared $^ -loolm -ldstruct -lmisc -L$(SRILM_LIBS) -o $@

srilm_java_wrap.o: srilm_java_wrap.cxx
	g++ -c -fpic $< -I/usr/local/include/ -I$(SRILM_INC) -I$(JAVA_INC) -I$(JAVA_INC_OS)


srilm_java_wrap.cxx: srilm_java.i
	swig -c++ -java -package mt.srilm -outdir . $<

#general
srilm.o: srilm.c
	g++ -c -fpic $< -I/usr/local/include/ -I$(SRILM_INC)

clean:
	\rm -rf srilm.o srilm*.java srilm_*_wrap.* SWIGTYPE*.java libsrilm.so 
