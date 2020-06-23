JAVAC=javac
JAVA=java
ANT=ant
JAR=jar

SRC_PATH=src/main/java
TARGET_DIR=target/classes
#JAVA_FILES=`find src/main/java -name "*.java"`
JAVA_FILES := $(shell find src/main/java -name '*.java') 
CLASSPATH=`find lib -name "*.jar" -printf "%p:" | sed "s/:$$//"`
RUNCLASSPATH=$(TARGET_DIR):$(CLASSPATH)

include Makefile.sys

ifeq "$(SYS)" "mingw"
CLASSPATH=`find lib -name "*.jar" -printf "%p;" | sed "s/;$$//"`
RUNCLASSPATH="$(TARGET_DIR);$(CLASSPATH)"
endif

.PHONY: all
all: build

.PHONY: build-2
build-2:
	mkdir -p $(TARGET_DIR)
	javac -target 1.6 -source 1.6 -sourcepath $(SRC_PATH) -d $(TARGET_DIR) -classpath $(CLASSPATH) $(JAVA_FILES)		
	cp -rf src/main/resources/* $(TARGET_DIR)

.PHONY: build
build:
	ant -f build/build.xml 

.PHONY: clean
clean:
	rm -rf target

.PHONY: visualizer
visualizer:
	$(JAVA) -ea -classpath $(RUNCLASSPATH) fi.hut.cs.treelib.gui.TreeVisualizer

.PHONY: loc
loc: 
	echo Java LOC: `find src/ -name "*.java" -print0 | xargs -0 cat | wc -l` 

.PHONY: sloc
sloc:
	sloccount src	

.PHONY: test-clean
test-clean:
	$(ANT) -f build/build-test.xml testrunner-clear

.PHONY: test-init
test-init:
	$(ANT) -f build/build-test.xml testrunner-init

.PHONY: test-run
test-run:
	$(ANT) -f build/build-test.xml testrunner-run
	
.PHONY: test
test: test-init test-run

treelib.jar: $(TARGET_DIR) $(TARGET_DIR)/* 
	rm -f treelib.jar
	cd $(TARGET_DIR) && $(JAR) cf ../../treelib.jar *  

DBS=cmvbt tsb tsb-iks tsb-wob tmvbt mvvbt
#DBS=cmvbt tsb 
STATES=initial del-50 del-100

.PHONY: stats
stats:
	rm -f stats.txt
	for db in $(DBS) ; do \
		for s in $(STATES) ; do \
			echo State $$s for $$db >>stats.txt ; \
			FILE=`grep 'db/.*[.]db' src/main/resources/exec-$$db.xml | sed 's/.*\"\(db\/.*.db\)\".*/\\1/g'` ;\
			rm $$FILE ; \
			cp $$FILE.$$s $$FILE ; \
			$(JAVA) -ea -classpath $(RUNCLASSPATH) fi.hut.cs.treelib.operations.OperationExecutor exec-$$db.xml stats 2>>stats.txt ;\
			echo >>stats.txt ;\
		done; \
	done

	echo
	cat stats.txt 

.PHONY: unpack
unpack:
	mkdir -p $(TARGET_DIR)
	cd $(TARGET_DIR) && $(JAR) xf ../../treelib.jar
