# cnv-project19-20
README file with the text description of the architecture and your selections of the system configurations (auto-scaling, load balancer, etc...)

LOAD BALANCER 
The load balancer is the only entry point into the system: it receives a sequence of web requests and selects one of the active web server cluster nodes to handle each of the requests. 
For the checkpoint, this job is performed by an off-the-shelf load balancer available at Amazon AWS, thoughtfully configured by us. Although the algorithm for load balancing is not fully implemented at this stage, we have already some logic developed.
As explained, in detail, in the intermediate project report, the web server responsible for receiving requests acts as our load balancer. When a request is received, the load balancer checks our MSS to see if there is some data available about previous requests with similar parameters. For requests considered unrelated to any previous one (initial ones), we have a simple heuristic for estimating the cost. For requests where we have enough information about previous requests with similar parameters, we use an estimation model to calculate the cost. Having estimated the cost of the request, the load balancer selects an active web server to handle it. For this purpose, the load balancer may know which servers are busy, how many and what requests they are currently handling, what the parameters of those requests are, their current progress (and, conversely, how much work is left taking into account the estimated cost).
The load balancer we created using Amazon AWS is of the classic type, with us-east-1a as its subnet and configured with protocol HTTP. The load balancer has web server on port 80, and forwards to port 8000. The selected security group for the load balancer has 3 inbound rules of type HTTP (with protocol TCP, port range 80	and source 0.0.0.0/0), Custom TCP (with protocol TCP, port range 8000	and source 0.0.0.0/0) and SSH (with protocol TCP, port range 22	and source 0.0.0.0/0). The health check was configured with ping target HTTP:8000/test (add advanced details)
