package net.krautchan.tests;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import junit.framework.Assert;
import junit.framework.TestCase;
import net.krautchan.data.KCPosting;
import net.krautchan.data.KCThread;
import net.krautchan.data.KODataListener;
import net.krautchan.parser.KCPageParser;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

public class RemoteTests extends TestCase {
	public String url;
	public String resolverPath;
	public File downloadDir;
	public Map<Long, KCThread> newParserThreads = new LinkedHashMap<Long, KCThread>();
	public Map<Long, KCThread> domParserThreads = new LinkedHashMap<Long, KCThread>();
	

	public void setUp() {
		downloadDir = new File (Long.toString(new Date().getTime()));
		downloadDir.mkdir();
	}
	
	public void downloadPage (int i, String boardShortName) {
		String url = "http://krautchan.net/"+boardShortName+"/"+i+".html";
		DefaultHttpClient client = new DefaultHttpClient();
		HttpGet request;
		if (url.startsWith("/")) {
			request = new HttpGet (resolverPath + url.substring(1));
		} else {
			request = new HttpGet (url);
		}
		//client.getParams().setParameter("Range", "bytes=42000-");
		try {
			HttpResponse response = client.execute(request);
			BufferedReader reader = new BufferedReader (new InputStreamReader (response.getEntity().getContent()));
			
			BufferedWriter writer = new BufferedWriter (new FileWriter (new File(downloadDir, i+".html")));
			String line = reader.readLine();
			while (line != null) {
				writer.write(line);
				line = reader.readLine();
			}
			reader.close();
			if (response.getEntity() != null ) {
				response.getEntity().consumeContent();
			}
			
		} catch (Exception e) {
			Assert.fail(e.getMessage());
		} finally {
	        client.getConnectionManager().shutdown(); // Close the instance here
		}
	}

	public void parseOneBoardPage(int i, String boardShortName) throws Exception {
		File page = new File(downloadDir, i+".html");
		BufferedReader reader = new BufferedReader (new FileReader (page));
		
		KCPageParser parser = new KCPageParser("http://krautchan.net/"+boardShortName+"/", 12345)
			.setBasePath("http://krautchan.net/")
			.setThreadHandler(threadListener)
			.setPostingHandler(postListener);

		parser.parse(reader);
		reader.close();		
		reader = new BufferedReader (new FileReader (page));
		
		domParserThreads = TestParser.parseBoardPage(reader);
	}
	

	/*public void testParseOneBoardPage() throws Exception {
		parseOneBoardPage(1);
		for (Long key : newParserThreads.keySet()) {
			KCThread domParserThread = domParserThreads.get(key);
			KCThread newParserThread = newParserThreads.get(key);
			System.out.println ("<#> "+key+ " "+ domParserThread.digest + " <-> "+newParserThread.digest);
			if (!newParserThread.equals(domParserThread)) {	
				System.out.println("MINE");
				System.out.println(newParserThread);
				System.out.println("JSOUP");
				System.out.println(domParserThread);
			}
			Assert.assertTrue(newParserThread.equals(domParserThread));
			//Assert.assertEquals(newParserThread.hashCode(), domParserThread.hashCode());
		}
	}*/
	
	public void testParseAllBoardPage() throws Exception {
		String boardShortName = "b";
		for (int i = 0; i < 15; i++) {
			System.out.println (">>>>>> PAGE "+i);
			newParserThreads = new LinkedHashMap<Long, KCThread>();
			domParserThreads = new LinkedHashMap<Long, KCThread>();
			downloadPage (i, boardShortName);
			parseOneBoardPage(i, boardShortName);
			boolean ok = true;
			for (Long key : newParserThreads.keySet()) {
				KCThread domParserThread = domParserThreads.get(key);
				KCThread newParserThread = newParserThreads.get(key);
				if (!newParserThread.equals(domParserThread)) {	
					System.out.println("MINE");
					System.out.println(newParserThread);
					System.out.println("JSOUP");
					System.out.println(domParserThread);
					ok = false;
				} 
				Assert.assertTrue(newParserThread.equals(domParserThread));
				//Assert.assertEquals(newParserThread.hashCode(), domParserThread.hashCode());
			}
			if (ok) {
				File testFile = new File (downloadDir, i+".html");
				testFile.delete();
			}

		}
		File[] files = downloadDir.listFiles(new FileFilter() {
			@Override
			public boolean accept(File arg0) {
				return (!arg0.getName().startsWith("."));
			}
			
		});
		if (files.length == 0) {
			downloadDir.delete();
		}
	}
	
	
	private KODataListener<KCThread> threadListener = new KODataListener<KCThread>() {
		@Override
		public void notifyAdded(KCThread item, Object token) {
			System.out.println ("Added: "+item.getUri());
			newParserThreads.put(item.kcNummer, item);
		}

		@Override
		public void notifyDone(Object token) {
			System.out.println ("Done: "+token.toString());
		}

		@Override
		public void notifyError(Exception ex, Object token) {
			Assert.fail(token.toString()+""+ex.getStackTrace());
		}
	};
	
	private static KODataListener<KCPosting> postListener = new KODataListener<KCPosting>() {
		@Override
		public void notifyAdded(KCPosting item, Object token) {
			System.out.println ("Added: "+item.getUri());
		}

		@Override
		public void notifyDone(Object token) {
			System.out.println ("Done: "+token.toString());
		}

		@Override
		public void notifyError(Exception ex, Object token) {
			Assert.fail(token.toString()+""+ex.getStackTrace());
		}
	};
}
