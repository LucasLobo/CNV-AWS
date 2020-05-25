# HOW TO RUN

* Java Environment variables need to be set.
    * RNL:
        ```
        export JAVA_HOME=/usr/lib/jvm/java-7-openjdk-amd64/  
        export JAVA_ROOT=/usr/lib/jvm/java-7-openjdk-amd64/  
        export JDK_HOME=/usr/lib/jvm/java-7-openjdk-amd64/  
        export JRE_HOME=/usr/lib/jvm/java-7-openjdk-amd64/jre  
        export PATH=/usr/lib/jvm/java-7-openjdk-amd64/bin/:$PATH  
        export SDK_HOME=/usr/lib/jvm/java-7-openjdk-amd64/  
        export _JAVA_OPTIONS="-XX:-UseSplitVerifier "$_JAVA_OPTIONS
        ``` 
    * ec2-user:
        ```
        export _JAVA_OPTIONS="-XX:-UseSplitVerifier "$_JAVA_OPTIONS
        ```
* Download AWS Java SDK into home folder and unzip it:
    ```
    wget http://sdk-for-java.amazonwebservices.com/latest/aws-java-sdk.zip  
    unzip aws-java-sdk.zip
    ```

* Place project files in home folder:
    * RNL:
        Download ```cnv-project19-20``` github into home folder.
    * ec2-user:
        Download ```cnv-project19-20/codebase/SolverInstance``` github folder **CONTENTS** into home folder.


* Update CLASSPATH (fix AWS version number if needed).
    * RNL:
        ```
        export CLASSPATH="/home/test/cnv-project19-20/codebase/SolverInstance"
        export CLASSPATH="$CLASSPATH:/home/test/cnv-project19-20/codebase/SolverInstance/instrumented/"
        export CLASSPATH="$CLASSPATH:/home/test/cnv-project19-20/codebase/SolverInstance/project/"
        export CLASSPATH="$CLASSPATH:/home/test/aws-java-sdk-1.11.789/lib/aws-java-sdk-1.11.789.jar"
        export CLASSPATH="$CLASSPATH:/home/test/aws-java-sdk-1.11.789/third-party/lib/*"
        ```

    * ec2-user:
        ```
        export CLASSPATH="/home/ec2-user"
        export CLASSPATH="$CLASSPATH:/home/ec2-user/instrumented/"
        export CLASSPATH="$CLASSPATH:/home/ec2-user/project/"
        export CLASSPATH="$CLASSPATH:/home/ec2-user/aws-java-sdk-1.11.789/lib/aws-java-sdk-1.11.789.jar"
        export CLASSPATH="$CLASSPATH:/home/ec2-user/aws-java-sdk-1.11.789/third-party/lib/*"
        ```
* Create AWS credentials folder in home folder and place security key.

* Go to the correct folder:
    * RNL: ```cnv-project19-20/codebase/SolverInstance```

    * ec2-user: ```home```

* To recompile BIT (MethodCounterLocal is only to be used without the WebServer!):
    ```
    javac ./BIT/MethodCounter.java
    javac ./BIT/MethodCounterLocal.java
    ```

* To recompile the solvers:
    ```
    javac ./project/pt/ulisboa/tecnico/cnv/solver/*.java
    ```

* To reinstrument the code (MethodCounterLocal is only to be used without the WebServer!):
    ```
    java BIT.MethodCounter ./project/pt/ulisboa/tecnico/cnv/solver/ ./instrumented/pt/ulisboa/tecnico/cnv/solver/
    java BIT.MethodCounterLocal ./project/pt/ulisboa/tecnico/cnv/solver/ ./instrumented/pt/ulisboa/tecnico/cnv/solver/
    ```

* To recompile the WebServer:
    ```
    javac ./project/pt/ulisboa/tecnico/cnv/server/WebServer.java
    ```

* To run Solver directly (instrumentation is only displayed if using MethodCounterLocal):
    ```
    java pt.ulisboa.tecnico.cnv.solver.SolverMain -n1 9 -n2 9 -un 81 -i SUDOKU_PUZZLE_9x19_101 -s DLX -b [[2,0,0,8,0,5,0,9,1],[9,0,8,0,7,1,2,0,6],[0,1,4,2,0,3,7,5,8],[5,0,1,0,8,7,9,2,4],[0,4,9,6,0,2,0,8,7],[7,0,2,1,4,9,3,0,5],[1,3,7,5,0,6,0,4,9],[4,2,5,0,1,8,6,0,3],[0,9,6,7,3,4,0,1,2]]
    ```

* To run the Web Server at port 8000:
    ```
    java pt.ulisboa.tecnico.cnv.server.WebServer
    ```

* Final image should be saved to be used by Autoscaler.