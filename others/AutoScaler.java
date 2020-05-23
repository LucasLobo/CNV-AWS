public class AutoScaler{

  final static int TIME_INTERVAL_CHECKING = 60000;

  public static void main(String args[]){
    try{
      while(true){
        EC2LaunchWaitTerminate.main(args);
        Thread.currentThread().sleep(TIME_INTERVAL_CHECKING);
      }
  }catch(InterruptedException e){
  }catch(Exception e2){}
  }

}
