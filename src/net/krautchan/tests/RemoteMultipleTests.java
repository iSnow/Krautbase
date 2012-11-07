package net.krautchan.tests;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.HashMap;
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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class RemoteMultipleTests extends TestCase {
	public String url;
	public String resolverPath;
	public File downloadDir;
	public Map<Long, KCThread> newParserThreads = new HashMap<Long, KCThread>();
	public Map<Long, KCThread> domParserThreads = new HashMap<Long, KCThread>();
	

	public void setUp() {
		downloadDir = new File (Long.toString(new Date().getTime()));
		downloadDir.mkdir();
		for (int i = 0; i < 15; i++) {
			url = "http://krautchan.net/b/"+i+".html";
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
			} finally {
		        client.getConnectionManager().shutdown(); // Close the instance here
			}
		}
		
	}

	public void parseOneBoardPage(int i) throws Exception {
		File page = new File(downloadDir, i+".html");
		BufferedReader reader = new BufferedReader (new FileReader (page));
		
		KCPageParser parser = new KCPageParser("http://krautchan.net/b/", 12345)
			.setBasePath("http://krautchan.net/")
			.setThreadHandler(threadListener)
			.setPostingHandler(postListener);

		parser.parse(reader);
		reader.close();
		
		reader = new BufferedReader (new FileReader (page));
		//CharBuffer target =  CharBuffer.allocate ((int)page.length());
		
		String content = "";
		String line = reader.readLine();
		while (line != null) {
			content += line;
			line = reader.readLine();
		}
		reader.close();
		Document doc = Jsoup.parse(content);
		Elements threads = doc.select("div.thread");
		for (Element elem : threads) {
			Long id = Long.parseLong(elem.id().replace("thread_", ""));
			KCThread thread  = new KCThread ("/b/thread-"+id+".html");
			Assert.assertTrue(newParserThreads.get(id) != null);
			
			Elements threadContent = elem.select(".thread_body .postbody blockquote p");
			String tContent = "";
			for (Element para : threadContent) {
				tContent += para.outerHtml();
			}
			
			Elements followupPosts = elem.select("a[name]");
			for (Element fupPost : followupPosts) {
				System.out.println ("\n\n**"+fupPost.outerHtml());
			}
			
			System.out.println ("##"+tContent);
		
			System.out.println (">"+elem.html());
			domParserThreads.put(id, thread);
		}
	}

	public void testParseOneBoardPage() throws Exception {
		parseOneBoardPage(1);
	}
	
	/*public void testParseAllBoardPage() throws Exception {
		for (int i = 0; i < 15; i++) {
			parseOneBoardPage(i);
		}
	}*/
	
	
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
