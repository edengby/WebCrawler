package WebCrawler;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.*;

public class CrawlerManager implements Runnable{

    static Logger logger = LogManager.getLogger(CrawlerManager.class);

    final int NUMBER_OF_FETCHER_THREADS = 10;

    private Map<String, Boolean> _URLVisited = new ConcurrentHashMap<>();
    private CountDownLatch _pageLimit;
    private BlockingQueue<String> _URLQueue ;
    private ExecutorService _executor;


    public CrawlerManager(String baseURL, String pageLimitString) {
        int pageLimit = parseInt(pageLimitString);
        _pageLimit = new CountDownLatch(pageLimit);

        int numberOfThreads = parseInt(System.getProperty("threads"));
        numberOfThreads = numberOfThreads > 0 ? numberOfThreads : NUMBER_OF_FETCHER_THREADS;
        logger.debug("initialize thread pool of {}", numberOfThreads);
        _executor = Executors.newFixedThreadPool(numberOfThreads);

        int queueCapacity = pageLimit*numberOfThreads;
        logger.debug("size of queue capacity is: {}", queueCapacity);
        _URLQueue = new ArrayBlockingQueue<>(queueCapacity, true);

        initialization(baseURL);
    }

    private int parseInt(String limit) {
        try{
            return Integer.parseInt(limit);
        }
        catch(NumberFormatException  e){
            logger.error("Unable to parse page limit {} {}", limit, e);
            return 1;
        }

    }

    private void initialization(String baseURL) {
        _URLVisited.put(baseURL, true);
        _URLQueue.add(baseURL);
    }

    @Override
    public void run() {
        pollNextURL();

        try {
            _pageLimit.await();
        } catch (InterruptedException E) {
            logger.error("initial thread interrupted, may cause fail to countDownLatch");
        }

    }


    public void pollNextURL() {
        while(true) {
            String next = _URLQueue.poll();
            if (StringUtils.isEmpty(next)) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    logger.error("thread interrupted while waiting for poll next url");
                }
            }
            else
            {
                logger.debug("create new thread for {}", next);
                Runnable fetcher = new Fetcher(this, next);
                _executor.execute(fetcher);
            }
        }

    }

    public boolean reachedPageLimit() {
        if(_pageLimit.getCount() == 0){
            logger.debug("page limit is reached, application will be terminate");
            return true;
        }

        return false;
    }

    public synchronized void decreasePageLimit() {
        logger.debug("page limit counted down to {}",_pageLimit.getCount());
        _pageLimit.countDown();
    }

    public void addURL(String url) {
        _URLVisited.put(url, true);
        try {
            if(!reachedPageLimit()) {
                _URLQueue.add(url);
            }
        } catch (Exception e) {
            logger.error("unable to add items to URLQueue {}",e);
        }

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            logger.error("thread interrupted while sleeping");
        }

    }

    public void shutdown() {
        _executor.shutdown();
        try {
            _executor.awaitTermination(60, TimeUnit.SECONDS); // or what ever
        } catch (InterruptedException e) {
            logger.error("error while await termination {}",e);
        }
        _executor.shutdownNow();
    }

    public boolean containsKey(String url) {
        return _URLVisited.containsKey(url);
    }
}
