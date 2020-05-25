# RNL
export JAVA_HOME=/usr/lib/jvm/java-7-openjdk-amd64/
export JAVA_ROOT=/usr/lib/jvm/java-7-openjdk-amd64/
export JDK_HOME=/usr/lib/jvm/java-7-openjdk-amd64/
export JRE_HOME=/usr/lib/jvm/java-7-openjdk-amd64/jre
export PATH=/usr/lib/jvm/java-7-openjdk-amd64/bin/:$PATH
export SDK_HOME=/usr/lib/jvm/java-7-openjdk-amd64/
export _JAVA_OPTIONS="-XX:-UseSplitVerifier "$_JAVA_OPTIONS

# INSTANCE
export _JAVA_OPTIONS="-XX:-UseSplitVerifier "$_JAVA_OPTIONS

# CLASSPATH EC2-USER
export CLASSPATH="/home/ec2-user/project"
export CLASSPATH="$CLASSPATH:/home/ec2-user/aws-java-sdk-1.11.789/lib/aws-java-sdk-1.11.789.jar"
export CLASSPATH="$CLASSPATH:/home/ec2-user/aws-java-sdk-1.11.789/third-party/lib/*"

# CLASSPATH RNL-VM
export CLASSPATH="/home/test/cnv-project19-20/codebase/LBInstance/project"
export CLASSPATH="$CLASSPATH:/home/test/cnv-project19-20/codebase/LBInstance/aws-java-sdk-1.11.787/lib/aws-java-sdk-1.11.787.jar"
export CLASSPATH="$CLASSPATH:/home/test/cnv-project19-20/codebase/LBInstance/aws-java-sdk-1.11.787/third-party/lib/*"

# Compile WebServer
javac ./project/pt/ulisboa/tecnico/cnv/server/WebServer.java

# Start Webserver
java pt.ulisboa.tecnico.cnv.server.WebServer
