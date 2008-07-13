SRILM_ROOT=/u/nlp/packages/SRILM-1.5.6/
SRILM_LIBS=$(SRILM_ROOT)/lib/i686
SRILM_INC=$(SRILM_ROOT)/include
JAVA_INC=/apps/share/jdk1.6.0_03/include/ 
JAVA_INC_OS=/apps/share/jdk1.6.0_03/include/linux 

#java
java: clean libsrilm.so

libsrilm.so: srilm.o srilm_java_wrap.o
	g++ -shared $^ -loolm -ldstruct -lmisc -L$(SRILM_LIBS) -o $@

srilm_java_wrap.o: srilm_java_wrap.cxx
	g++ -c -fpic $< -I/usr/local/include/ -I$(SRILM_INC) -I$(JAVA_INC) -I$(JAVA_INC_OS)


srilm_java_wrap.cxx: srilm_java.i
	swig -c++ -java -package ckyDecoder -outdir . $<



#general
srilm.o: srilm.c
	g++ -c -fpic $< -I/usr/local/include/ -I$(SRILM_INC)

clean:
	\rm -fr srilm.o srilm_*_wrap.* *.so srilm.py* srilm.pm SWIGTYPE*.java srilm*.java
