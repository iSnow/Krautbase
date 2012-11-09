package net.krautchan.tests;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;
import junit.framework.TestCase;
import net.krautchan.data.KCPosting;
import net.krautchan.data.KCThread;
import net.krautchan.data.KODataListener;
import net.krautchan.parser.KCPageParser;

public class LocalTests extends TestCase {
	private String url;
	private long boardDbId;
	private File boardPage;
	private File threadPage;
	private Reader boardFileReader;
	private Reader threadFileReader;
	private KCPageParser boardParser;
	private KCPageParser threadParser;
	public File downloadDir;
	public Map<Long, KCThread> newParserThreads = new LinkedHashMap<Long, KCThread>();
	public Map<Long, KCThread> domParserThreads = new LinkedHashMap<Long, KCThread>();
	
	public LocalTests() {
		super();
	}
	

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		boardDbId = 1337;
		/*boardPage = new File ("testdata/page_reduced.html");
		threadPage = new File ("thread-29953.html");
		if ((null == boardPage) || (!boardPage.exists())) {
			TestCase.fail("Board Page file does not exist");
		}
		boardFileReader = new FileReader (boardPage);
		if ((null == threadPage) || (!threadPage.exists())) {
			TestCase.fail("Thread Page file does not exist");
		}
		threadFileReader = new FileReader (boardPage);*/
	}
	
	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
	}
	
	public void parseOneBoardPage(File page) throws Exception {
		BufferedReader reader = new BufferedReader (new FileReader (page));
		
		KCPageParser parser = new KCPageParser("http://krautchan.net/b/", 12345)
			.setBasePath("http://krautchan.net/")
			.setThreadHandler(threadListener)
			.setPostingHandler(postListener);

		parser.parse(reader);
		reader.close();		
		reader = new BufferedReader (new FileReader (page));
		
		domParserThreads = TestParser.parseBoardPage(reader);
	}
	
	/*public void testParseBoardPage() throws Exception {
		boardParser = new KCPageParser("file:///_kc_.html", boardDbId);
		boardParser.setPostingHandler(new KODataListener<KCPosting>() {

			public void notifyAdded(KCPosting item, Object token) {
				assertNotNull("KC Nummer null", item.getKcNummer());
				assertNotNull("DB ID null", item.getDbId());
				assertNotNull("Content null", item.getContent());
				assertNotNull("URI null", item.getUri());
				System.out.println ("POST " +token + " " + item.hashCode());
			}

			public void notifyDone(Object token) {
				System.out.println ("POST " +token + " Done");
			}

			public void notifyError(Exception ex, Object token) {
				TestCase.fail("POST " +token+ " " + ex.getMessage());
			}
		});
		
		boardParser.setThreadHandler(new KODataListener<KCThread>() {

			public void notifyAdded(KCThread item, Object token) {
				assertNotNull("KC Nummer null", item.kcNummer);
				assertNotNull("DB ID null", item.getDbId());
				assertNotNull("Digest null", item.digest);
				assertNotNull("First Post Date null", item.firstPostDate);
				assertNotNull("Last Post Date null", item.lastPostDate);
				assertNotNull("URI null", item.getUri());
				System.out.println ("THREAD " +token + " " + item.hashCode());
			}

			public void notifyDone(Object token) {
				System.out.println ("THREAD " + token + " Done");
			}

			public void notifyError(Exception ex, Object token) {
				TestCase.fail("THREAD " + token+ " " + ex.getMessage());
			}
		});
		boardParser.setBasePath(url);
		List<KCThread> threads = boardParser.filterThreads(boardFileReader);
	}*/
	
	public void testParseOneBoardPageLocal() throws Exception {
		//downloadDir = new File ("1352151639011");
		parseOneBoardPage(new File ("testdata/1352151639011/5.html"));
		for (Long key : newParserThreads.keySet()) {
			KCThread domParserThread = domParserThreads.get(key);
			KCThread newParserThread = newParserThreads.get(key);
			if (!newParserThread.equals(domParserThread)) {	
				System.out.println("MINE");
				System.out.println(newParserThread);
				System.out.println("JSOUP");
				System.out.println(domParserThread);
			}
			Assert.assertTrue(newParserThread.equals(domParserThread));
			//Assert.assertEquals(newParserThread.hashCode(), domParserThread.hashCode());
		}
	}
	
	public void iterate (File parent, List<File> testFiles) {
		if (parent.isFile() && (!parent.getName().startsWith("."))) {
			testFiles.add(parent);
		} else if (parent.isDirectory()) {
			File children[] = parent.listFiles(new FileFilter() {
				@Override
				public boolean accept(File arg0) {
					return (!arg0.getName().startsWith("."));
				}	
			});
			for (File child : children) {
				iterate (child, testFiles);
			}
		}
	}
	
	public void testParseAll() throws Exception {
		File testDataDir = new File ("testdata");
		ArrayList<File> testFiles = new ArrayList<File>();
		iterate (testDataDir, testFiles);
		for (File f: testFiles) {
			newParserThreads = new LinkedHashMap<Long, KCThread>();
			domParserThreads = new LinkedHashMap<Long, KCThread>();
			System.out.println ("FILE: "+f.getName());
			parseOneBoardPage(f);
			for (Long key : newParserThreads.keySet()) {
				KCThread domParserThread = domParserThreads.get(key);
				KCThread newParserThread = newParserThreads.get(key);
				if (!newParserThread.equals(domParserThread)) {	
					System.out.println ("FILE: "+f.getName());
					System.out.println("MINE");
					System.out.println(newParserThread);
					System.out.println("JSOUP");
					System.out.println(domParserThread);
				}
				Assert.assertTrue(newParserThread.equals(domParserThread));
				//Assert.assertEquals(newParserThread.hashCode(), domParserThread.hashCode());
			}
		}
	}
	
	private KODataListener<KCThread> threadListener = new KODataListener<KCThread>() {
		@Override
		public void notifyAdded(KCThread item, Object token) {
			newParserThreads.put(item.kcNummer, item);
		}

		@Override
		public void notifyDone(Object token) {
		}

		@Override
		public void notifyError(Exception ex, Object token) {
			Assert.fail(token.toString()+""+ex.getStackTrace());
		}
	};
	
	private static KODataListener<KCPosting> postListener = new KODataListener<KCPosting>() {
		@Override
		public void notifyAdded(KCPosting item, Object token) {
		}

		@Override
		public void notifyDone(Object token) {
		}

		@Override
		public void notifyError(Exception ex, Object token) {
			Assert.fail(token.toString()+""+ex.getStackTrace());
		}
	};
}
