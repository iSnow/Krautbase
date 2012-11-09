package net.krautchan.data;

/*
* Copyright (C) 2011 Johannes Jander (johannes@jandermail.de)
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;

public class KCPosting extends KrautObject implements Comparable<KCPosting>{
	private static final long serialVersionUID = 2343973223217952495L;
	private static final SimpleDateFormat df = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss");
	// we get the date as: 		EEE, dd MMM yyyy HH:mm:ss z"
	// we need to write it as: 	2008-07-17T09:24:17Z
	private static SimpleDateFormat dfShort = new SimpleDateFormat ("dd.MM. HH:mm");
	private static SimpleDateFormat dfOut = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss");
	//private static final Pattern imgPat = Pattern.compile("href=\"/download/(\\d+\\..+?)/(.+?)\"");
	//private static final Pattern imgPat = Pattern.compile("a\\s+href=\"/files/(\\d+\\..+?)\".+?src=\"?/thumbnails/(\\d+\\..+?)\\s\"?", Pattern.DOTALL);
	private static final Pattern imgPat = Pattern.compile("a\\s+href=\"/download/(\\d+\\..+?)/(.+?)\"", Pattern.DOTALL);

	private static final Pattern linkPat = Pattern.compile("https?://w?w?w?\\.?(.+?)/(.+?)([\"\\s<>\\(\\)])");
	private static final Pattern kcLinkPat = Pattern.compile("<a href=\".+\">>>(\\d+)</a>");
	private static final Pattern uriPat = Pattern.compile("href=\"(.+?)\"");
	private static final Pattern spoilerPat = Pattern.compile("<span class=\"spoiler\">(.+?)</span>", Pattern.DOTALL);
	private static final Pattern quotePat = Pattern.compile("<span class=\"quote\">(.+?)</span>", Pattern.DOTALL);
	private Long 	kcNummer;
	private Long 	threadDbId;
	private Long	created;
	private String 	title;
	private String 	creationDate;
	private String 	creationShortDate;
	private String 	content;
	private String	originalContent;
	private String 	user;
	private String 	tripCode;
	private boolean sage;
	private Map<String, String> files = new TreeMap<String, String>();
	
	public static enum Fields  {
		KC_NUM,
		TITLE,
		USER,
		DATE,
		URI,
		IMAGES,
		CONTENT,
		SAGE
	}
	
	public static String sanitizeContent (String inContent) {
		String locContent = inContent;
		locContent = StringEscapeUtils.unescapeHtml4(locContent);
		locContent = locContent.replaceAll("<p>", "");
		locContent = locContent.replaceAll("</p>", " ");
		
		locContent = locContent.replaceAll("onclick=\"highlightPost\\(\\'\\d+\\'\\);\"", "");		
		locContent = locContent.replaceAll(">>>(\\d+)</a>", " onclick='quoteClick(this); return false;' class=\"kclink\">&gt;&gt; $1</a>");

		locContent = locContent.replaceAll("<a href=\"/resolve(/.+?)\"\\s*>.+?</a>", "<a href=\"/resolve$1\" class=\"kclink\" onclick=\"Android.openKcLink('$1');return false;\">&gt;&gt; $1</a>");
		Matcher m = linkPat.matcher(locContent);
		StringBuffer buf = new StringBuffer(locContent.length()+1000);
		int end = 0;
		while (m.find()) {
			int gc = m.groupCount();
			if (gc > 0) {
				buf.append(locContent.substring(end, m.start()));
				end = m.end();
				String host = m.group(1);
				String name = host;
				String styleClass="extlink";
				String androidFunction = "openExternalLink";
				String url = m.group(1)+"/"+m.group(2);
				if ((host.contains("youtube")) || (host.contains("youtu.be"))) {
					styleClass="ytlink";
					name = "YouTube";
					androidFunction = "openYouTubeVideo";
				} else if (host.contains("krautchan.net")){
					styleClass="kclink";
					name = ">>";
					host = "";
					androidFunction = "openKcLink";
				}
				buf.append("<a href=\"http://"+m.group(1)+"/"+m.group(2)+"\" class=\""+styleClass+"\" onclick=\"Android."+androidFunction+"('"+url+"');return false;\">"+name+"</a>"+m.group(3));
			}
		}
		buf.append(locContent.substring(end, locContent.length()));
		return "<p><span>"+buf.toString().trim()+"</span></p>";
	}
	
	public String asHtml  (boolean showImages) {
		String innerHtml = "<div class=\"posthead\">" +
		"<p class=\"headline\"><b>"+kcNummer+"</b><time class='timeago' datetime='"+creationDate+"'>"+creationDate+"</time></p>";
		if (null != title) {
			innerHtml += "<p class=\"topic\">"+title+"</p>";
		}
		innerHtml += "</div>";
		innerHtml += content;
		if (showImages ) {
			String[] uids = getFileUids().toArray(new String[this.getFileUids().size()]);
			if (uids.length > 0) {
				innerHtml += "<div class=\"image-container\">";
				for (int i = 0; i < uids.length; i++) {
					if (uids[i] != null) {
						innerHtml += "<a href=\"/files/"+getFile((uids[i]))+"\" onclick=\"Android.openImage('"+getFile((uids[i]))+"');return false;\"><img src=\"/thumbnails/"+getFile((uids[i]))+"\"></a>";	
					}
				}
				innerHtml += "</div>";
			}
			
			/*innerHtml += "<div class=\"image-container\">";
			for (int i = 0; i < imgs.length; i++) {
				if (null != imgs[i]) {
					innerHtml += "<a href=\"/files/"+imgs[i]+"\" onclick=\"Android.openImage('"+imgs[i]+"');return false;\"><img src=\"/thumbnails/"+thumbs[i]+"\"></a>";
				}
			}
			innerHtml += "</div>";
			*/
		}
		return "<div id='"+kcNummer+"'>"+innerHtml+"</div>";
	}

	
	public String getKcStyledContent () {
		String kcStyledContent = originalContent.replaceAll("<br>", "\n>");
		Matcher kcMatcher = kcLinkPat.matcher(kcStyledContent);
		while (kcMatcher.find()) {
			kcStyledContent = kcMatcher.replaceAll(">>"+kcMatcher.group(1));
		}
		Matcher spoilerMatcher = spoilerPat.matcher(kcStyledContent);
		while (spoilerMatcher.find()) {
			kcStyledContent = ">>"+spoilerMatcher.replaceAll("[spoiler]"+spoilerMatcher.group(1)+"[/spoiler]");
		}
		Matcher quoteMatcher = quotePat.matcher(kcStyledContent);
		while (quoteMatcher.find()) {
			kcStyledContent = quoteMatcher.replaceFirst(quoteMatcher.group(1));
			quoteMatcher = quotePat.matcher(kcStyledContent);
		}
		kcStyledContent = ">"+kcStyledContent;
		return kcStyledContent.trim(); 
	}
	
	public Long getKcNummer() {
		return kcNummer;
	}

	public Long getCreated() {
		return created;
	}

	public String getTitle() {
		return title;
	}

	public String getCreationDate() {
		return creationDate;
	}

	public String getCreationShortDate() {
		return creationShortDate;
	}

	public String getContent() {
		return content;
	}

	public String getOriginalContent() {
		return originalContent;
	}

	public String getUser() {
		return user;
	}

	public String getTripCode() {
		return tripCode;
	}

	public boolean isSage() {
		return sage;
	}

	public Long getThreadDbId() {
		return threadDbId;
	}
	
	public Set<String> getFileUids () {
		return files.keySet();
	}
	
	public String getFile(String uId) {
		return files.get(uId);
	}

	public void addFile(String uId, String name) {
		files.put(uId, name);
	}

	public void setThreadDbId(Long threadDbId) {
		this.threadDbId = threadDbId;
	}

	public void setKcNummer(Long kcNummer) {
		this.kcNummer = kcNummer;
	}

	public void setTitle(String title) {
		this.title = title.trim();
	}
	
	public void setDate (Date cDate) {
		created = cDate.getTime();
		creationShortDate = dfShort.format(cDate);
		creationDate = dfOut.format(cDate);
	}
	
	public void setDate (String arg) {
		try {
			int pos = arg.lastIndexOf('.');
			Date cDate = df.parse(arg.substring(0, pos));
			setDate (cDate);
		} catch (Exception ex) {
			
		}
	}

	public void setContent(String content) {
		String locContent = content.replaceFirst(kcNummer+"\">\\s*", "");
		locContent = StringEscapeUtils.unescapeHtml4 (locContent);
		locContent = locContent.replaceAll("<p>", "");
		locContent = locContent.replaceAll("</p>", " ");
		this.content = sanitizeContent(locContent);
	}

	public void setOriginalContent(String originalContent) {
		this.originalContent = originalContent;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public void setTripCode(String tripCode) {
		this.tripCode = tripCode;
	}

	public void setSage(boolean sage) {
		this.sage = sage;
	}

	public void setField(Fields fields, String arg) throws ParseException {
		switch (fields) {
			case KC_NUM: { 
				if (null != arg) {
					kcNummer = Long.parseLong(arg);
				}
				break;
			}
			case TITLE: { 
				setTitle (StringEscapeUtils.unescapeHtml4(arg));
				break;
			}
			case USER: { 
				user = arg;
				break;
			}
			case DATE: { 
				setDate (arg);
				break;
			}
			case URI: { 
				Matcher m = uriPat.matcher(arg);
				if (m.find()) {
					this.setUri(m.group(1));
				}
				break;
			}
			case IMAGES: {
				Matcher m = imgPat.matcher(arg);
				int i = 0;
				while (m.find()) {
					files.put(m.group(1), m.group(2));
					i++;
				}
				break;
			}
			case CONTENT: { 
				content = arg.replaceFirst(kcNummer+"\">\\s*", "").trim();
				if (content.endsWith("</p>")) {
					content = content.substring(0, content.length() - 4);
				}
				content = content.replaceAll("</p>\\s+<p", "</p><p");
				originalContent = content.replaceAll("<br>", "<br />");
				content = sanitizeContent(content);
				break;
			}
			case SAGE: { 
				sage = true;
				break;
			}
			default: 
				throw new IllegalStateException ("Illegal State in KCPosting:setField");
		}
	}

	@Override
	public int compareTo(KCPosting arg0) {
		return kcNummer.compareTo(arg0.kcNummer);
	}
	
	@Override
	public boolean equals (Object arg0) {
		if (!(arg0 instanceof KCPosting)) {
			return false;
		}
		/*boolean result = true;
		String aContent = StringEscapeUtils.unescapeHtml4(originalContent).replaceAll("  +", " ").replaceAll("<img (.+?)\">", "<img $1\" />");
		String bContent = StringEscapeUtils.unescapeHtml4(((KCPosting)arg0).getOriginalContent()).replaceAll("  +", " ").replaceAll("<img (.+?)\">", "<img $1\" />");
		result = result & aContent.equals(bContent); 
		if (title != null) {
			result = result & title.equals(((KCPosting)arg0).getTitle()); 
		}
		if (user != null) {
			result = result & user.equals(((KCPosting)arg0).getUser()); 
		}
		if (tripCode != null) {
			result = result & tripCode.equals(((KCPosting)arg0).getTripCode()); 
		}
		result = result & (!(sage ^ ((KCPosting)arg0).isSage())); 
		result = result & (created.equals(((KCPosting)arg0).getCreated()));
		result = result & (kcNummer.equals(((KCPosting)arg0).getKcNummer()));
		return result;*/
		return toString().equals(((KCPosting)arg0).toString());
	}
	
	@Override	
	public int hashCode () {
		return toString().hashCode();
	}
	
	@Override	
	public String toString () {
		String retVal = "Kc-Num: " + getKcNummer()+"\n";
		retVal += "Hash: "	 	+ getThreadDbId()+"\n";
		retVal += "Created: " 	+ getCreationDate()+"\n";
		retVal += "Author: " 	+ getUser()+"\n";
		retVal += "T-Code: " 	+ getTripCode()+"\n";
		retVal += "Title: " 	+ getTitle().replaceAll("\\s+", " ")+"\n";
		retVal += "Sage: " 		+ isSage()+"\n";
		retVal += "Content: " 	+ StringEscapeUtils.unescapeHtml4(getOriginalContent())
			.replaceAll("\\s+", " ")
			.replaceAll("\\s</", "</")
			.replaceAll("<img (.+?)\">", "<img $1\" />")
			.replaceAll("<a (.+?)\" >", "<a $1\">")+"\n";
		retVal += "Imgs: \n";
		String[] uids = getFileUids().toArray(new String[this.getFileUids().size()]);
		for (int i = 0; i < uids.length; i++) {
			if (uids[i] != null) {
				retVal += "   " 	+ getFile((uids[i]))+"\n";
			} else {
				retVal += "   -\n";
			}
		}
		return retVal;
	}
}
