package net.krautchan.data;

public class KCTaskStatus extends KrautObject {
	public String taskName;
	public STATUS_CODE status;
	public String lastTry;
	public String lastSuccess;
	
	

	public static enum STATUS_CODE {
		ok,
		stuck,
		failed,
		didnotstart
	}
}
