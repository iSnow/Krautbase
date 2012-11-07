package net.krautchan.parser;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

public class KCPageParserRunner implements Runnable {
	private String resolverPath = null;
	private KCPageParser parser;
	public HttpClient client;
	public String url;

	public KCPageParserRunner(String url, String basePath, KCPageParser parser) {
		this.url = url;
		this.resolverPath = basePath;
		this.parser = parser;
	}
	
	@Override
	public void run() {
		client = new DefaultHttpClient();
		HttpGet request;
		if (url.startsWith("/")) {
			request = new HttpGet (resolverPath + url.substring(1));
		} else {
			request = new HttpGet (url);
		}
		client.getParams().setParameter("Range", "bytes=42000-");
		try {
			HttpResponse response = client.execute(request);
			BufferedReader reader = new BufferedReader (new InputStreamReader (response.getEntity().getContent()));
			parser.setBasePath(resolverPath);
			parser.parse (reader);
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