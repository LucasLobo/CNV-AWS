package pt.ulisboa.tecnico.cnv.server;

import pt.ulisboa.tecnico.cnv.estimatecomplexity.Estimator;
import pt.ulisboa.tecnico.cnv.estimatecomplexity.EstimatorBFS;
import pt.ulisboa.tecnico.cnv.estimatecomplexity.EstimatorDLX;
import pt.ulisboa.tecnico.cnv.estimatecomplexity.EstimatorCP;
import pt.ulisboa.tecnico.cnv.estimatecomplexity.LinearRegression;
import pt.ulisboa.tecnico.cnv.server.AutoScaler;

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
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
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
	static String tableName = "request-cost-table";
	static EstimatorBFS estimatorBFS = new EstimatorBFS();
	static EstimatorDLX estimatorDLX = new EstimatorDLX();
	static EstimatorCP estimatorCP = new EstimatorCP();
	static AtomicLong requestIds = new AtomicLong(1L);
	static AtomicLong lastSavedRequestId = new AtomicLong();
	static final int HEALTH_CHECK_TIME_INTERVAL = 30000;
	static final String instancePort = "8000";

	static HashMap<Long, Integer> requestCostEstimation = new HashMap<>();
	static HashMap<Long, Integer> requestMethodProgress = new HashMap<>(); // needs to be converted to cost
	static HashMap<Long, String> requestSolver = new HashMap<>();
	static ArrayList<InstanceRequest> instanceRequests = new ArrayList<>();

	public static void main(final String[] args) throws Exception {

		startAutoScaler();
		createMSS();
		init();
		updateEstimators();

		final HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

		server.createContext("/sudoku", new SudokuHandler());
		server.createContext("/update", new ProgressChecksHandler());

		// be aware! infinite pool of threads!
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while (true) {
						Thread.sleep(HEALTH_CHECK_TIME_INTERVAL);
						sendHealthChecks();
					}
				} catch (InterruptedException e) {
				}
			}
		}).start();

		System.out.println(server.getAddress().toString());
	}

	private static void startAutoScaler() {
		new Thread(new Runnable() {
			@Override
			public void run() {
			  AutoScaler.startAS();
			}
		}).start();
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

	private static void sendHealthChecks(){
		ArrayList<Instance> aliveInstances = new ArrayList<>(); // Get from AS
		ArrayList<String> deadInstances = new ArrayList<>();

		for (Map.Entry<String, Instance> entry : AutoScaler.getReadyInstances().entrySet()) {
			Instance instance = entry.getValue();
			aliveInstances.add(instance);
		}

		for(Instance instance : aliveInstances){
			String url = "http://" + instance.getPublicDnsName() + ":" + instancePort + "/test"; //TODO
			// Prepare sending healthCheck
			try {
				URL myUrl = new URL(url);
				//Sending healthCheck
				int i = 0;
				while(i < 3){
					HttpURLConnection con = (HttpURLConnection) myUrl.openConnection();

					con.setRequestMethod("POST");
					con.setRequestProperty("User-Agent", "Java client");
					con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
					con.setDoOutput(true);

					DataOutputStream out = new DataOutputStream(con.getOutputStream());
					out.flush();
					out.close();

					// Receive response from Solver Instance
					if(con.getResponseCode() == 200){ //UP
						con.disconnect();
						break;
					}
					i++;
					con.disconnect();
				}

				if(i == 3){
					aliveInstances.remove(instance);
					deadInstances.add(instance.getInstanceId());
				}

			} catch (Exception e) {
				System.out.println(e);
			}
		}

		for(String instance : deadInstances){
			removeRequests(instance.getInstanceId());
		}
	}

	private static void removeRequests(String instanceId){
		InstanceRequest instanceRequest = instanceRequests.get(instanceId);
		for(int i = 0; i!= instanceRequest.getRequestIds().size(); i++){
			sendRequest(instanceRequest.getQueries().get(i), instanceRequest.getBodies().get(i)); // Attribute an instance this request
		}
		instanceRequest.getQueries().clear();
		instanceRequest.getRequestIds().clear();
		instanceRequest.getBodies().clear();
	}

	private static void sendRequest(String query, String body){

	}

    private static Instance choose_best_instance(Set<Instance> instances) {
		int highest_load = Integer.MAX_VALUE;
		Instance chosen_instance;
		for (Instance instance : instances) {
				String state = instance.getState().getName();
				if (state.equals("running") && instance.getImageId().equals(SOLVER_IMAGE_ID)){
					int instance_load = 0;
					for (InstanceRequest request:instanceRequests){
						if(request.getInstanceId() == instance.getInstanceId()){
							List<Long> requests = request.getRequestIds();
						}

					}
					for (Long request_id:requests){
						int estimated_cost = requestCostEstimation.get(request_id);
						int methods = requestMethodProgress.get(request_id);
						String solver = requestSolver.get(request_id);
						int request_load = estimated_cost - estimateCostByMethodNumber(solver, methods);
						instance_load += request_load;
					}
					if (instance_load < highest_load) {
						highest_load = instance_load;
						chosen_instance = instance;
					}

				}
		}
		return chosen_instance;	
	}

	private static Integer estimateRequestCost(String solver, Integer size, Integer un) {
		Estimator estimator;
		if (solver.equals("BFS")) {
			estimator = estimatorBFS;
		} else if (solver.equals("DLX")) {
			estimator = estimatorDLX;
		} else if (solver.equals("CP")) {
			estimator = estimatorCP;
		} else {
			return -1;
		}
		return estimator.estimate(size, un);
	}

	private static List<Map<String,AttributeValue>> fetchEntriesHigherThan(Long id) {
		 Map<String, AttributeValue> expressionAttributeValues = new HashMap<String, AttributeValue>();
		 expressionAttributeValues.put(":val", new AttributeValue().withN(String.valueOf(id)));
		 ScanRequest scanRequest = new ScanRequest().withTableName(tableName).withFilterExpression("request_id > :val").withExpressionAttributeValues(expressionAttributeValues);
		 ScanResult result = dynamoDB.scan(scanRequest);
    	 return result.getItems();
  	}

	private static Integer estimateCostByMethodNumber(String solver, Integer methods) {
		Estimator estimator;
		if (solver.equals("BFS")) {
			estimator = estimatorBFS;
		} else if (solver.equals("DLX")) {
			estimator = estimatorDLX;
		} else if (solver.equals("CP")) {
			estimator = estimatorCP;
		} else {
			return -1;
		}
		return estimator.transform(methods);
	}

	private static void addPoint(String solver, Integer size, Integer un, Integer methods) {
		Estimator estimator;
		if (solver.equals("BFS")) {
			estimator = estimatorBFS;
		} else if (solver.equals("DLX")) {
			estimator = estimatorDLX;
		} else if (solver.equals("CP")) {
			estimator = estimatorCP;
		} else {
			return;
		}
		estimator.addDataPoint(size, un, methods);
	}

	private static void updateEstimators() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while (true) {
						Long lastSaved = lastSavedRequestId.get();
						Long biggestRequestId = 0L;
						List<Map<String,AttributeValue>> mss_entries = fetchEntriesHigherThan(lastSaved);
						for (Map<String,AttributeValue> line : mss_entries) {
							Long requestId = Long.parseLong(line.get("request_id").getN());
							String solver = line.get("strategy").getS();
							Integer size = Integer.parseInt(line.get("size").getN());
							Integer un = Integer.parseInt(line.get("un").getN());
							Integer methods = Integer.parseInt(line.get("cost").getN());
							addPoint(solver, size, un, methods);
							if (requestId > biggestRequestId) biggestRequestId = requestId;
						}
						lastSavedRequestId.set(biggestRequestId);
						Thread.sleep(30*1000);
					}
				} catch (RuntimeException e) {
					System.out.println(e);
				} catch (Exception e) {
					System.out.println(e);
				}
			}
		}).start();
	}

	static class ProgressChecksHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) throws IOException {

			final String query = t.getRequestURI().getQuery();
			final String[] params = query.split("&");

			// System.out.println(query);
			Long requestId = -1L;
			Integer methods = -1;

			for (final String p : params) {
				final String[] splitParam = p.split("=");
				if (splitParam[0].equals("r")) {
					requestId = Long.valueOf(splitParam[1]);
				} else if (splitParam[0].equals("m")) {
					methods = Integer.valueOf(splitParam[1]);
				}
			}

			requestMethodProgress.put(requestId, methods);

			String response = "";
			t.sendResponseHeaders(200, response.length());
			OutputStream os = t.getResponseBody();
			os.write(response.getBytes());
			os.close();
		}

	}

	static class SudokuHandler implements HttpHandler {

		public boolean instanceExists(String instanceId){
			for(InstanceRequest instanceRequest : instanceRequests){
				if(instanceRequest.getInstanceId().equals(instanceId))
					return true;
			}
			return false;
		}


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
			String body = parseRequestBody(t.getRequestBody());
			newArgs.add(body);

			// newArgs.add("-d");

			Integer estimatedCost = estimateRequestCost(solver, size, un);
			requestCostEstimation.put(requestId, estimatedCost);
			requestSolver.put(requestId, solver);
			System.out.println("Estimated cost: " + estimatedCost);

			try {
				Set<Instance> instances = AutoScaler.getReadyInstances().values();

				System.out.println("You have " + instances.size() + " Amazon EC2 instance(s) running.");
				
                Instance chosen_instance = choose_best_instance(instances);

                // TODO
                // Saves information about the request in a global structure

                // Send incoming request to the chosen Solver Instance
                // chosen_instance.getPublicDnsName() + ":8000/sudoku?" + query;
                // next line is for testing purposes, insert public DNS of the solver instance you want to test


                if(!instanceExists(name)){
                    InstanceRequest instanceRequest = new InstanceRequest(name);
                }
                instanceRequests.getQueries().add((String) query);
                instanceRequests.getRequestIds().add((long) requestId);
                instanceRequests.getBodies().add((String) body);

                String url = "http://" + chosen_instance.getPublicDnsName() + ":" + instancePort + "/sudoku?" + query + requestIdQuery;

                byte[] postData = newArgs.get(11).getBytes(StandardCharsets.UTF_8);
                URL myurl = new URL(url);

                HttpURLConnection con = (HttpURLConnection) myurl.openConnection();
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


                instanceRequests.getQueries().remove((String) query);
                instanceRequests.getRequestIds().remove((long) requestId);
                instanceRequests.getBodies().remove((String) body);

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

                JSONArray solution = new JSONArray();
                for(int lin = 0; lin<Integer.parseInt(newArgs.get(5)); lin++){
                    JSONArray line = new JSONArray();
                    for(int col = 0; col<Integer.parseInt(newArgs.get(7)); col++){
                        line.put(my_matrics[lin][col]);

                    }
                    solution.put(line);
                }

                System.out.println((requestMethodProgress.get(requestId)) + ":" + estimateCostByMethodNumber(solver, requestMethodProgress.get(requestId)));

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
            } catch (AmazonServiceException ase) {
				System.out.println("Caught Exception: " + ase.getMessage());
				System.out.println("Reponse Status Code: " + ase.getStatusCode());
				System.out.println("Error Code: " + ase.getErrorCode());
				System.out.println("Request ID: " + ase.getRequestId());
			}

			requestCostEstimation.remove(requestId);
			requestSolver.remove(requestId);
			requestMethodProgress.remove(requestId);
		}
	}
}