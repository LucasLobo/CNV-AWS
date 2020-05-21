package BIT;

import BIT.highBIT.*;
import java.io.*;
import java.util.*;


public class ICount {
  private static PrintStream out = null;
  private static int THREAD_ID = 12;

  private static int[] m_count_vector;

  /* main reads in all the files class files present in the input directory,
  * instruments them, and outputs them to the specified output directory.
  */
  public static void main(String argv[]) {
    File file_in = new File(argv[0]);
    String infilenames[] = file_in.list();

    for (int i = 0; i < infilenames.length; i++) {
      String infilename = infilenames[i];
      if (infilename.endsWith(".class")) {
        // create class info object
        ClassInfo ci = new ClassInfo(argv[0] + System.getProperty("file.separator") + infilename);

        // loop through all the routines
        // see java.util.Enumeration for more information on Enumeration class
        for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
          Routine routine = (Routine) e.nextElement();
          routine.addBefore("BIT/ICount", "mcount", new Integer(1));
        }
        ci.addAfter("BIT/ICount", "printICount", ci.getClassName());
        ci.write(argv[1] + System.getProperty("file.separator") + infilename);
      }
    }
  }

  public static synchronized void printICount(String foo) {
    int id = (int) Thread.currentThread().getId() - THREAD_ID;
    System.out.println(m_count_vector[id] + " basic blocks were executed in this request");
  }

  public static synchronized void mcount(int incr) {
    int id = (int) Thread.currentThread().getId() - THREAD_ID;
    m_count_vector[id]++;
  }
  public static void instanciateVector(int n){
    m_count_vector = new int[n];
    for (int i = 0; i < n; i++) {
      m_count_vector[i] = 0;
    }
  }

  public static int getBCount(){
    int id = (int) Thread.currentThread().getId() - THREAD_ID;
    return m_count_vector[id];
  }
  
  public static void resetVar(){
    int id = (int) Thread.currentThread().getId() - THREAD_ID;
    m_count_vector[id] = 0;
  }
}
