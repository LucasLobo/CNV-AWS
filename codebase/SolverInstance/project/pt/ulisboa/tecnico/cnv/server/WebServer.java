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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.lang.StringBuilder;
import java.util.Date;
import java.text.SimpleDateFormat;

public class WebServer {

  public static boolean debug = false;
  public static String LB_URL = "";

  public static void main(final String[] args) throws Exception {

    final HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

    server.createContext("/test", new HealthCheckHandler());
    server.createContext("/sudoku", new SudokuSolverHandler());

    // be aware! infinite pool of threads!
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();

    System.out.println(server.getAddress().toString());

    if(args.length != 0)
    if(args[0].equals("debug"))
    debug = true;
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
		final int UPDATE_TIME_INTERVAL = 10000;
    long requestId;
		boolean finished = false;


    private URL sendUpdate(int requestId){
      String query = requestId + "&" + methods;
      String url = LB_URL + ":8000/update?" + query;

      URL myUrl = new URL(url);
      con = (HttpURLConnection) myUrl.openConnection();

      con.setDoOutput(true);
      con.setRequestMethod("POST");
      con.setRequestProperty("User-Agent", "Java client");
      con.setRequestProperty("Content-Type", "application/json");

      DataOutputStream out = new DataOutputStream(con.getOutputStream()))
      out.flush();
      out.close();
      con.disconnect();
    }


    @Override
    public void handle(final HttpExchange t) throws IOException {
      // Get the query.
      final String query = t.getRequestURI().getQuery();
      System.out.println("> Query:\t" + query);

      // Break it down into String[].
      final String[] params = query.split("&");

      // Store as if it was a direct call to SolverMain.
      final ArrayList<String> newArgs = new ArrayList<>();
      for (final String p : params) {
        final String[] splitParam = p.split("=");
        if(splitParam[0].equals("req")){
          requestId = splitParam[2];
        }else{
          newArgs.add("-" + splitParam[0]);
          newArgs.add(splitParam[1]);
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
      
      //TODO - thread x a x tempo enviar url
			new Thread(new Runnable() {

			@Override
			public void run() {
				try{
					while(!finished){
						Thread.currentThread().sleep(UPDATE_TIME_INTERVAL);
						methods = MethodCounter.getMethodCount();
						sendUpdate();
					}
				}catch(InterruptedException e){
				}

			}
		}).start();

      
      // Get user-provided flags.
      final SolverArgumentParser ap = new SolverArgumentParser(args);

      // Create solver instance from factory.
      final Solver s = SolverFactory.getInstance().makeSolver(ap);
      //Solve sudoku puzzle
      JSONArray solution = s.solveSudoku();
      System.out.println("Thread id = " + Thread.currentThread().getId());
      methods = MethodCounter.getMethodCount();
			finished = true;
			sendUpdate();
      System.out.println("Number of methods were: " + methods);

      // Send response to browser.
      final Headers hdrs = t.getResponseHeaders();

      //t.sendResponseHeaders(200, responseFile.length());


      ///hdrs.add("Content-Type", "image/png");
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

      if(debug)
      printToFile();
    }


    private void printToFile(){
      StringBuilder filename = new StringBuilder();
      filename.append("~/metrics/logs/").append(requestInfo[0]).append("-").append(requestInfo[2]).append("x").append(requestInfo[3]).append("-").append(requestInfo[1]).append("-").append(new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SS").format(new Date()));
      try(PrintWriter writer = new PrintWriter(new File(filename.toString() + ".txt"))){
        writer.write(methods);
        writer.flush();
        System.out.println("Wrote to file.");
      } catch(FileNotFoundException e){
        System.out.println(e.getMessage());
      }
    }
  }
}
