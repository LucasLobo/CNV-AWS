package pt.ulisboa.tecnico.cnv.server;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import pt.ulisboa.tecnico.cnv.solver.Solver;
import pt.ulisboa.tecnico.cnv.solver.SolverArgumentParser;
import pt.ulisboa.tecnico.cnv.solver.SolverFactory;

import BIT.*;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.util.TableUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.lang.StringBuilder;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;
import java.text.SimpleDateFormat;

public class WebServer {
  static AmazonDynamoDB dynamoDB;
  static String tableName;

  public static void main(final String[] args) throws Exception {

    createMSS();

    final HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

    server.createContext("/test", new HealthCheckHandler());
    server.createContext("/sudoku", new SudokuSolverHandler());

    // be aware! infinite pool of threads!
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();

    System.out.println(server.getAddress().toString());
  }

  public static String parseRequestBody(InputStream is) throws IOException {
    InputStreamReader isr =  new InputStreamReader(is,"utf-8");
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
			tableName = "request-cost-table";

			// Create a table with a primary hash key named 'name', which holds a string
      CreateTableRequest createTableRequest = new CreateTableRequest()
              .withTableName(tableName)
              .withKeySchema(new KeySchemaElement().withAttributeName("request_id").withKeyType(KeyType.HASH))
					    .withAttributeDefinitions(new AttributeDefinition().withAttributeName("request_id").withAttributeType(ScalarAttributeType.N))
					    .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));

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
    
  private static void saveResultDB(int request_id, String solver, int size, int un, int cost) {
    Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
		item.put("request_id", new AttributeValue().withN(Integer.toString(request_id)));
		item.put("strategy", new AttributeValue(solver));
		item.put("size", new AttributeValue().withN(Integer.toString(size)));
		item.put("un", new AttributeValue().withN(Integer.toString(un)));
    item.put("cost", new AttributeValue().withN(Integer.toString(cost)));

    PutItemRequest putItemRequest = new PutItemRequest(tableName, item);
    dynamoDB.putItem(putItemRequest);
  }

  static class HealthCheckHandler implements HttpHandler {
    @Override
      public void handle(HttpExchange t) throws IOException {
        String response = "";
        t.sendResponseHeaders(200, response.length());
        OutputStream os = t.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }
  }

  static class SudokuSolverHandler implements HttpHandler {
    int methods = 0;

    String[] requestInfo = new String[6];
    //Solver -> un -> n -> n -> name -> board



    @Override
    public void handle(final HttpExchange t) throws IOException {

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

      // Store from ArrayList into regular String[].
      final String[] args = new String[newArgs.size()];
      int i = 0;
      for(String arg: newArgs) {
        args[i] = arg;
        if((i % 2) != 0){
          requestInfo[i/2] = arg;
        }
        i++;
      }

      MethodCounter.resetVar();
      // Get user-provided flags.
      final SolverArgumentParser ap = new SolverArgumentParser(args);

      // Create solver instance from factory.
      final Solver s = SolverFactory.getInstance().makeSolver(ap);

      //Solve sudoku puzzle
      JSONArray solution = s.solveSudoku();
      System.out.println("Thread id = " + Thread.currentThread().getId());
      methods = MethodCounter.getMethodCount();
      System.out.println("Number of methods were: " + methods);

      // Send response to browser.
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

      int request_id = ThreadLocalRandom.current().nextInt(0, 123123213);
      if (!solver.equals("undefined") && size != -1 && un != -1) saveResultDB(request_id, solver, size, un, methods);
    }
  }
}
