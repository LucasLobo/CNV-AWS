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

2) Setup paths to Java version 7.

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

## To run the Web Server
Assuming project/ is present, in the ec2-user/home directory:
1) Compile WebServer.java:
          
        javac project/pt/ulisboa/tecnico/cnv/server/WebServer.java 
        
2) Go to project/ and run the Web Server from there:
    
        cd project
        
        java pt/ulisboa/tecnico/cnv/server/WebServer
        
        
        




