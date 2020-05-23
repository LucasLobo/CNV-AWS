package BIT;

import BIT.highBIT.*;
import java.io.*;
import java.util.*;


public class MethodCounter {
  private static PrintStream out = null;
  private static HashMap<Integer, Integer> m_counts = new HashMap<>();

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
          routine.addBefore("BIT/MethodCounter", "mcount", new Integer(1));
        }
        ci.write(argv[1] + System.getProperty("file.separator") + infilename);
      }
    }
  }

  public static synchronized void mcount(int incr) {
    int id = (int) Thread.currentThread().getId();
    Integer current_m_count = m_counts.get(id);
    if (current_m_count == null) {
       m_counts.put(id, 1);
    }
    else {
      m_counts.put(id, ++current_m_count);
    }
  }

  public static int getMethodCount(){
    int id = (int) Thread.currentThread().getId();
    return m_counts.get(id);
  }

  public static void resetVar(){
    int id = (int) Thread.currentThread().getId();
    m_counts.put(id, 0);
  }
}
