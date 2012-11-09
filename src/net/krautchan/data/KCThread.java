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
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import junit.framework.Assert;

import org.apache.commons.lang3.StringEscapeUtils;

public class KCThread extends KrautObject {
	private static final long serialVersionUID = -8659957154306651426L;
	private static transient final SimpleDateFormat df = new SimpleDateFormat ("EEE, dd MMM yyyy HH:mm:ss z");
	private static transient final SimpleDateFormat dfShort = new SimpleDateFormat ("dd.MM. HH:mm");
	public Long kcNummer = null;
	public Long board_id = null;
	public Long firstPostDate = null;
	public Long lastPostDate = null;
	public String digest = null;
	public boolean hidden = false;
	public boolean bookmarked = false;
	public Long previousLastKcNum = null;
	public transient int numPostings = 0;
	private Map<Long, KCPosting>  postings = new TreeMap<Long, KCPosting>();
	
	public KCThread () {
	}
	
	public KCThread (String uri) {
		this.setUri(uri);
	}
	
	public void setUri(String uri) {
		super.setUri(uri);
	}
	
	public synchronized KCPosting getPosting (Long id) {
		return postings.get(id);
	}
	
	public synchronized KCPosting getFirstPosting () {
		if (!postings.isEmpty()) {
			return postings.entrySet().iterator().next().getValue();
		}
		return null;
	}
	
	public synchronized KCPosting  getLastPosting() {
		if (!postings.isEmpty()) {
			KCPosting p = null;
			Iterator<Map.Entry<Long, KCPosting>> iter = postings.entrySet().iterator();
			while (iter.hasNext()) {
				p = iter.next().getValue();
			}
			return p;
		}
		return null;
	}
	
	public boolean containsPosting (KCPosting posting) {
		return postings.containsKey(posting.getDbId());
	}
	
	public synchronized void addPosting (KCPosting posting) {
		if (null == kcNummer) {
			kcNummer = posting.getKcNummer();
		}
		if (null == firstPostDate) {
			firstPostDate = posting.getCreated();
			makeDigest (posting);
		}
		if (null == this.getUri()) {
			this.setUri(posting.getUri());
		}
		if (null == this.getDbId()) {
			this.setDbId((long)this.getUri().hashCode());
		}
		if (null == digest) {
			makeDigest (posting);
		}
		lastPostDate = posting.getCreated();
		Long id = posting.getDbId();
		if (!postings.containsKey(id)) {
			posting.setThreadDbId(this.getDbId());
			postings.put(id, posting);
		}
		if ((null != previousLastKcNum) && (previousLastKcNum < posting.getKcNummer())) {
			previousLastKcNum = posting.getKcNummer();
		}
		Assert.assertNotNull(this.getDbId());	
	}
	
	public void recalc () {
		try {
			Iterator<Entry<Long, KCPosting>> iter = postings.entrySet().iterator();
			if (iter.hasNext()) {
				Entry<Long, KCPosting> entry = iter.next();
				KCPosting posting = entry.getValue();
				if (null == digest) {
					makeDigest (posting);
				}
				if (null == firstPostDate) {
					firstPostDate = posting.getCreated();
				}
			}
			Assert.assertNotNull(this.getDbId());
		} catch (Exception e) {
			String trace = "Exception in KCThread "+kcNummer+" "+e.getClass().getCanonicalName()+"\n";
			for (StackTraceElement elem : e.getStackTrace()) {
				trace+= " "+elem.toString()+"\n";
			}
			System.err.println (trace);
		}
	}
	
	public synchronized void clearPostings () {
		postings.clear();	
	}
	
	public synchronized Collection<Long> getIds () {
		return postings.keySet();
	}
	
	public synchronized Set<KCPosting> getSortedPostings () {
		TreeSet<KCPosting> s = new TreeSet<KCPosting>();
		s.addAll(postings.values());
		return s;
	}
	
	private void makeDigest (KCPosting posting) {
		if (null == posting) {
			return;
		}
		digest = posting.getContent();
		int len = digest.length();
		if (len > 250)
			len = 250;
		digest = digest.substring(0, len);
		if (digest.charAt(digest.length()-1) == '&') {
			digest = digest.substring(0, len-1);
		}
		digest = digest.replaceAll("[\n\r\u0085\u2028\u2029]", " ").replaceAll(" +", " ").trim();
		digest = StringEscapeUtils.unescapeHtml4(digest);
		digest = digest.replaceAll("<span class=\"spoiler\">.+?</span>", "");
		digest = digest.replaceAll("\\<.*?\\>", " ");
		digest = digest.replaceAll("https?://.+? ", " ");
		digest = digest.replaceAll(" +", " ");
		len = digest.length();
		if (len > 200)
			len = 200;
		digest = digest.substring(0, len);
		digest = digest.replaceAll("\\<.*", "");
		int pos = digest.length()-1;
		char c = digest.charAt(pos);
		while ((c != ' ') && (pos > 150)) {
			pos--;
			c = digest.charAt(pos);
		}
		digest = digest.trim();
		Iterator<String> iter = posting.getFileUids().iterator();
		while (iter.hasNext()) {
			digest += "\n   "+posting.getFile(iter.next());
			
		}
	}

	@Override
	public boolean equals (Object arg0) {
		if (!(arg0 instanceof KCThread)) {
			return false;
		}
		boolean result = true;
		for (Long key : this.postings.keySet()) {
			KCPosting post1 = postings.get(key);
			KCPosting post2 = ((KCThread)arg0).getPosting(key);
			result = result & post1.equals( post2);
		}
		
		for (Long key : ((KCThread)arg0).getIds()) {
			if (null != postings.get(key)) {
				result = result & postings.get(key).equals( ((KCThread)arg0).getPosting(key));
			}
			else {
				result = false;
			}
		}
		return result;
	}	
	
	@Override	
	public String toString () {
		String retVal = "Thread: "+getUri()+"\n";
		for (Long key : this.postings.keySet()) {
			KCPosting post = postings.get(key);
			retVal += post.toString()+"\n";
		}
		return retVal;
	}
	
	/*@Override	
	public int hashCode () {
		String result = "";
		for (Long key : this.postings.keySet()) {
			result = result + " " + postings.get(key).hashCode();
		}
		return result.hashCode();
	}*/
}
