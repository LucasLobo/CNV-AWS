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
import java.net.URL;
import java.net.HttpURLConnection;
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

  public static String LB_port = "8000";
  static AmazonDynamoDB dynamoDB;
  static String tableName = "request-cost-table";

  public static void main(final String[] args) throws Exception {

    init();
    final HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

    server.createContext("/test", new HealthCheckHandler());
    server.createContext("/sudoku", new SudokuSolverHandler());

    // be aware! infinite pool of threads!
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();

    System.out.println(server.getAddress().toString());
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

  private static void init() {
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
  }

  private static void saveResultDB(long request_id, String solver, int size, int un, int cost) {
    Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
    item.put("request_id", new AttributeValue().withN(Long.toString(request_id)));
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
    static final int UPDATE_TIME_INTERVAL = 1000;

    HashMap<Integer, Boolean> finished = new HashMap<>();

    @Override
    public void handle(final HttpExchange t) throws IOException {
      final String remote_address = t.getRemoteAddress().getAddress().toString().split("/")[1];
      final int thread_id = (int) Thread.currentThread().getId();
      long temp_req_id = -1;

      finished.put(thread_id, false);

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
        if (splitParam[0].equals("req")) {
          temp_req_id = Long.parseLong(splitParam[1]);
        } else {
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
      }

      final long request_id = temp_req_id;

      newArgs.add("-b");
      newArgs.add(parseRequestBody(t.getRequestBody()));

      // newArgs.add("-d");

      new Thread(new Runnable() {
        @Override
        public void run() {
          try {
            Thread.sleep(UPDATE_TIME_INTERVAL);
            while (!finished.get(thread_id)) {
              int method_progress = MethodCounter.getMethodCount(thread_id);
              // System.out.println(method_progress);
              sendUpdate(request_id, remote_address, method_progress);
              Thread.sleep(UPDATE_TIME_INTERVAL);
            }
          } catch (InterruptedException e) {
          }
        }
      }).start();

      final String[] args = newArgs.toArray(new String[0]);
      // Get user-provided flags.
      final SolverArgumentParser ap = new SolverArgumentParser(args);

      // Create solver instance from factory.
      final Solver s = SolverFactory.getInstance().makeSolver(ap);

      MethodCounter.resetVar();
      // Solve sudoku puzzle
      JSONArray solution = s.solveSudoku();
      // System.out.println("Thread id = " + Thread.currentThread().getId());
      int final_methods = MethodCounter.getMethodCount(thread_id);
      finished.put(thread_id, true);
      sendUpdate(request_id, remote_address, final_methods);
      // System.out.println("Number of methods were: " + final_methods);

      // Send response to browser.
      final Headers hdrs = t.getResponseHeaders();

      hdrs.add("Content-Type", "application/json");
      hdrs.add("Access-Control-Allow-Origin", "*");
      hdrs.add("Access-Control-Allow-Credentials", "true");
      hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
      hdrs.add("Access-Control-Allow-Headers",
          "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");

      t.sendResponseHeaders(200, solution.toString().length());

      final OutputStream os = t.getResponseBody();
      OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
      osw.write(solution.toString());
      osw.flush();
      osw.close();
      os.close();
      System.out.println("> Sent response to " + t.getRemoteAddress().toString());
      if (!solver.equals("undefined") && size != -1 && un != -1)
        saveResultDB(request_id, solver, size, un, final_methods);
    }

    private static void sendUpdate(long request_id, String remote_address, int methods) {
      String query = "r=" + request_id + "&" + "m=" + methods;
      String url = "http://" + remote_address + ":" + LB_port + "/update?" + query;
      // System.out.println(">>> " + url);
      try {
        URL myUrl = new URL(url);
        HttpURLConnection con = (HttpURLConnection) myUrl.openConnection();

        con.setRequestMethod("POST");
        con.setRequestProperty("User-Agent", "Java client");
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        con.setDoOutput(true);

        DataOutputStream out = new DataOutputStream(con.getOutputStream());
        out.flush();
        out.close();
        con.getResponseCode();
        con.disconnect();
      } catch (Exception e) {
        System.out.println(e);
      }

    }
  }
}
