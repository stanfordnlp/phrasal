#change this
SRILM_ROOT=/u/mgalley/private/joshua/srilm
SRILM_LIBS=$(SRILM_ROOT)/lib/i686-m64
SRILM_INC=$(SRILM_ROOT)/include

#change this
JAVA_INC=/apps/share/jdk1.6.0_03/include/
JAVA_INC_OS=/apps/share/jdk1.6.0_03/include/linux 

#### input file: srilm.h, srilm.c, and srilm_java.i

#java
java: clean libsrilm.so

libsrilm.so: srilm.o srilm_java_wrap.o
	g++ -fPIC -shared $^ -loolm -ldstruct -lmisc -L$(SRILM_LIBS) -o $@

srilm_java_wrap.o: srilm_java_wrap.cxx
	g++ -c -fpic $< -I/usr/local/include/ -I$(SRILM_INC) -I$(JAVA_INC) -I$(JAVA_INC_OS)


srilm_java_wrap.cxx: srilm_java.i
	swig -c++ -java -package edu.jhu.ckyDecoder -outdir . $<



#general
srilm.o: srilm.c
	g++ -c -fpic $< -I/usr/local/include/ -I$(SRILM_INC)

clean:
	\rm -rf srilm.o srilm*.java srilm_*_wrap.* SWIGTYPE*.java libsrilm.so 
