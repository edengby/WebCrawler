package WebCrawler;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

import java.net.URL;
import java.net.URLConnection;
import java.util.*;


public class Fetcher implements Runnable{

    static Logger logger = LogManager.getLogger(Fetcher.class);

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
                        Document document = Jsoup.parse(HTMLContent);
                        extractTitle(document);
                        extractURL(document);
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


    private void extractURL(Document htmlContent) {
        Elements htmlElement = htmlContent.select("a[href*=http]");
        for(Element element : htmlElement) {
            String attr = element.attr("abs:href");
            if(StringUtils.isNotEmpty(attr)) {
                _extractedURL.add(attr);
            }
        }

    }

    private void extractTitle(Document htmlDocument) {
        Elements select = htmlDocument.select("title");
        if(select.size() > 0) {
            Element element = select.get(0);
            if (element != null) {
                String cleanedTitle = element.text().replace("<title>", "")
                        .replace("</title>", "");
                System.out.println("Page title: " + cleanedTitle);
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
                    scanner = new Scanner(inputStream, "UTF-8");
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
            connection = new URL(_URLToFetch).openConnection();
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty(USER_AGENT,USER_AGENT_VALUE);

            return checkConnectionErrors((HttpURLConnection)connection);

        }catch ( Exception e ) {
            logger.error("Unable to create connection to URL: {} {}", _URLToFetch, e);
        }
        return connection;

    }

    private URLConnection checkConnectionErrors(HttpURLConnection connection) throws IOException {
        int responseCode = connection.getResponseCode();
        switch (responseCode) {
            case HttpURLConnection.HTTP_OK:
                return connection;
            default:
                logger.debug("connection to {} failed due to {}", _URLToFetch, responseCode);
                return null;
        }

    }


}
