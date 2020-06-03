package pt.ulisboa.tecnico.cnv.server;

import java.util.ArrayList;
import java.util.List;

public class InstanceRequest {

	String instanceId;
	List<String> queries;
	List<Long> requestIds;
	List<String> bodies;

	public InstanceRequest(String instanceId) {
		this.instanceId = instanceId;
		queries = new ArrayList<String>();
		requestIds = new ArrayList<Long>();
		bodies = new ArrayList<String>();
	}

	public String getInstanceId(){
		return instanceId;
	}

	public List<String> getQueries(){
		return queries;
	}
	public List<Long> getRequestIds(){
		return requestIds;
	}
	public List<String> getBodies(){
		return bodies;
	}
}
