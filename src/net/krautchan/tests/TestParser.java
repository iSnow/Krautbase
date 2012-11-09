package net.krautchan.tests;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.krautchan.data.KCPosting;
import net.krautchan.data.KCThread;

import org.apache.commons.lang3.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class TestParser {
	
	public static Map<Long, KCThread> parseBoardPage(BufferedReader reader) {
		String content = "";
		Map<Long, KCThread> domParserThreads = new LinkedHashMap<Long, KCThread>();	
		try {
			String line;
			line = reader.readLine();
			while (line != null) {
				content += line;
				line = reader.readLine();
			}
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		Document doc = Jsoup.parse(content);
		String title = doc.select("title").first().text();
		title = title.replaceAll("\\s+-\\s+.+", "").substring(1);
		String boardShortName = title.substring(0, title.length()-1);
		
		Elements threads = doc.select("div.thread");
		for (Element elem : threads) {
			Long id = Long.parseLong(elem.id().replace("thread_", ""));
			KCThread thread  = new KCThread ("/"+boardShortName+"/thread-"+id+".html");
			
			Elements threadContent = elem.select(".thread_body .postbody blockquote");
			String tContent = threadContent.html();
			tContent = tContent.replaceFirst("<p.*?>", "");
			if (tContent.endsWith("</p>")) {
				tContent  = tContent.substring(0, tContent.length()-4);
			};
			tContent = tContent.replaceAll("</p>\\s+<p", "</p><p");
			tContent = tContent.replaceAll("[\\r\\n]+ *", "");
			KCPosting posting = new KCPosting();
			posting.setKcNummer(id);
			posting.setTitle(elem.select(".postsubject").get(0).text());
			posting.setUser(elem.select(".postername").get(0).text());
			posting.setDate(elem.select(".postdate").get(0).text());
			posting.setSage(elem.select(".thread_body>.postheader .sage").size() != 0);
			posting.setOriginalContent(StringEscapeUtils.unescapeHtml4(tContent));
			posting.setContent(tContent);
			posting.setUri(thread.getUri());
			Elements imageNodes = elem.select(".file_thread");

			for (Element imageNode : imageNodes) {
				String href[] = imageNode.select(".filename a").get(0).attr("href").replace("/download/", "").split("/");
				posting.addFile(href[0], href[1]);
			}
			Set<String> keys = posting.getFileUids();
			thread.addPosting(posting);
			Elements followupPosts = elem.select(".thread_body>a[name]+table");
			for (Element fupPost : followupPosts) {
				//System.out.println ("\n\n**"+fupPost.outerHtml());
				long kcNum = Long.parseLong(fupPost.select("td.postreply").get(0).id().replace("post-", ""));
				//System.out.println ("**"+kcNum);
				KCPosting post = new KCPosting();
				post.setKcNummer(kcNum);
				
				Elements postContent = fupPost.select("div blockquote");
				String pContent = postContent.html();
				pContent = pContent.replaceFirst("<p.*?>", "");
				if (pContent.endsWith("</p>")) {
					pContent  = pContent.substring(0, pContent.length()-4);
				};
				pContent = pContent.replaceAll("</p>\\s+<p", "</p><p");
				pContent = pContent.replaceAll("[\\r\\n]+ *", "");
				post.setTitle(fupPost.select(".postsubject").get(0).text());
				post.setUser(fupPost.select(".postername").get(0).text());
				post.setDate(fupPost.select(".postdate").get(0).text());
				post.setSage(fupPost.select(".sage").size() != 0);
				post.setOriginalContent(StringEscapeUtils.unescapeHtml4(pContent));
				post.setContent(pContent);
				String postUrl = thread.getUri() + '#' + kcNum;
				post.setUri(thread.getUri() + '#' + kcNum);
				Elements replyImageNodes = fupPost.select(".file_reply");

				for (Element imageNode : replyImageNodes) {
					String href[] = imageNode.select(".filename a").get(0).attr("href").replace("/download/", "").split("/");
					post.addFile(href[0], href[1]);
				}
				Set<String> rKeys = post.getFileUids();
				thread.addPosting(post);
			}
			domParserThreads.put(id, thread);
		}
		return domParserThreads;	
	}

}
