package com.ucu.inginf.twitter;


import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.social.twitter.api.SearchResults;
import org.springframework.social.twitter.api.Tweet;
import org.springframework.social.twitter.api.Twitter;
import org.springframework.social.twitter.api.impl.TwitterTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Properties;

/**
 * Created by nachogarrone on 13/9/15.
 */
@Configuration
public class Processor {
    private static final Logger log = LoggerFactory.getLogger(Processor.class);
    private static String URL_REGEX = "(http|ftp|https)://([\\w_-]+(?:(?:\\.[\\w_-]+)+))([\\w.," +
            "@?^=%&:/~+#-]*[\\w@?^=%&/~+#-])?";
    private static int TWEETS_QUANTITY = 50;
    SendRequest sendRequest;
    private String DATUMBOX_API_KEY;
    private String DATUMBOX_API_ENDPOINT;
    private String TWITTER_APP_ID;
    private String TWITTER_APP_SECRET;
    private String TWITTER_ACCESS_TOKEN;
    private String TWITTER_ACCESS_TOKEN_SECRET;
    @Autowired
    @Qualifier("myProperties")
    private Properties myProps;
    private String[] candidates = {"Hillary Clinton", "Donald Trump", "elections2016"};

    @Bean
    public Processor getProcessor() {
        sendRequest = new SendRequest();
        initProperties();
        return this;
    }

    @Bean(name = "myProperties")
    public Properties getMyProperties() throws IOException {
        return PropertiesLoaderUtils.loadProperties(new ClassPathResource("/application.properties"));
    }

    private void initProperties() {
        this.TWITTER_APP_ID = myProps.getProperty("spring.social.twitter.appId");
        this.TWITTER_APP_SECRET = myProps.getProperty("spring.social.twitter.appSecret");
        this.TWITTER_ACCESS_TOKEN = myProps.getProperty("spring.social.twitter.access.token");
        this.TWITTER_ACCESS_TOKEN_SECRET = myProps.getProperty("spring.social.twitter.access.token.secret");
        this.DATUMBOX_API_KEY = myProps.getProperty("datumbox.api.key");
        this.DATUMBOX_API_ENDPOINT = myProps.getProperty("datumbox.api.endpoint");
    }

    public void analyzeCandidates() throws Exception {
        System.out.println("Starting to analyze...");
        System.out.println("Asking for " + TWEETS_QUANTITY + " number of tweets per query.");
        int neutrals = 0;
        for (String candidate : candidates) {
            System.out.println("Analyzing: " + candidate);
            neutrals += run(candidate);
        }

        System.out.println("API performance: " + 100 * ((float) neutrals / (float) (TWEETS_QUANTITY * candidates
                .length)) + "%");
    }

    private Integer run(String input) throws Exception {
        Twitter twitter = new TwitterTemplate(TWITTER_APP_ID, TWITTER_APP_SECRET, TWITTER_ACCESS_TOKEN, TWITTER_ACCESS_TOKEN_SECRET);

        SearchResults searchResults = twitter.searchOperations().search(input, TWEETS_QUANTITY);
        int result = 0;
        int negative = 0;
        int neutral = 0;
        int positive = 0;
        for (Tweet tweet : searchResults.getTweets()) {
            int temp = 0;
            try {
                if (tweet != null && tweet.getText() != null) {
                    //result += getDatumBoxValuation(sanitizeText(tweet.getText()));
                    temp = getDatumBoxValuation(sanitizeText(tweet.getText()));
                    switch (temp) {
                        case -1:
                            negative++;
                        case 0:
                            neutral++;
                        case 1:
                            positive++;
                    }
                    result += temp;
                    System.out.print(".");
                }
            } catch (Exception e) {
                //System.out.println("error = " + e );
            }
        }
        System.out.println();
        System.out.println(input + " = " + result);
        //System.out.println("Total analyzed for " + input + " = " + searchResults.getTweets().size());
//        System.out.println("Negatives for " + input + " = " + negative);
//        System.out.println("Neutrals for " + input + " = " + neutral);
//        System.out.println("Positives for " + input + " = " + positive);
        return neutral;
    }

    private String sanitizeText(String input) {
        return input.replaceAll(URL_REGEX, "");
    }

    /**
     * @param text
     * @return -1 if negative valuation
     * 0 if neutral valuation
     * 1 if positive valuation
     * @throws IOException
     */
    private int getDatumBoxValuation(String text) throws IOException {
        URL url = new URL(DATUMBOX_API_ENDPOINT);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        String parameters = "api_key=" + DATUMBOX_API_KEY + "&text=" + URLEncoder.encode(text, "UTF-8");
        InputStream inputStream = sendRequest.postURL(connection, url, parameters, DATUMBOX_API_ENDPOINT);

        String jsonTxt = IOUtils.toString(inputStream);
        String responseData = parse(jsonTxt).replace("\"", "");
        //System.out.println("responseData = " + responseData);
        int response = 0;
        switch (responseData) {
            case "negative":
                response = -1;
                break;
            case "neutral":
                response = 0;
                break;
            case "positive":
                response = 1;
                break;
        }
        return response;
    }

    private String parse(String jsonLine) {
        JsonElement jelement = new JsonParser().parse(jsonLine);
        JsonObject jobject = jelement.getAsJsonObject();
        jobject = jobject.getAsJsonObject("output");
        String result = jobject.get("result").toString();
        return result;
    }
}
