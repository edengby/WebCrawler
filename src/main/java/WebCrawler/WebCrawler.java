package WebCrawler;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WebCrawler {

    static Logger logger = LogManager.getLogger(WebCrawler.class);

    public static void main(String[] args) {

        logger.info("App Started");

        String baseURL = System.getProperty("baseURL");
        String limit = System.getProperty("limit");
        if(StringUtils.isNotEmpty(baseURL) && StringUtils.isNotEmpty(limit))
        {
            startCrawlerManager(baseURL, limit);
        }
        else
        {
            logger.error("Missing arguments. Check again provided baseURL and limit.");
        }

    }

    private static void startCrawlerManager(String baseURL, String limit) {
        logger.debug("Creating CrawlerManager using: {}, limit: {}", baseURL, limit);
        CrawlerManager crawlerManager = new CrawlerManager(baseURL, limit);
        Thread crawlerManagerThread = new Thread(crawlerManager, "crawlerManager");
        crawlerManagerThread.start();
    }
}
