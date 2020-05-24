package pt.ulisboa.tecnico.cnv.server;

import pt.ulisboa.tecnico.cnv.estimatecomplexity.Estimator;
import pt.ulisboa.tecnico.cnv.estimatecomplexity.EstimatorBFS;
import pt.ulisboa.tecnico.cnv.estimatecomplexity.EstimatorDLX;
import pt.ulisboa.tecnico.cnv.estimatecomplexity.EstimatorCP;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.util.HashMap;
import java.util.Map;

import java.lang.Object;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.util.TableUtils;

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

public class WebServer {

	static AmazonDynamoDB dynamoDB;
	static AmazonEC2 ec2;
	static EstimatorBFS estimatorBFS = new EstimatorBFS();
	static EstimatorDLX estimatorDLX = new EstimatorDLX();
	static EstimatorCP estimatorCP = new EstimatorCP();
	private static HttpURLConnection con;
	static AtomicLong requestIds = new AtomicLong();

	public static void main(final String[] args) throws Exception {

		createMSS();

		final HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

		// TODO
		// Create a thread that will be receiving information from solver instances on
		// their progress

		server.createContext("/sudoku", new MyHandler());
		server.createContext("/update", new ProgressChecksHandler());

		// be aware! infinite pool of threads!
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();

		System.out.println(server.getAddress().toString());
	}

	private static Map<String, AttributeValue> newItem(int request_id, String strategy, int size, int un, int cost) {

		Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
		item.put("request_id", new AttributeValue().withN(Integer.toString(request_id)));
		item.put("strategy", new AttributeValue(strategy));
		item.put("size", new AttributeValue().withN(Integer.toString(size)));
		item.put("un", new AttributeValue().withN(Integer.toString(un)));
		item.put("cost", new AttributeValue().withN(Integer.toString(cost)));

		return item;
	}

	private static void createMSS() {

		ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();
		try {
			credentialsProvider.getCredentials();
		} catch (Exception e) {
			throw new AmazonClientException("Cannot load the credentials from the credential profiles file. "
					+ "Please make sure that your credentials file is at the correct "
					+ "location (~/.aws/credentials), and is in valid format.", e);
		}
		dynamoDB = AmazonDynamoDBClientBuilder.standard().withCredentials(credentialsProvider).withRegion("us-east-1")
				.build();

		try {
			String tableName = "incoming-requests-table";

			// Create a table with a primary hash key named 'name', which holds a string
			CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(tableName)
					.withKeySchema(new KeySchemaElement().withAttributeName("request_id").withKeyType(KeyType.HASH))
					.withAttributeDefinitions(new AttributeDefinition().withAttributeName("request_id")
							.withAttributeType(ScalarAttributeType.N))
					.withProvisionedThroughput(
							new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));

			// Create table if it does not exist yet
			TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
			// wait for the table to move into ACTIVE state
			try {
				TableUtils.waitUntilActive(dynamoDB, tableName);
			} catch (InterruptedException e) {
				System.out.println("Caught an InterruptedException");
				System.out.println("Error Message: " + e.getMessage());
			}

		} catch (AmazonServiceException ase) {
			System.out.println("Caught an AmazonServiceException, which means your request made it "
					+ "to AWS, but was rejected with an error response for some reason.");
			System.out.println("Error Message:    " + ase.getMessage());
			System.out.println("HTTP Status Code: " + ase.getStatusCode());
			System.out.println("AWS Error Code:   " + ase.getErrorCode());
			System.out.println("Error Type:       " + ase.getErrorType());
			System.out.println("Request ID:       " + ase.getRequestId());
		} catch (AmazonClientException ace) {
			System.out.println("Caught an AmazonClientException, which means the client encountered "
					+ "a serious internal problem while trying to communicate with AWS, "
					+ "such as not being able to access the network.");
			System.out.println("Error Message: " + ace.getMessage());
		}
	}

	private static void init() {
		AWSCredentials credentials = null;
		try {
			credentials = new ProfileCredentialsProvider().getCredentials();
		} catch (Exception e) {
			throw new AmazonClientException("Cannot load the credentials from the credential profiles file. "
					+ "Please make sure that your credentials file is at the correct "
					+ "location (~/.aws/credentials), and is in valid format.", e);
		}
		ec2 = AmazonEC2ClientBuilder.standard().withRegion("us-east-1")
				.withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
	}

	public static String parseRequestBody(InputStream is) throws IOException {
		InputStreamReader isr = new InputStreamReader(is, "utf-8");
		BufferedReader br = new BufferedReader(isr);

		// From now on, the right way of moving from bytes to utf-8 characters:

		int b;
		StringBuilder buf = new StringBuilder(512);
		while ((b = br.read()) != -1) {
			buf.append((char) b);

		}

		br.close();
		isr.close();

		return buf.toString();
	}


	private static Integer estimateRequestCost(String solver, Integer size, Integer un) {
		Estimator estimator;
		if (solver == "BFS") {
			estimator = estimatorBFS;
		} else if (solver == "DLX") {
			estimator = estimatorDLX;
		} else if (solver == "CP") {
			estimator = estimatorCP;
		} else {
			return -1;
		}
		return estimator.estimate(size, un);
	}

	static class ProgressChecksHandler implements HttpHandler{
		@Override
			public void handle(HttpExchange t) throws IOException{
				final String query = t.getRequestURI.getQuery();
				final String[] params = query.split("&");
				System.out.println("Request Id = " + params[0]);
				System.out.println("Number of methods = " + params[1]);

			}

	}



	static class MyHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) throws IOException {

			Instance chosen_instance;
			long requestId = requestIds.getAndIncrement();
			String requestIdQuery = "&req=" + requestId;
			// Get the query.
			final String query = t.getRequestURI().getQuery();
			System.out.println("> Query:\t" + query);

			// Break it down into String[].
			final String[] params = query.split("&");

			String solver;
			Integer size;
			Integer un;

			// Store as if it was a direct call to SolverMain.
			final ArrayList<String> newArgs = new ArrayList<>();
			for (final String p : params) {
				final String[] splitParam = p.split("=");
				newArgs.add("-" + splitParam[0]);
				newArgs.add(splitParam[1]);

				if (splitParam[0].equals("s")) {
					solver = splitParam[1];
				} else if (splitParam[0].equals("n1")) {
					size = Integer.parseInt(splitParam[1]);
				} else if (splitParam[0].equals("un")) {
					un = Integer.parseInt(splitParam[1]);
				}
			}
			newArgs.add("-b");
			newArgs.add(parseRequestBody(t.getRequestBody()));

			newArgs.add("-d");

			// TODO
			// Estimate request cost
			// HERE
			// ArrayList newArgs will be [-s, <solving_strategy>, -un, <thr__miss_elems>,
			// -n1, <sizeX>, -n2, <sizeY>, -i, <puzzle_name>, -b, <puzzle_base_contents>, -d]

			// Example on how to access the DynamoDB table
			// Scan items for movies with a year attribute greater than 1985
			/*
			 * HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
			 * Condition condition = new Condition()
			 * .withComparisonOperator(ComparisonOperator.GT.toString())
			 * .withAttributeValueList(new AttributeValue().withN("1985"));
			 * scanFilter.put("year", condition); ScanRequest scanRequest = new
			 * ScanRequest(tableName).withScanFilter(scanFilter); ScanResult scanResult =
			 * dynamoDB.scan(scanRequest); System.out.println("Result: " + scanResult);
			 */


			Integer cost = estimateRequestCost(solver, size, un);

			init();

			try {
				DescribeInstancesResult describeInstancesRequest = ec2.describeInstances();
				List<Reservation> reservations = describeInstancesRequest.getReservations();
				Set<Instance> instances = new HashSet<Instance>();

				for (Reservation reservation : reservations) {
					instances.addAll(reservation.getInstances());
				}

				System.out.println("You have " + instances.size() + " Amazon EC2 instance(s) running.");

				for (Instance instance : instances) {
					String name = instance.getInstanceId();
					String state = instance.getState().getName();
					// Selecting only from running instances
					if (state.equals("running")) {
						// TODO

						// When doing this TODO, delete the next three lines
						// chosen_instance is the first running instance found
						chosen_instance = instance;
						break;
					}
				}

				// TODO
				// Send the request to the chosen Solver instance
				String url = chosen_instance.getPublicDnsName() + ":8000/sudoku?" + query + requestIdQuery;
				byte[] postData = parseRequestBody(t.getRequestBody()).getBytes(StandardCharsets.UTF_8);

				try {
					URL myurl = new URL(url);
					con = (HttpURLConnection) myurl.openConnection();

					con.setDoOutput(true);
					con.setRequestMethod("POST");
					con.setRequestProperty("User-Agent", "Java client");
					con.setRequestProperty("Content-Type", "application/json");

					DataOutputStream out = new DataOutputStream(con.getOutputStream()))
					out.write(postData);
					out.flush();
					out.close();
					con.disconnect();
				}


			} catch (AmazonServiceException ase) {
				System.out.println("Caught Exception: " + ase.getMessage());
				System.out.println("Reponse Status Code: " + ase.getStatusCode());
				System.out.println("Error Code: " + ase.getErrorCode());
				System.out.println("Request ID: " + ase.getRequestId());
			}

		}
	}

}
