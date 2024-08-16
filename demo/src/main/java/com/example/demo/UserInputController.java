package com.example.demo;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.watson.natural_language_understanding.v1.NaturalLanguageUnderstanding;
import com.ibm.watson.natural_language_understanding.v1.model.*;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;

@Controller
public class UserInputController {

    @GetMapping("/")
    public String getInputForm() {

        return "inputForm"; // This maps to inputForm.html

    }

    String yelpUrl;
    List<String> yelpNames;
    List<String> yelpReviews;

    @PostMapping("/display")
    public String displayInput(@RequestParam("userInput") String userInput, Model model) {

        yelpUrl = userInput;
        yelpUrl = yelpUrl.substring(yelpUrl.indexOf("/biz/") + 5);
        // edge case if user input link contains trailing sub queries (if they searched for the restaurant);
        // for example: https://www.yelp.com/biz/shake-shack-seattle-3?osq=food
        // user searched up food before clicking on shake shack
        if (yelpUrl.contains("?")) {
            yelpUrl = yelpUrl.substring(0, yelpUrl.indexOf("?"));
        }

        model.addAttribute("yelpName", yelpUrl.replaceAll("-", " ").toUpperCase());
        model.addAttribute("yelpIntro", "Three recent customer experiences");

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("https://api.yelp.com/v3/businesses/"+yelpUrl+"/reviews?&limit=10&sort_by=yelp_sort")
                .get()
                .addHeader("accept", "application/json")
                .addHeader("Authorization", "Bearer _CIEzrLnJErshuHX697jX3CZ3giZ4qdFd5kjBCff4w1lpsrEy2SQPMZRv6orAr9g3zRr0yt4-v1TE8hGHlYKc1zAwAJV46m48-WXmG8ZvgM5ZyV7hkTqdso5Cl0JZXYx")
                .build();


        yelpNames = new ArrayList<>();
        yelpReviews = new ArrayList<>();

        try {
            Response response = client.newCall(request).execute();

            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseBody);

                // Extract reviews from the JSON response
                JSONArray rev = jsonResponse.getJSONArray("reviews");

                for (int i = 0; i < rev.length(); i++) {
                    JSONObject review = rev.getJSONObject(i);
                    String reviewText = review.getString("text");
                    String reviewName = review.getJSONObject("user").getString("name");
                    String reviewTime = review.getString("time_created");
                    String timeAgo = getTimeAgo(reviewTime);


                    /*
                    System.out.println("Review " + (i + 1));
                    System.out.println(timeAgo);
                    System.out.println(reviewName + " - " + reviewText);
                    System.out.println(); */

                    model.addAttribute("yelpRev" + i, "Review " + (i + 1));
                    model.addAttribute("yelpTime" + i, timeAgo);
                    model.addAttribute("yelpText" + i, reviewName + ".. " + reviewText);

                    yelpNames.add(reviewName);
                    yelpReviews.add(reviewText);
                }

            } else {
                System.err.println("Error: " + response.code() + " - " + response.message());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }



        return "displayInput"; // This maps to displayInput.html
    }

    @GetMapping("/display2")
    public String displayInput2(Model model) {

        // IBM Watson NLU API key and URL
        String apiKey = "GicQZcEpN2JR_Y7ZVf6tpKnKfV9oZQyqBqPTNHda-COB";
        String apiUrl = "https://api.us-south.natural-language-understanding.watson.cloud.ibm.com/instances/63a555a6-a393-4594-9a43-cca773895cf0";

        // Initialize the IBM Watson NLU service
        IamAuthenticator authenticator = new IamAuthenticator(apiKey);
        NaturalLanguageUnderstanding naturalLanguageUnderstanding = new NaturalLanguageUnderstanding("2022-04-07", authenticator);
        naturalLanguageUnderstanding.setServiceUrl(apiUrl);


        // ibm watsons natural language understanding ai
        // to analyze the 3 yelp reviews we just retrieved

        model.addAttribute("yelpName", yelpUrl.replaceAll("-", " ").toUpperCase());
        model.addAttribute("nlpIntro", "Let's use IBM's Natural Language Understanding AI to explore the sentiment, emotions, and key insights for each review");

        double ratingTotal = 0.0;

        for (int i = 0; i < 3; i++) {

            // Create a text to analyze
            String textToAnalyze = yelpReviews.get(i);
            model.addAttribute("nlpReview"+i, yelpNames.get(i)+".. "+ textToAnalyze);

            // Define the features you want to analyze (e.g., sentiment, emotion, language)
            SentimentOptions sentiment = new SentimentOptions.Builder().build();
            EmotionOptions emotion = new EmotionOptions.Builder().build();
            ConceptsOptions concept= new ConceptsOptions.Builder()
                    .limit(3)
                    .build();

            Features features = new Features.Builder()
                    .sentiment(sentiment)
                    .emotion(emotion)
                    .concepts(concept)
                    .build();

            // Create an analyze options object
            AnalyzeOptions parameters  = new AnalyzeOptions.Builder()
                    .text(textToAnalyze)
                    .features(features)
                    .build();

            // Analyze the text
            AnalysisResults response = naturalLanguageUnderstanding
                    .analyze(parameters)
                    .execute()
                    .getResult();

            // Print the analysis results

            System.out.println();
            String aiResult = yelpNames.get(i);

            model.addAttribute("aiResult"+i, aiResult);
            double rating = convertToRating(response.getSentiment().getDocument().getScore());

            // add cumulative rating
            ratingTotal += rating;

            model.addAttribute("aiPercent"+i,
                    rating
                    +"/5 star " + response.getSentiment().getDocument().getLabel());

            StringBuilder emotions = new StringBuilder();

            Map<Double, String> storage = new HashMap<>();
            storage.put(convertToPercentage(response.getEmotion().getDocument().getEmotion().getAnger()), "anger");
            storage.put(convertToPercentage(response.getEmotion().getDocument().getEmotion().getDisgust()), "disgust");
            storage.put(convertToPercentage(response.getEmotion().getDocument().getEmotion().getFear()), "fear");
            storage.put(convertToPercentage(response.getEmotion().getDocument().getEmotion().getJoy()), "joy");
            storage.put(convertToPercentage(response.getEmotion().getDocument().getEmotion().getSadness()), "sadness");

            List<Double> emotionRank = new ArrayList<>(storage.keySet());

            Collections.sort(emotionRank, Collections.reverseOrder());

            for (Double d : emotionRank) {
                emotions.append(storage.get(d)).append(": ").append(d).append("% <br>");
            }

            model.addAttribute("aiEmotion"+i, emotions.toString());

            List<ConceptsResult> concepts = response.getConcepts();

            String conc = "none";

            for (ConceptsResult s : concepts) {
                if (s.getRelevance() > 0.5) {
                    conc = ("");
                    break;
                }
            }

            for (ConceptsResult s : concepts) {
                if (s.getRelevance() > 0.5) {
                    conc += s.getText() +" "+convertToPercentage(s.getRelevance())+"% relevance <br>";
                }
            }

            model.addAttribute("aiConcept"+i, conc);

        }

        ratingTotal /= 3;
        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        ratingTotal = Double.parseDouble(decimalFormat.format(ratingTotal));
        model.addAttribute("overallRating", ratingTotal);

        return "displayInput2"; // This maps to displayInput2.html
    }


    public static String getTimeAgo(String timestampStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date timestamp = sdf.parse(timestampStr);
            long currentTimeMillis = System.currentTimeMillis();
            long timestampMillis = timestamp.getTime();
            long timeDifferenceMillis = currentTimeMillis - timestampMillis;

            // Calculate time units
            long seconds = timeDifferenceMillis / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;

            if (days > 0) {
                return days + (days == 1 ? " day ago" : " days ago");
            } else if (hours > 0) {
                return hours + (hours == 1 ? " hour ago" : " hours ago");
            } else if (minutes > 0) {
                return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
            } else {
                return seconds + (seconds == 1 ? " second ago" : " seconds ago");
            }
        } catch (ParseException e) {
            e.printStackTrace();
            return "Invalid timestamp";
        }
    }

    // converts the decimals into %
    public static double convertToPercentage(double decimal) {
        // Multiply the decimal by 100 to convert to a percentage
        double percentageValue = decimal * 100;

        // Round down to two decimal places
        double roundedValue = Math.floor(percentageValue * 100) / 100;

        return roundedValue;
    }

    // converts {-1, 1} inclusive to {0, 5} rounded down to 2 decimal places
    public static double convertToRating(double score) {
        // Check if the input score is outside the valid range
        if (score < -1.0 || score > 1.0) {
            throw new IllegalArgumentException("Input score must be between -1 and 1 (inclusive).");
        }

        // Map the input score to the output rating scale
        double minInput = -1.0;
        double maxInput = 1.0;
        double minOutput = 0.0;
        double maxOutput = 5.0;

        // Calculate the converted rating
        double convertedRating = ((score - minInput) / (maxInput - minInput)) * (maxOutput - minOutput) + minOutput;

        // Round down to two decimal places
        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        convertedRating = Double.parseDouble(decimalFormat.format(convertedRating));

        return convertedRating;
    }



}