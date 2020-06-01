package pt.ulisboa.tecnico.cnv.server;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
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

public class AutoScaler {

	/*
	 * Before running the code: Fill in your AWS access credentials in the provided
	 * credentials file template, and be sure to move the file to the default
	 * location (~/.aws/credentials) where the sample code will load the credentials
	 * from. https://console.aws.amazon.com/iam/home?#security_credential
	 *
	 * WARNING: To avoid accidental leakage of your credentials, DO NOT keep the
	 * credentials file in your source directory.
	 */

	static AmazonEC2 ec2;
	static AmazonCloudWatch cloudWatch;

	static final String REGION = "us-east-1";

	static final int MIN_INSTANCE_COUNT = 1;
	static final int MAX_INSTANCE_COUNT = 10;

	static final double MIN_CPU_VALUE = 20;
	static final double MAX_CPU_VALUE = 60;

	static final int TIME_INTERVAL = 10 * 1000;
	static final int GRACE_PERIOD = 30 * 1000;
	static final int CPU_USAGE_TIME_PERIOD_SECONDS = 60;

	static final String SOLVER_IMAGE_ID = "ami-0274e8a8391c43714";
	static final String KEY_NAME = "project-final";
	static final String SECURITY_GROUP_NAME = "CNV-Vanilla";

	private static Map<String, Integer> remaningGracePeriodInstance = new HashMap<>();
	private static Map<String, Instance> startingInstances = new HashMap<>();
	private static Map<String, Instance> readyInstances = new HashMap<>();
	private static Map<String, Boolean> hasRequests = new HashMap<>();
	private static Map<String, Instance> instancesToShutDown = new HashMap<>();

	/**
	 * The only information needed to create a client are security credentials
	 * consisting of the AWS Access Key ID and Secret Access Key. All other
	 * configuration, such as the service endpoints, are performed automatically.
	 * Client parameters, such as proxies, can be specified in an optional
	 * ClientConfiguration object when constructing a client.
	 *
	 * @see com.amazonaws.auth.BasicAWSCredentials
	 * @see com.amazonaws.auth.PropertiesCredentials
	 * @see com.amazonaws.ClientConfiguration
	 */
	private static void init() {

		/*
		 * The ProfileCredentialsProvider will return your [default] credential profile
		 * by reading from the credentials file located at (~/.aws/credentials).
		 */
		AWSCredentials credentials = null;
		try {
			credentials = new ProfileCredentialsProvider().getCredentials();
		} catch (Exception e) {
			throw new AmazonClientException("Cannot load the credentials from the credential profiles file. "
					+ "Please make sure that your credentials file is at the correct "
					+ "location (~/.aws/credentials), and is in valid format.", e);
		}
		ec2 = AmazonEC2ClientBuilder
						.standard()
						.withRegion(REGION)
						.withCredentials(new AWSStaticCredentialsProvider(credentials)).build();

		cloudWatch = AmazonCloudWatchClientBuilder.standard()
						.withRegion(REGION)
						.withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
	}

	private static void launchInstance() {
		if (readyInstances.size() + startingInstances.size() < MAX_INSTANCE_COUNT) {
			System.out.println("Starting a new instance.");
			RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
			runInstancesRequest.withImageId(SOLVER_IMAGE_ID)
								.withInstanceType("t2.micro")
								.withMinCount(1)
								.withMaxCount(1)
								.withKeyName(KEY_NAME)
								.withSecurityGroups(SECURITY_GROUP_NAME);

			RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
			Instance instance = runInstancesResult.getReservation().getInstances().get(0);
			String instanceId = instance.getInstanceId();
			startingInstances.put(instanceId, instance);
			remaningGracePeriodInstance.put(instanceId, GRACE_PERIOD);
			hasRequests.put(instanceId, false);
		}
	}

	private static void dropInstance(String instanceId) {
		System.out.println("Terminating the instance.");
		TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
		termInstanceReq.withInstanceIds(instanceId);
		ec2.terminateInstances(termInstanceReq);
	}

	private static void checkGracePeriodInstances() {
		ArrayList<String> finishedGracePeriodInstances = new ArrayList<>();

		for (Map.Entry<String, Instance> entry : startingInstances.entrySet()) {
			String instanceId = entry.getKey();
			Instance instance = entry.getValue();
			Integer remainingTime = remaningGracePeriodInstance.get(instanceId);
			remainingTime -= TIME_INTERVAL;
			if (remainingTime > 0) {
				remaningGracePeriodInstance.put(instanceId, remainingTime);
			} else {
				finishedGracePeriodInstances.add(instanceId);
			}
		}

		for (String instanceId : finishedGracePeriodInstances) {
			Instance instance = new Instance();
			instance = instance.withInstanceId(instanceId);
			startingInstances.remove(instanceId);
			remaningGracePeriodInstance.remove(instanceId);
			readyInstances.put(instanceId, instance);
		}
	}

	private static void checkShutDownInstances() {
		ArrayList<String> removedInstances = new ArrayList<>();
		for (Map.Entry<String, Instance> entry : instancesToShutDown.entrySet()) {
			String instanceId = entry.getKey();
			if (!hasRequests.get(instanceId)) {
				removedInstances.add(instanceId);
			}
		}

		for (String instanceId : removedInstances) {
			instancesToShutDown.remove(instanceId);
			dropInstance(instanceId);
		}
	}

	private static void addToShutDownList(String instanceId, Instance instance) {
		if (readyInstances.size() > MIN_INSTANCE_COUNT) {
			instancesToShutDown.put(instanceId, instance);
			readyInstances.remove(instanceId);
		}
	}

	public static void startAS() {
		init();

		List<Reservation> reservations = ec2.describeInstances().getReservations();
		for (Reservation reservation : reservations) {
			for(Instance instance : reservation.getInstances()){
				if(instance.getImageId().equals(SOLVER_IMAGE_ID) && instance.getState().getName().equals("running")) {
					readyInstances.put(instance.getInstanceId(), instance);
					hasRequests.put(instance.getInstanceId(), false);
				}
			}
		}

		try {
			while (true) {
				try {

					System.out.println();
					System.out.println("You have " + readyInstances.size() + " solver instance(s) ready.");
					System.out.println("You have " + startingInstances.size() + " solver instance(s) being launched.");
					System.out.println("You have " + instancesToShutDown.size() + " solver instance(s) waiting to shut down.");

					if ((readyInstances.size() + startingInstances.size()) < MIN_INSTANCE_COUNT) {
						launchInstance();
						continue;
					}


					Instance toShutDownInstance = null;
					String toShutDownInstanceId = null;

					boolean toShutDown = false;
					boolean toStart = false;

					for (Map.Entry<String, Instance> entry : readyInstances.entrySet()) {
						String instanceId = entry.getKey();
						Instance instance = entry.getValue();
						String state = instance.getState().getName();

						Dimension instanceDimension = new Dimension();
						instanceDimension.setName("InstanceId");
						instanceDimension.setValue(instanceId);


						Date startDate = new Date(new Date().getTime() - 1000 * 60 * 10);
						Date endDate = new Date();
						GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
								.withStartTime(startDate)
								.withEndTime(endDate)
								.withNamespace("AWS/EC2")
								.withPeriod(1)
								.withMetricName("CPUUtilization")
								.withStatistics("Average")
								.withDimensions(instanceDimension);


						GetMetricStatisticsResult getMetricStatisticsResult = cloudWatch.getMetricStatistics(request);

						List<Datapoint> datapoints = getMetricStatisticsResult.getDatapoints();
						Integer size = datapoints.size();

						// CASE WHERE MACHINE IS NOT ALIVE

						if (size == 0) continue; // means the instance was just created
						else if (size == 1) {
							Double avg = datapoints.get(0).getAverage();
							if (avg > MAX_CPU_VALUE) {
								toStart = true;
								break;
							} else if (avg < MIN_CPU_VALUE) {
								toShutDownInstance = instance;
								toShutDownInstanceId = instanceId;
								toShutDown = true;
								break;
							}

						} else if (size == 2) {
							Double avg1 = datapoints.get(0).getAverage();
							Double avg2 = datapoints.get(1).getAverage();
							if (avg1 > MAX_CPU_VALUE && avg2 > MAX_CPU_VALUE) {
								toStart = true;
								break;
							} else if (avg1 < MIN_CPU_VALUE && avg2 < MIN_CPU_VALUE) {
								toShutDownInstance = instance;
								toShutDownInstanceId = instanceId;
								toShutDown = true;
								break;
							}

						} else {
							Double avg1 = datapoints.get(size-1).getAverage();
							Double avg2 = datapoints.get(size-2).getAverage();
							Double avg3 = datapoints.get(size-3).getAverage();

							if (avg1 > MAX_CPU_VALUE && avg2 > MAX_CPU_VALUE && avg3 > MAX_CPU_VALUE) {
								toStart = true;
								break;
							} else if (avg1 < MIN_CPU_VALUE && avg2 < MIN_CPU_VALUE && avg3 < MIN_CPU_VALUE) {
								toShutDown = true;
								toShutDownInstance = instance;
								toShutDownInstanceId = instanceId;
								break;
							}
						}
					}

					if (toStart) {
						launchInstance();
					} else if (toShutDown) {
						addToShutDownList(toShutDownInstanceId, toShutDownInstance);
					}

				} catch (AmazonServiceException ase) {
					System.out.println("Caught Exception: " + ase.getMessage());
					System.out.println("Reponse Status Code: " + ase.getStatusCode());
					System.out.println("Error Code: " + ase.getErrorCode());
					System.out.println("Request ID: " + ase.getRequestId());
				}

				Thread.sleep(TIME_INTERVAL);
				checkGracePeriodInstances();
				checkShutDownInstances();
			}

		} catch (InterruptedException e1) {
			System.out.println(e1);

		} catch (Exception e2) {
			System.out.println(e2);
		}
	}

	public static final Map<String, Instance> getReadyInstances() {

		System.out.println("Ready to run:" + readyInstances.keySet());
		return readyInstances;
	}

	public static final void reportDead(String instanceId) {

		System.out.println("Dead: " + instanceId);
		readyInstances.remove(instanceId);
		dropInstance(instanceId);
	}

	public static void setHasRequests(String instanceId, boolean toggle) {
		System.out.println("Has requests: " + instanceId + " - " + toggle);
		hasRequests.put(instanceId, toggle);
	}

	public static void main(String[] args) {
		startAS();
	}
}
