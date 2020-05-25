# LOAD BALANCER INSTANCE

In an EC2 instance, we will have a directory structured like this:

```
ec2-user
|
home
├── aws-java-sdk-1.XX.XXX
│   └── ...
└── project
    └── ...
```

In the ec2-user/home directory:
1) Download the AWS Java SDK from http://sdk-for-java.amazonwebservices.com/latest/aws-java-sdk.zip  and unzip it (wget recommended).

2) Download Java 7 and setup environment variables. ```export _JAVA_OPTIONS="-XX:-UseSplitVerifier "$_JAVA_OPTIONS```

3) Point your Java classpath to the AWS SDK and to the third party libs included:
  
        export CLASSPATH=$CLASSPATH:path-to-sdk/lib/aws-java-sdk-1.XX.YYY.jar:path-to-sdk/third-party/lib/*:~/project/

  or update if newer version is returned.

4) Create an ".aws" folder on your home folder. ( ~/.aws/ )

5) Create a file called "credentials" in the .aws folder containing:

        [default]

        aws_access_key_id=<your-aws-access-key-id>

        aws_secret_access_key=<your-aws-secret-access-key>
        
        aws_session_token=<your-aws_session_token>
  
Take note: the file must follow this exact syntax.

6) Update CLASSPATH:

        export CLASSPATH="/home/ec2-user/project"
        export CLASSPATH="$CLASSPATH:/home/ec2-user/aws-java-sdk-1.XX.YYY/lib/aws-java-sdk-1.XX.YYY.jar"
        export CLASSPATH="$CLASSPATH:/home/ec2-user/aws-java-sdk-1.XX.YYY/third-party/lib/*"

        # IF RNL
        export CLASSPATH="/home/test/cnv-project19-20/codebase/LBInstance/project"
        export CLASSPATH="$CLASSPATH:/home/test/aws-java-sdk-1.XX.YYY/lib/aws-java-sdk-1.11.787.jar"
        export CLASSPATH="$CLASSPATH:/home/test/aws-java-sdk-1.XX.YYY/third-party/lib/*"


## To run the Web Server
Assuming project/ is present, in the ec2-user/home directory:
1) Compile WebServer.java:
          
        javac ./project/pt/ulisboa/tecnico/cnv/server/WebServer.java
        
2) Run the Web Server from there:
        
        java pt.ulisboa.tecnico.cnv.server.WebServer
        
        
        




