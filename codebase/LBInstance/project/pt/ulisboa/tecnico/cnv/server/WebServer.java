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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Executors;
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

import org.json.*;

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
		init();

		final HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

		server.createContext("/sudoku", new MyHandler());
		server.createContext("/update", new ProgressChecksHandler());

		// be aware! infinite pool of threads!
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();

		System.out.println(server.getAddress().toString());
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
			String tableName = "request-cost-table";

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

	private static List<Map<String,AttributeValue>> fetchEntries(int id){
    ArrayList<Item> itemList = new ArrayList<>();
    Table table = dynamoDB.getTable(tableName);
    Map<String, AttributeValue> expressionAttributeValues = new HashMap<String, AttributeValue>();
    expressionAttributeValues.put(":val", new AttributeValue().withN(String.valueOf(id)));
    ScanRequest scanRequest = new ScanRequest().withTableName(tableName).withFilterExpression("request_id > :val").withExpressionAttributeValues(expressionAttributeValues);
    ScanResult result = dynamoDB.scan(scanRequest);
    return result.getItems();

  }

	static class ProgressChecksHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) throws IOException {

			final String query = t.getRequestURI().getQuery();
			final String[] params = query.split("&");

			System.out.println(query);
			Integer requestId = -1;
			Integer cost = -1;

			for (final String p : params) {
				final String[] splitParam = p.split("=");
				if (splitParam[0].equals("r")) {
					requestId = Integer.valueOf(splitParam[1]);
				} else if (splitParam[0].equals("c")) {
					cost = Integer.valueOf(splitParam[1]);
				}
			}

			String response = "";
			t.sendResponseHeaders(200, response.length());
			OutputStream os = t.getResponseBody();
			os.write(response.getBytes());
			os.close();
		}

	}

	static class MyHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) throws IOException {

			long requestId = requestIds.getAndIncrement();
			String requestIdQuery = "&req=" + requestId;

			// Get the query.
			final String query = t.getRequestURI().getQuery();
			System.out.println("> Query:\t" + query);

			// Break it down into String[].
			final String[] params = query.split("&");

			String solver = "undefined";
			Integer size = -1;
			Integer un = -1;

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

			// newArgs.add("-d");

			Integer cost = estimateRequestCost(solver, size, un);

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
					if (true || state.equals("running")) {
						// TODO
						// Uses information on the global structure to decide to which instance it will send the request
						// When doing this TODO, delete the next line
						// chosen_instance, as it is, is the first running instance found
						Instance chosen_instance = instance;

						// TODO
						// Saves information about the request in a global structure

						// Send incoming request to the chosen Solver Instance
						// chosen_instance.getPublicDnsName() + ":8000/sudoku?" + query;
						// next line is for testing purposes, insert public DNS of the solver instance you want to test

						String instanceURL = "127.0.0.1";
						String instancePort = "8500";
						String url = "http://" + instanceURL + ":" + instancePort + "/sudoku?" + query + requestIdQuery;

						byte[] postData = newArgs.get(11).getBytes(StandardCharsets.UTF_8);
						URL myurl = new URL(url);

						con = (HttpURLConnection) myurl.openConnection();
						con.setDoOutput(true);
						con.setRequestMethod("POST");
						con.setRequestProperty("User-Agent", "Java client");
						con.setRequestProperty("Content-Type", "text/plain;charset=UTF-8");
						DataOutputStream out = new DataOutputStream(con.getOutputStream());
						out.write(postData);
						out.flush();
						out.close();

						// Receive response from Solver Instance
						int status = con.getResponseCode();
						BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
						String inputLine;
						StringBuffer content = new StringBuffer();
						while ((inputLine = in.readLine()) != null) {
							content.append(inputLine);
						}
						in.close();
						con.disconnect();

						// Turn String content into a JSONArray
						String s = content.toString();
						s=s.replace("[","");//replacing all [ to ""
						s=s.substring(0,s.length()-2);//ignoring last two ]]
						String s1[]=s.split("],");//separating all by "],"
						int my_matrics[][] = new int[s1.length][s1.length];//declaring two dimensional matrix for input

						for(int i=0;i<s1.length;i++){
							s1[i]=s1[i].trim();//ignoring all extra space if the string s1[i] has
							String single_int[]=s1[i].split(",");//separating integers by ", "
							for(int j=0;j<single_int.length;j++){
								my_matrics[i][j]=Integer.parseInt(single_int[j]);//adding single values
							}
						}
						// for (int i = 0; i < my_matrics.length; i++)
						// 	for (int j = 0; j < my_matrics[i].length; j++)
						// 		System.out.print(my_matrics[i][j] + " ");

						JSONArray solution = new JSONArray();
						for(int lin = 0; lin<Integer.parseInt(newArgs.get(5)); lin++){
							JSONArray line = new JSONArray();
							for(int col = 0; col<Integer.parseInt(newArgs.get(7)); col++){
								line.put(my_matrics[lin][col]);

							}
							solution.put(line);
						}

						// Send response to browser
						final Headers hdrs = t.getResponseHeaders();
						hdrs.add("Content-Type", "application/json");
						hdrs.add("Access-Control-Allow-Origin", "*");
						hdrs.add("Access-Control-Allow-Credentials", "true");
						hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
						hdrs.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");
						t.sendResponseHeaders(200, solution.toString().length());
						final OutputStream os = t.getResponseBody();
						OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
						osw.write(solution.toString());
						osw.flush();
						osw.close();
						os.close();
						System.out.println("> Sent response to " + t.getRemoteAddress().toString());
						break;
					}
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
