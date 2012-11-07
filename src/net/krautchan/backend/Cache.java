package net.krautchan.backend;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.krautchan.data.KCBoard;
import net.krautchan.data.KCPosting;
import net.krautchan.data.KCThread;
import net.krautchan.data.KrautObject;
import net.krautchan.parser.KCParser;

public class Cache {
	private Logger logger = Logger.getAnonymousLogger();
	private ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(5);
	private KCParser parser = null;
	private final Map<String, String> config;
	private Long siteId;
	private Long curThread = 0L;
	private Long curBoard  = -1L;
	private Set<KrautObject> boards = null;
	private Map<Long, Collection<Long>> threadsForBoards = null;
	private Map<Long, KCThread> threads = new HashMap<Long, KCThread>();
	private Map<Long, Long> threadIdMap = new HashMap<Long, Long>();
	private Map<Long, Collection<KCPosting>> posts = new HashMap<Long, Collection<KCPosting>>();
	
	public Cache (final Long siteId, Map<String, String> config, Logger logger) {
		this (siteId, config);
		this.logger = logger;
	}
	
	public Cache (final Long siteId, Map<String, String> config) {
		this.siteId = siteId;
		this.config = config;
		parser = new KCParser(logger, false);
		loadBoards(siteId);
		/*Runnable reloadBoards = new Runnable () {
			@Override
			public void run() {
				//FIXME: board db-ids need to be stable for one session. 
				//loadBoards(siteId);
				
			}
		};
		timer.scheduleWithFixedDelay(reloadBoards, 10, 60 * 60 * 1000, TimeUnit.MILLISECONDS); //reload board list every hour
		*/
		/*Runnable reloadThreads = new Runnable () {
			@Override
			public void run() {
				Set<KrautObject> boards = Cache.this.boards;
				curBoard++;
				if (curBoard > boards.size())
					curBoard = 0L;
				KCBoard board = getCurrentBoard();
				retrieveThreads (board) ;
			}
		};
		timer.scheduleWithFixedDelay(reloadThreads, 120, 1000, TimeUnit.MILLISECONDS);  //reload board every minute
		*/
	}
	
	public Collection<KrautObject> getBoards() {
		loadBoards(siteId);
		return boards;
	}
	
	public Collection<KrautObject> getThreads (Long boardId) {			
		logger.log(Level.WARNING, "Getting Threads for Board: "+boardId);
		KCBoard board = getBoard (boardId);
		if (null == threadsForBoards) {
			threadsForBoards = new HashMap<Long, Collection<Long>> ();
		}
		Collection<Long> threadsIds = threadsForBoards.get(boardId);
		Collection<KrautObject> threads = new HashSet<KrautObject>();
		if (null == threadsIds) {
			threads = retrieveThreads (board); 
		} else {
			for (Long threadId: threadsIds) {
				KCThread tp = Cache.this.threads.get(threadId);
				if (null == tp) {
					Collection<KCPosting> posts = parser.parseThread(board.getUri(), threadIdMap.get(threadId), threadId);
					tp = new KCThread();
					//tp.postings
				}
				threads.add(tp);
			}
			logger.log(Level.WARNING, "Number of Threads for Board: "+boardId+": "+threadsForBoards.get(boardId).size());
		}
		return threads;
	}
	
	private void loadBoards(Long siteId) {
		if (boards == null) {
			boards = new LinkedHashSet<KrautObject>();
		}
		Set<KrautObject> locBoards = loadBoards0(siteId);
		for (KrautObject lBoard: locBoards) {
			boolean found = false;
			for (KrautObject board: boards) {
				if (((KCBoard)board).shortName.equals(((KCBoard)lBoard).shortName)) {
					found = true;
					break;
				}
			}
			if (!found) {
				boards.add(lBoard);
			}
		}
	}
	
	private static Set<KrautObject> loadBoards0(Long siteId) {
		try {
			Set<KrautObject> locBoards = new LinkedHashSet<KrautObject>();
			Map<String, String> parsedBoards = KCParser.getBoardList();
			for (String key: parsedBoards.keySet()) {
				KCBoard board= null;
				board= new KCBoard();
				board.type = KrautObject.DataEventType.ADD;
				board.setDbId((long)(Math.random() * Long.MAX_VALUE));
				board.name = parsedBoards.get(key);
				board.shortName = key;
				board.setUri("/"+key+"/");
				board.numPages = 1;
				locBoards.add(board);
			}
			return locBoards;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	public Collection<KCPosting> getPostings(long threadDbId) {
		KCThread thread = threads.get(threadDbId);
		KCBoard board = getBoard(thread.board_id);
		if (null == board) {
			logger.log(Level.ALL, "got board id null for thread "+thread.kcNummer);
			return null;
		}
		Collection<KCPosting> postings = posts.get(threadDbId);
		if (null == postings) {
			postings = new ArrayList<KCPosting>();
			Collection<KCPosting>	tp = parser.parseThread(Cache.this.config.get("rsspath")+Cache.this.config.get("threadrss")+board.shortName, thread.kcNummer, threadDbId);
			for (KrautObject trans : tp) {
				if (trans instanceof KCPosting) {
					postings.add((KCPosting)trans);
				}	
			}
			posts.put(threadDbId, postings);
		}
		return postings;
	}
	
	public synchronized KCBoard getCurrentBoard () {
		KCBoard board = null;
		Iterator<KrautObject> bIter = boards.iterator();
		int loop = 0;
		while (bIter.hasNext()) {
			board = (KCBoard)bIter.next();
			if (loop++ == curBoard) {
				return board;
			}
		}
		return null;
	}
	
	public synchronized KCBoard getBoard (Long boardId) {
		KCBoard board = null;
		Iterator<KrautObject> bIter = boards.iterator();
		while (bIter.hasNext()) {
			board = (KCBoard)bIter.next();
			if (board.getDbId().longValue() == boardId.longValue()) {
				return board;
			}
		}
		return null;
	}
	
	//TODO check and fix synchronization logic
	public synchronized Collection<KrautObject> retrieveThreads (KCBoard board) {
		Collection<KrautObject> threads = parser.parseBoard(Cache.this.config.get("rsspath")+Cache.this.config.get("boardrss"), board.getUri());
		Collection<Long> oldThreadIds = threadsForBoards.get(board.getDbId());
		if (null != oldThreadIds)
			for (Long id : oldThreadIds) {
				threads.remove(id);
				threadIdMap.remove(id);
			}
		Collection<Long> ids = new HashSet<Long>();
		for (KrautObject thread: threads) {
			if (thread instanceof KCThread) {
				((KCThread)thread).board_id = board.getDbId();
				Cache.this.threads.put(thread.getDbId(), (KCThread)thread);
				ids.add(thread.getDbId());
				threadIdMap.put(thread.getDbId(), ((KCThread) thread).kcNummer);
			}
		}
		threadsForBoards.put(board.getDbId(), ids);
		return threads;
	}

}
