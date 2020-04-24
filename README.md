# cnv-project19-20
README file with the text description of the architecture and your selections of the system configurations (auto-scaling, load balancer, etc...)

The Sudoku@Cloud system will be run within the Amazon Web Services ecosystem. The system will be organized in four main components:

• Web Servers: The web servers receive web requests to perform puzzle solving, discovering missing
elements in the grid and return the solved puzzle. In Sudoku@Cloud , there will be a varying number
of identical web servers. Each will run on a rented AWS Elastic Compute Cloud (EC2) instance.

• Load Balancer: The load balancer is the entry point into the Sudoku@Cloud system. It receives
all web requests, and for each one, it selects an active web server to serve the request and forwards
it to that server.

• Auto-Scaler: The auto-scaler is in charge of collecting system performance metrics and, based on
them, adjusting the number of active web servers.

• Metrics Storage System: The metrics storage system will use one of the available data storage
mechanisms at AWS to store web server performance metrics relating to requests. These will help
the load balancer choose the most appropriate web server.

LOAD BALANCER

The load balancer is the only entry point into the system: It receives a sequence of web requests and selects one of the active web server cluster nodes to handle each of the requests. 
For the checkpoint, this job is performed by an off-the-shelf load balancer available at Amazon AWS, thoughtfully configured by us. Although the algorithm for load balancing is not fully implemented at this stage, we have already some logic developed.
As explained, in detail, in the intermediate project report, the web server responsible for receiving requests acts as our load balancer. When a request is received, the load balancer checks our MSS to see if there is some data available about previous requests with similar parameters. For requests considered unrelated to any previous one (initial ones), we have a simple heuristic for estimating the cost. For requests where we have enough information about previous requests with similar parameters, we use an estimation model to calculate the cost. Having estimated the cost of the request, the load balancer selects an active web server to handle it. For this purpose, the load balancer may know which servers are busy, how many and what requests they are currently handling, what the parameters of those requests are, their current progress (and, conversely, how much work is left taking into account the estimated cost).
The load balancer we created using Amazon AWS is of the classic type, with us-east-1a as its subnet and configured with protocol HTTP. The load balancer has web server on port 80, and forwards to port 8000. The selected security group for the load balancer has 3 inbound rules of type HTTP (with protocol TCP, port range 80	and source 0.0.0.0/0), Custom TCP (with protocol TCP, port range 8000	and source 0.0.0.0/0) and SSH (with protocol TCP, port range 22	and source 0.0.0.0/0). The health check was configured with ping target HTTP:8000/sudoku with a response timeout of 60 seconds, interval of 120 seconds, 2 as the number of consecutive health check failures before declaring an EC2 instance unhealthy and 5 as the number of consecutive health check successes before declaring an EC2 instance healthy. 

Auto-Scaler

The auto-scaling component adaptively decides how many web server nodes should be active at any given moment. 
For the checkpoint we haven't yet finished implementing our own auto scaler but we have implemented until a certain degree. Our idea is that as we gather the statics of each of the instances that are running at check its Average CPU usage of the 3 most recent datapoints. If there isn't enough datapoints we just gather the last one. If the 3 datapoints have a CPU usage of CPU_MAX_VALUE (for example, 80%) then another instance is created. The same happens if we don't have enough datapoints. [IMPLEMENTED - not tested]
The same analysis happens to decrement the number of instances but instead of comparing the Average CPU usage with CPU_MAX_VALUE, it compares with CPU_MIN_VALUE (for example 10%). The same logic applies, if the most recent 3 datapoints have a CPU usage of < CPU_MIN_VALUE then an instance gets terminated. [NOT IMPLEMENTED]


Regarding the auto scaling group we created using Amazon AWS, we started by creating a launch configuration, using an an AMI provided by AWS, Amazon Linux 2 AMI (HVM). We used the default instance type and enabled CloudWatch detailed monitoring. We also used default storage device settings with size (GiB) 8 of type General Purpose (SSD). The selected security group for the auto-scaler has 3 inbound rules of type HTTP (with protocol TCP, port range 80	and source 0.0.0.0/0), Custom TCP (with protocol TCP, port range 8000	and source 0.0.0.0/0) and SSH (with protocol TCP, port range 22	and source 0.0.0.0/0). 
