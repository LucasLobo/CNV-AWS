export JAVA_HOME=/usr/lib/jvm/java-7-openjdk-amd64/
export JAVA_ROOT=/usr/lib/jvm/java-7-openjdk-amd64/
export JDK_HOME=/usr/lib/jvm/java-7-openjdk-amd64/
export JRE_HOME=/usr/lib/jvm/java-7-openjdk-amd64/jre
export PATH=/usr/lib/jvm/java-7-openjdk-amd64/bin/:$PATH
export SDK_HOME=/usr/lib/jvm/java-7-openjdk-amd64/
export _JAVA_OPTIONS="-XX:-UseSplitVerifier "$_JAVA_OPTIONS

# CLASSPATH EC2-USER
export CLASSPATH="/home/ec2-user"
export CLASSPATH="$CLASSPATH:/home/ec2-user/instrumented/"
export CLASSPATH="$CLASSPATH:/home/ec2-user/project/"

# CLASSPATH RNL-VM
export CLASSPATH="/home/test/cnv-project19-20/codebase/SolverInstance"
export CLASSPATH="$CLASSPATH:/home/test/cnv-project19-20/codebase/SolverInstance/instrumented/"
export CLASSPATH="$CLASSPATH:/home/test/cnv-project19-20/codebase/SolverInstance/project/"


# BIT (should not be necessary)
javac ./BIT/MethodCounter.java
javac ./BIT/MethodCounterLocal.java

# Compile Solvers
javac ./project/pt/ulisboa/tecnico/cnv/solver/*.java

# Instrument classes (only if Solvers and/or BIT were recompiled)
java BIT.MethodCounter ./project/pt/ulisboa/tecnico/cnv/solver/ ./instrumented/pt/ulisboa/tecnico/cnv/solver/
java BIT.MethodCounterLocal ./project/pt/ulisboa/tecnico/cnv/solver/ ./instrumented/pt/ulisboa/tecnico/cnv/solver/

# Compile WebServer
javac ./project/pt/ulisboa/tecnico/cnv/server/WebServer.java

# Test SolverMain Directly
java pt.ulisboa.tecnico.cnv.solver.SolverMain -n1 9 -n2 9 -un 81 -i SUDOKU_PUZZLE_9x19_101 -s DLX -b [[2,0,0,8,0,5,0,9,1],[9,0,8,0,7,1,2,0,6],[0,1,4,2,0,3,7,5,8],[5,0,1,0,8,7,9,2,4],[0,4,9,6,0,2,0,8,7],[7,0,2,1,4,9,3,0,5],[1,3,7,5,0,6,0,4,9],[4,2,5,0,1,8,6,0,3],[0,9,6,7,3,4,0,1,2]]

# Start Webserver
java -XX:-UseSplitVerifier pt.ulisboa.tecnico.cnv.server.WebServer
