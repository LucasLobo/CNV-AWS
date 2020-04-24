	/* ICount.java
 * Sample program using BIT -- counts the number of instructions executed.
 *
 * Copyright (c) 1997, The Regents of the University of Colorado. All
 * Rights Reserved.
 * 
 * Permission to use and copy this software and its documentation for
 * NON-COMMERCIAL purposes and without fee is hereby granted provided
 * that this copyright notice appears in all copies. If you wish to use
 * or wish to have others use BIT for commercial purposes please contact,
 * Stephen V. O'Neil, Director, Office of Technology Transfer at the
 * University of Colorado at Boulder (303) 492-5647.
 */
 
package BIT;
 
import BIT.highBIT.*;
import java.io.*;
import java.util.*;


public class ICount {
    private static PrintStream out = null;
    private static int THREAD_ID = 12;
    
    private static int[] b_count_vector;
    
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
                    
                    for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
                        BasicBlock bb = (BasicBlock) b.nextElement();
                        bb.addBefore("BIT/ICount", "count", new Integer(bb.size()));
                    }
                }
                ci.addAfter("BIT/ICount", "printICount", ci.getClassName());
                ci.write(argv[1] + System.getProperty("file.separator") + infilename);
            }
        }
    }
    
    public static synchronized void printICount(String foo) {
        int id = (int) Thread.currentThread().getId() - THREAD_ID;
        System.out.println(b_count_vector[id] + " basic blocks were executed in this request");
    }
    

    public static synchronized void count(int incr) {
        int id = (int) Thread.currentThread().getId() - THREAD_ID;
        b_count_vector[id]++;
        //i_count += incr;
        //b_count++;
    }

    public static synchronized void mcount(int incr) {
		//m_count++;
    }
    public static void instanciateVector(int n){
        b_count_vector = new int[n];
        for(int i = 0; i!= n; i++){
            b_count_vector[i] = 0;
        }
    }
    
    public static int getBCount(){
        int id = (int) Thread.currentThread().getId() - THREAD_ID;
        return b_count_vector[id];
    }
    public static void resetVar(){
        int id = (int) Thread.currentThread().getId() - THREAD_ID;
        b_count_vector[id] = 0;
        
    }
}

