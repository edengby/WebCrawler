package WebCrawler;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import javax.net.ssl.HttpsURLConnection;

import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Fetcher implements Runnable{

    static Logger logger = LogManager.getLogger(Fetcher.class);

    final String LINK_REGEX = "<a href=\"https:\\/\\/([^\"]+)\">";
    final String TITLE_REGEX = ".*?<title>(.*?)</title>.*?";
    final String LINK_PREFIX = "https://";
    final String USER_AGENT = "User-Agent";
    final String USER_AGENT_VALUE = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/76.0.3809.87 Safari/537.36";

    final int CONNECTION_TIMEOUT = 5000;
    final int READ_TIMEOUT = 5000;

    private CrawlerManager _crawlerManager;
    private String _URLToFetch;
    private List<String> _extractedURL;

    public Fetcher(CrawlerManager crawlerManager, String URLToRequest) {
        _crawlerManager = crawlerManager;
        _URLToFetch = URLToRequest;
        _extractedURL = new ArrayList<>();

        logger.debug("Created new Fetcher for: {}", _URLToFetch);
    }

    @Override
    public void run() {
        try
        {
            logger.debug("Running Fetcher for {}", _URLToFetch);

            URLConnection connection = getConnection();

            if(connection != null) {
                Scanner scanner = getScanner(connection);

                if(scanner != null) {
                    String HTMLContent = getHTMLContent(scanner);

                    if(StringUtils.isNotEmpty(HTMLContent)) {
                        extractTitle(HTMLContent);
                        extractURL(HTMLContent);
                        addURLsToQueue();
                    }
                }
            }
        }

        catch(Exception e)
        {
            logger.error("Thread {} interrupted {}", Thread.currentThread().getName(), e);
        }

    }

    private void addURLsToQueue() {
        int extractedSuccessfully = 0;

        if(_crawlerManager.reachedPageLimit()) {
            logger.debug("reached page limit, killing thread {}", Thread.currentThread().getName());
            _crawlerManager.shutdown();
            return;
        }
        else
        {
            _crawlerManager.decreasePageLimit();

            for (String url : _extractedURL) {
                if (!_crawlerManager.containsKey(url)) {
                    logger.debug("added new url to queue {}", url);
                    extractedSuccessfully++;
                    _crawlerManager.addURL(url);
                }
            }

            logger.debug("Thread {} finished extracted {} URLs from {}",
                    Thread.currentThread().getName(), extractedSuccessfully,
                    _URLToFetch);

        }

    }


    private void extractURL(String htmlContent) {
        Pattern pattern = Pattern.compile(LINK_REGEX);
        Matcher matcher = pattern.matcher(htmlContent);
        try {
            while (matcher.find()) {
                for(int i = 0; i<=matcher.groupCount() ; i++) {
                    String link = matcher.group(1);
                    _extractedURL.add(LINK_PREFIX+link);
                }
            }
        } catch (Exception e) {
            logger.error("failed to extract url {}", e);
        }

    }

    private void extractTitle(String htmlContent) {
        Pattern pattern = Pattern.compile(TITLE_REGEX);
        Matcher matcher = pattern.matcher(htmlContent);
        while (matcher.find()) {
            if(matcher.groupCount() > 0) {
                String title = matcher.group(1);
                System.out.println("Page title: "+title);
                return;
            }
        }

    }

    private String getHTMLContent(Scanner scanner) {
        String HTMLContent;
        scanner.useDelimiter("\\Z");
        HTMLContent = scanner.next();
        scanner.close();
        return HTMLContent;
    }

    private Scanner getScanner(URLConnection connection) {
        Scanner scanner = null;

        try {
            if(connection != null) {
                InputStream inputStream = connection.getInputStream();

                if (inputStream != null) {
                    scanner = new Scanner(inputStream);
                }
            }
        } catch (IOException e) {
            logger.error("Unable to create scanner for connection to: {} {}", _URLToFetch, e);
        }
        return scanner;

    }

    private URLConnection getConnection() {
        URLConnection connection = null;

        try {
            connection =  new URL(_URLToFetch).openConnection();
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty(USER_AGENT,USER_AGENT_VALUE);

            return checkConnectionErrors(connection);

        }catch ( Exception e ) {
            logger.error("Unable to create connection to URL: {} {}", _URLToFetch, e);
        }
        return connection;

    }

    private URLConnection checkConnectionErrors(URLConnection connection) throws IOException {
        int responseCode = ((HttpsURLConnection)connection).getResponseCode();
        switch (responseCode) {
            case HttpURLConnection.HTTP_OK:
                return connection;
            default:
                logger.debug("connection to {} failed due to {}", _URLToFetch, responseCode);
                return null;
        }

    }


}
