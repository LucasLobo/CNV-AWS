/* 2016-20 Extended by Luis Veiga and Joao Garcia */
/*
 * Copyright 2010-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Date;
import java.util.ArrayList;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;

public class EC2LaunchWaitTerminate {

    /*
     * Before running the code:
     *      Fill in your AWS access credentials in the provided credentials
     *      file template, and be sure to move the file to the default location
     *      (~/.aws/credentials) where the sample code will load the
     *      credentials from.
     *      https://console.aws.amazon.com/iam/home?#security_credential
     *
     * WARNING:
     *      To avoid accidental leakage of your credentials, DO NOT keep
     *      the credentials file in your source directory.
     */

    static AmazonEC2      ec2;
    static AmazonCloudWatch cloudWatch;
		static final int MAX_CPU_VALUE = 60;
		static final int MIN_CPU_VALUE = 20;
		static final int TIME_INTERVAL = 60000;
		static final String SOLVER_IMAGE_ID = "ami-013bd72423f2e5b8c";


		private static DescribeInstancesResult describeInstancesRequest;
		private static List<Reservation> reservations;
		public static Set<Instance> instances;
    /**
     * The only information needed to create a client are security credentials
     * consisting of the AWS Access Key ID and Secret Access Key. All other
     * configuration, such as the service endpoints, are performed
     * automatically. Client parameters, such as proxies, can be specified in an
     * optional ClientConfiguration object when constructing a client.
     *
     * @see com.amazonaws.auth.BasicAWSCredentials
     * @see com.amazonaws.auth.PropertiesCredentials
     * @see com.amazonaws.ClientConfiguration
     */
    private static void init() {

        /*
         * The ProfileCredentialsProvider will return your [default]
         * credential profile by reading from the credentials file located at
         * (~/.aws/credentials).
         */
        AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider().getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                    "Please make sure that your credentials file is at the correct " +
                    "location (~/.aws/credentials), and is in valid format.",
                    e);
        }
    	ec2 = AmazonEC2ClientBuilder.standard().withRegion("us-east-1").withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
    	cloudWatch = AmazonCloudWatchClientBuilder.standard().withRegion("us-east-1").withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
    }

		private static void launchInstance(){
			System.out.println("Starting a new instance.");
			RunInstancesRequest runInstancesRequest = new RunInstancesRequest();

				/* TODO: configure to use your AMI, key and security group */
			runInstancesRequest.withImageId(SOLVER_IMAGE_ID)
												 .withInstanceType("t2.micro")
												 .withMinCount(1)
												 .withMaxCount(1)
												 .withKeyName("cnv-project")
												 .withSecurityGroups("cnv-project");
			RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
			String newInstanceId = runInstancesResult.getReservation().getInstances()
																	.get(0).getInstanceId();
			describeInstancesRequest = ec2.describeInstances();
			reservations = describeInstancesRequest.getReservations();
			instances = new HashSet<Instance>();

			addSolverInstances();
		}

		private static void dropInstance(String instanceId){
			System.out.println("Terminating the instance.");
			TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
			termInstanceReq.withInstanceIds(instanceId);
			ec2.terminateInstances(termInstanceReq);
		}

		private static void addSolverInstances(){
			for (Reservation reservation : reservations) {
				for(Instance instance : reservation.getInstances()){
					if(instance.getImageId().equals(SOLVER_IMAGE_ID))
						instances.add(instance);
				}
			}
		}

    public static void main(String[] args) {

        System.out.println("===========================================");
        System.out.println("Welcome to the AWS Java SDK!");
        System.out.println("===========================================");

        init();
				boolean runInstance = false;
				boolean stopInstance = false;
				String instanceId = "";
				try{
					while(true){
		        /*
		         * Amazon EC2
		         *
		         * The AWS EC2 client allows you to create, delete, and administer
		         * instances programmatically.
		         *
		         * In this sample, we use an EC2 client to get a list of all the
		         * availability zones, and all instances sorted by reservation id, then
		         * create an instance, list existing instances again, wait a minute and
		         * the terminate the started instance.
		         */
		        try {
		            DescribeAvailabilityZonesResult availabilityZonesResult = ec2.describeAvailabilityZones();
		            System.out.println("You have access to " + availabilityZonesResult.getAvailabilityZones().size() +
		                    " Availability Zones.");
		            /* using AWS Ireland.
		             * TODO: Pick the zone where you have your AMI, sec group and keys */
		            describeInstancesRequest = ec2.describeInstances();
		            reservations = describeInstancesRequest.getReservations();
		            instances = new HashSet<Instance>();

		            addSolverInstances();

		            System.out.println("You have " + instances.size() + " Amazon EC2 Solver instance(s) running.");



			    		long offsetInMilliseconds = 1000 * 60 * 10;
					    Dimension instanceDimension = new Dimension();
		          instanceDimension.setName("InstanceId");
		          List<Dimension> dims = new ArrayList<Dimension>();
		          dims.add(instanceDimension);

					    System.out.println("Verifying CPU Costs.");
					    for(Instance instance : instances){
								String name = instance.getInstanceId();
			          String state = instance.getState().getName();
			          if (state.equals("running")) {
									System.out.println("running instance id = " + name);
									instanceDimension.setValue(name);
									GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
										.withStartTime(new Date(new Date().getTime() - offsetInMilliseconds))
										.withNamespace("AWS/EC2")
										.withPeriod(60)
										.withMetricName("CPUUtilization")
										.withStatistics("Average")
										.withDimensions(instanceDimension)
										.withEndTime(new Date());
									GetMetricStatisticsResult getMetricStatisticsResult = cloudWatch.getMetricStatistics(request);
									List<Datapoint> datapoints = getMetricStatisticsResult.getDatapoints();
									int size = datapoints.size()-1;
									try{
										Double value1 = datapoints.remove(size).getAverage();
										Double value2 = datapoints.remove(size-1).getAverage();
										Double value3 = datapoints.remove(size-2).getAverage();
										if(value3 > MAX_CPU_VALUE && value2 > MAX_CPU_VALUE && value1 > MAX_CPU_VALUE){
											runInstance = true;
											break;
										}else if(value3 < MIN_CPU_VALUE && value2 < MIN_CPU_VALUE && value1 < MIN_CPU_VALUE){
											stopInstance = true;
											instanceId = name;
											break;
										}
									}catch(IndexOutOfBoundsException e){
										if(datapoints.get(size).getAverage() > MAX_CPU_VALUE){
											runInstance = true;
											break;
										}else if(datapoints.get(size).getAverage() < MIN_CPU_VALUE){
											stopInstance = true;
											instanceId = name;
											break;
										}
									}
								}
			    		}


			    	if(runInstance){
			    		launchInstance();
				    }else if(stopInstance){
							dropInstance(instanceId);
				    }

			      } catch (AmazonServiceException ase) {
		                System.out.println("Caught Exception: " + ase.getMessage());
		                System.out.println("Reponse Status Code: " + ase.getStatusCode());
		                System.out.println("Error Code: " + ase.getErrorCode());
		                System.out.println("Request ID: " + ase.getRequestId());
		      	}
						Thread.currentThread().sleep(TIME_INTERVAL);
					}
				}catch(InterruptedException e){
				}catch(Exception e2){
				}
  	}
}
