package net.krautchan.parser;

import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.krautchan.data.KCBoard;
import net.krautchan.data.KCPosting;
import net.krautchan.data.KCThread;
import net.krautchan.data.KrautObject;

import org.apache.commons.lang3.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;

public class KCParser {
	private Logger errLog;
	private boolean debug;
	private static SimpleDateFormat fm = new SimpleDateFormat ("dd.MM. - HH:mm:ss");
	
	public KCParser(Logger logger, boolean b) {
		errLog = logger;
		debug = b;
	}


	public static Map<String, String> getBoardList () throws IOException {
		Map<String, String> boards = new LinkedHashMap<String, String>();
		Document doc = Jsoup.connect("http://krautchan.net/nav")
		  .userAgent("Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.6; de; rv:1.9.2.13) Gecko/20100101 Firefox/4.0b9")
		  .cookie("auth", "token")
		  .timeout(3000)
		  .get();
		
		Elements boardlist = doc.select(".boardlist li");
		Iterator<Element> iter = boardlist.iterator();
		while (iter.hasNext()) {
			Element elem = iter.next();
			String id = elem.id();
			if (id.startsWith("board_")) {
				Elements links = elem.select("a");
				if ((null != links) && (null != links.get(0))) {
					String content = links.get(0).ownText();
					String[] keyVal = content.split("\\s*-\\s*");
					String key = keyVal[0].trim().replaceAll("/", "");
					boards.put(key, keyVal[1].trim());
				}
			}
		}
		return boards;
	}
	
	public static Map<String, KCBoard> getBoardList (String urlStr) throws IOException {
		Map<String, KCBoard> boards = new LinkedHashMap<String, KCBoard>();
		Document doc = Jsoup.connect(urlStr)
		  .userAgent("Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.6; de; rv:1.9.2.13) Gecko/20100101 Firefox/4.0b9")
		  .cookie("auth", "token")
		  .timeout(3000)
		  .get();
		
		Elements boardlist = doc.select(".boardlist li");
		Iterator<Element> iter = boardlist.iterator();
		while (iter.hasNext()) {
			Element elem = iter.next();
			String idStr = elem.id();
			if (idStr.startsWith("board_")) {
				Elements links = elem.select("a");
				if ((null != links) && (null != links.get(0))) {
					KCBoard board = new KCBoard();
					board.setUri (links.attr("href"));
					String content = links.get(0).ownText();
					String[] keyVal = content.split("\\s*-\\s*");
					board.shortName = keyVal[0].trim().replaceAll("/", "");
					board.name = keyVal[1].trim();
					board.setDbId((long) getUniqueId (board));
					boards.put(board.shortName, board);
				}
			}
		}
		return boards;
	}
	
	
	public Collection<KrautObject> parseBoard (String baseUrlStr, String boardName) {
		List<KrautObject> threads = new ArrayList<KrautObject>();
		XmlReader reader = null;
		try {			
			if (baseUrlStr.endsWith("/")) {
				baseUrlStr = baseUrlStr.subSequence(0, baseUrlStr.length()-1).toString();
			}
			URL rssUri = new URL (baseUrlStr+boardName);
			reader = new XmlReader(rssUri);
			SyndFeed feed = null;
			try {
				feed = getFeed(reader);
			} catch (FeedException ex) {
				try {
					Thread.sleep(1000);
					reader = new XmlReader(rssUri);
					feed = getFeed(reader);
				} catch (FeedException ex2) {
					Thread.sleep(1000);
					reader = new XmlReader(rssUri);
					feed = getFeed(reader);
				}
			}
			@SuppressWarnings("unchecked")
			List<SyndEntry> entries = feed.getEntries();
			for (SyndEntry obj : entries) {
				KCThread tp = new KCThread();
				String path = obj.getLink();
				int delim = path.indexOf(boardName);
				String threadId  = path.substring(delim+boardName.length(), path.length());
				tp.kcNummer = Long.parseLong(threadId);
				tp.firstPostDate = 0L;
				threads.add (tp);
				KCPosting pt = new KCPosting();
				pt.setDbId((long) (Math.random() * Long.MAX_VALUE));
				pt.setContent(sanitizeContent (obj));
				pt.setTitle(sanitizeTitle (obj, threadId));
				pt.setDate(obj.getPublishedDate());
				pt.setKcNummer (tp.kcNummer);		
				pt.setDbId(Long.valueOf(getUniqueId (pt)));
				tp.setDbId(pt.getDbId());
				pt.setThreadDbId(tp.getDbId());
				tp.addPosting(pt);
				threads.add(pt);
			}
		} catch (Exception ex) {
			errLog.log(Level.SEVERE, ex.getMessage(), ex);
			ex.printStackTrace();
		}
		return threads;
	}
	
	
	public static String sanitizeTitle (SyndEntry entry, String threadId) {
		String title = entry.getTitle().replace(threadId+":", "").trim();
		if (title.endsWith("...")) // yeah, I am too dumb to find out how to escape '.' in Java regexp
			title = title.substring (0, title.length()-3);

		String content = StringEscapeUtils.unescapeHtml4(entry.getDescription().getValue().replace("<p>", "").trim());
		content = content.replaceAll("<a href=\"/resolve/.+?\">", "");
		content = content.replaceAll("</a>", "");
		content = content.replaceAll("<span class=\"quote\">", "");
		content = content.replaceAll("</span", "");
		content = content.replace("<br>", "");
		if ((content.trim().length() == 0) || (content.startsWith(title)))
			return null;
		if (title.length() > 40) {
			title = title.substring(0, 40)+"...";
		}
		return title;
	}
	
	public static String sanitizeContent (SyndEntry entry) {
		String content = StringEscapeUtils.unescapeHtml4(entry.getDescription().getValue().trim());
		content = content.replaceAll("<p>", "");
		content = content.replaceAll("</p>", " ");
		int delim = content.indexOf("<a href=\"http://krautchan.net/download/");
		if (delim != -1) {
			String imgSection = content.substring(delim).trim();
			content = content.substring(0, delim-1).trim();
			content = "<p><span>"+content+"</span></p>";
			content = content+"<div class=\"image-container\">"+imgSection+"</div>";
		} else {
			content = "<p><span>"+content+"</span></p>";
		}
		content = content.replaceAll(">>>(\\d+)</a>", "><span class=\"kclnk\">☛</span> $1</a>");
		return content;
	}
	

	public Collection<KCPosting> parseThread (String baseUrlStr, Long threadNum, Long threadDbId) {
		List<KCPosting> posts = new ArrayList<KCPosting>();
		XmlReader reader = null;
		try {
			if (!baseUrlStr.endsWith("/")) {
				baseUrlStr = baseUrlStr+"/";
			}
			URL rssUri = new URL (baseUrlStr+threadNum.toString());
			reader = new XmlReader(rssUri);
			SyndFeed feed = null;
			try {
				feed = getFeed(reader);
			} catch (FeedException ex) {
				try {
					Thread.sleep(1000);
					reader = new XmlReader(rssUri);
					feed = getFeed(reader);
				} catch (FeedException ex2) {
					Thread.sleep(1000);
					reader = new XmlReader(rssUri);
					feed = getFeed(reader);
				}
			}
			@SuppressWarnings("unchecked")
			List<SyndEntry> entries = feed.getEntries();
			for (SyndEntry obj : entries) {
				KCPosting pt = new KCPosting();
				String path = obj.getLink();
				int delim = path.lastIndexOf("/");
				String postId  = path.substring(delim+1, path.length());
				pt.setKcNummer(Long.parseLong(postId));
				pt.setContent(sanitizeContent (obj));
				pt.setTitle(sanitizeTitle (obj, postId));
				pt.setDate(obj.getPublishedDate());
				pt.setDbId(Long.valueOf(getUniqueId (pt)));
				pt.setThreadDbId(threadDbId);
				posts.add(0, pt);
			}
		} catch (Exception ex) {
			errLog.log(Level.SEVERE, ex.getMessage(), ex);
			ex.printStackTrace();
		}
		return posts;
	}
	
	private static SyndFeed getFeed (XmlReader reader) throws IllegalArgumentException, FeedException {
		SyndFeedInput input = new SyndFeedInput();
		return input.build(reader);
	}
	
	/*
	 * Per http://stackoverflow.com/questions/1660501/what-is-a-good-64bit-hash-function-in-java-for-textual-strings
	 * Would have preferred a hash function returning long, but hey, for the number of postings we expect...
	 * 
	 * Must call this AFTER filling in the pt fields
	 */
	private static int getUniqueId (KCPosting pt) {
		if ((null == pt.getCreationDate()) || (null == pt.getTitle()) || (null == pt.getKcNummer()) || (null == pt.getContent()))
			throw new IllegalArgumentException ("Transport Fields cannot be null. Hint: Must get Unique ID after filling in the transport's fields");
		String completeContent = pt.getCreationDate() + pt.getTitle() + pt.getKcNummer() + pt.getContent();
		return completeContent.hashCode();
	}
	

	/*
	 * Per http://stackoverflow.com/questions/1660501/what-is-a-good-64bit-hash-function-in-java-for-textual-strings
	 * Would have preferred a hash function returning long, but hey, for the number of postings we expect...
	 * 
	 * Must call this AFTER filling in the pt fields
	 */
	private static int getUniqueId (KCBoard pt) {
		if ((null == pt.shortName) || (null == pt.name) || (null == pt.getUri()) )
			throw new IllegalArgumentException ("Transport Fields cannot be null. Hint: Must get Unique ID after filling in the transport's fields");
		String completeContent = pt.shortName + pt.name + pt.getUri();
		return completeContent.hashCode();
	}
	

	public static void main(String[] args) {
		try {
			Map<String, String> boards = getBoardList ();
			for (String key: boards.keySet()) {
				System.out.println (key + " : "+boards.get(key));
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}
}
