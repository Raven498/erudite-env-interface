package com.erudite.env_interface;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Service for interacting with the Gemini API to prompt Gemini for generated true knowledge and parse/return them.
 * Populates record classes with parsed information from Gemini response and returns them - record classes are by
 * knowledge type (concepts, algos, etc.) and are true (or partial) knowledge representations.
 * (Because Gemini serves as the "environment," all true knowledge is always provided by Gemini on an on-demand basis to TEN.)
 *      (TODO: Describe explicitly the environmental role of Gemini in Controller/another general class).
 *
 * Currently only returns concept instances. (The terminology used throughout this class's code currently references "concepts"
 * when they're actually only building "instances" - need to change this soon).
 *
 */
@Service
public class TKBService {
    /**
     * Prompt for Gemini to generate a random instance in a standardized format with criteria for behaviors/attributes.
     * Any required changes to response format, instance structure or fields go here.
     */
    private final String GEMINI_TKB_INSTANCE_PROMPT = """
            Generate a completely random proper noun that exists, and define it using an object-oriented structure with the 
            following standard format:
            
            {Object Name}
            
            - Attributes:
                - {AttributeName}: {AttributeValue}
                - ...
            
            - Behaviors:
                - {BehaviorName}
                - ...
            
            Attributes can only be either integers, decimals, or string values. 
            Behaviors must only include actions that the object itself actively performs, not actions that the object experiences from other objects. 
            They must also be very specific actions that complete specific tasks, not general behaviors that can create different outcomes.
            Follow the above-specified format strictly when defining your chosen object. Do not add any additional information
            or descriptions for any attributes, behaviors or objects.
            """;

    private final String GEMINI_TKB_CONCEPT_PROMPT = """
            Generate a completely random noun that exists, and define it using an object-oriented structure corresponding to a class structure with the 
            following standard format:
            
            {Concept Name}
            
            - Attributes:
                - {AttributeName}
                - ...
            
            - Behaviors:
                - {BehaviorName}
                - ...
            Attributes should not have any values associated with them. 
            Behaviors must only include actions that objects of this concept itself actively perform, not actions that its objects experience from other objects. 
            They must also be very specific actions that complete specific tasks, not general behaviors that can create different outcomes.
            Follow the above-specified format strictly when defining your chosen concept. Do not add any additional information
            or descriptions for any attributes, behaviors or concepts.
            """;

    private final String GEMINI_TKB_CONCEPT_PROMPT_V2_SINGLE = """
            First, find a completely random topic that exists in real life. Then, generate a single, random idea (representing an abstract or tangible object, place, or person) 
            that exists within this topic, and define it using an object-oriented structure corresponding to a class structure with the following standard format:
            
            {Concept Name}
            
            - Attributes:
                - {AttributeName}
                - ...
            
            - Behaviors:
                - {BehaviorName}
                - ...
            
            Attributes should not have any values associated with them. 
            Behaviors must only include actions that objects of this concept itself actively perform, not actions that its objects experience from other objects. 
            They must also be very specific actions that complete specific tasks, not general behaviors that can create different outcomes.
            Follow the above-specified format strictly when defining your chosen concept. Do not add any additional information
            or descriptions for any attributes, behaviors or concepts.
        """;

    //TODO:
    /*
    1. Evaluate the reliability of this prompt and develop a metric for it
    2. Branch out this change (and anything else related to it)
     */
    private final String GEMINI_TKB_ALGORITHM_PROMPT = """
            For a completely random task, define an algorithm 
             First, find a completely random topic that exists in real life. Then,
            
            generate a single, random idea (representing an abstract or tangible
            
            object, place, or person) that exists within this topic, and define it using an object-oriented
            
            structure corresponding to a class structure with the following standard format:
            
            
            {Concept Name}
            
            
            - Attributes:
            
            - {AttributeName}
            
            - ...
            
            
            - Behaviors:
            
            - {BehaviorName}
            
            - ...
            
            
            
            Attributes should not have any values associated with them. Keep the
            
            concept itself as general as possible, and include any specific
            
            potential characteristics of it as attributes.
            
            
            
            Behaviors must only include actions that objects of this concept
            
            itself actively perform, not actions that its objects experience from
            
            other objects. They must also be very specific actions that complete
            
            specific tasks, not general behaviors that can create different
            
            outcomes.
            
            
            
            Follow the above-specified format strictly when defining your chosen
            
            concept. Do not add any additional information or descriptions for any
            
            attributes, behaviors or concepts.\s
            """;

    /**
     * Characters to remove from parsed fields.
     */
    private final String[] INVALID_CHARS = {"*", "-", " ", "\n", "\""};

    /*
        TODO:
        1. Improve parsing algorithm to remove all unnecessary characters, whitespace, etc. from all fields
        2. Measure & optimize time delays for Gemini API connections, use @Retryable & @Recover for conn retry logic
        3. Improvements to GEMINI_TKB_CONCEPT_PROMPT for more standardized answer formatting
        4. Figure out way to migrate Erudite app into this architecture & add to VCS
        5. Define question/problem to answer in Theory Journal (something to do with chain-of-thought, sim analysis & needed non-RL algos)
        6. Please, please please PLEASE DOCUMENT ALL CODE!!!!!
        7. Create code for generating "actual concepts" on the class level along with instances
            - Could try reverse engineering classes from a generated instance
        8. General organizational stuff - both theoretical and practical
            - Theory: Consolidating & proofreading theory docs & theory journal
            - Practical: Architecture and code documentation (first, transfer notes written on Mac)
    */

    /**
     * Calls the Gemini API to generate a random instance using the respective prompt,
     * parses the response, and constructs an Instance record representing true/partial Instance.
     *
     * @return Instance object parsed from Gemini API response
     * @throws IOException if there is a problem with the API call or response parsing
     */
    public Instance getInstance() throws IOException {
        // Create HTTP client with timeout
        OkHttpClient client = new OkHttpClient.Builder().writeTimeout(20, TimeUnit.SECONDS).build();

        // Prepare request body with prompt
        String json = String.format("{\"contents\": [{\"parts\": [{\"text\": \"%s\"}]}]}", GEMINI_TKB_INSTANCE_PROMPT);
        RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));

        // Build request to Gemini API
        Request request = new Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent")
                .addHeader("x-goog-api-key", "") // TODO: Create secrets for API key
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        // Execute request and get response as JSON string
        ResponseBody response = client.newCall(request).execute().body();
        String responseJson = response.string();

        // Convert JSON response string into JsonNodes, filter for Gemini response
        ObjectMapper mapper = new ObjectMapper();
        JsonNode responseNode = mapper.readTree(responseJson);
        System.out.println("ANSWER: " + responseJson);
        String answer = responseNode.get("candidates").deepCopy().get(0).get("content").get("parts").deepCopy().get(0).get("text").asText();
        System.out.println(answer);

        // Split response into lines and clean up empty strings
        List<String> parts = new ArrayList<>(Arrays.asList(answer.split("\n")));
        parts.removeIf(String::isEmpty);

        // Extract object name
        String objectName = parts.get(0);

        // Initialize record fields for parsing
        Map<String, String> attrs = new HashMap<>();
        ArrayList<String> behaviorNames = new ArrayList<>();
        boolean parseBehaviors = false;
        System.out.println("PARTS: " + parts);

        // Parse attributes and behaviors from response
        for(int i = 3; i < parts.size(); i++){
            // Sanitize fields for unwanted chars
            for(String c : INVALID_CHARS){
                parts.set(i, parts.get(i).replace(c, ""));
            }
            if(parts.get(i).equals("Behaviors:")){
                parseBehaviors = true;
                continue;
            }
            if(parseBehaviors){
                behaviorNames.add(parts.get(i));
                continue;
            }
            String[] map = parts.get(i).split(":");
            System.out.println("MAP: " + Arrays.toString(map));
            attrs.put(map[0], map[1]);
        }

        // Construct and return record
        return new Instance(objectName, "", attrs, behaviorNames);
    }

    public Concept getConcept() throws IOException{
        // Create HTTP client with timeout
        OkHttpClient client = new OkHttpClient.Builder().writeTimeout(20, TimeUnit.SECONDS).build();

        // Prepare request body with prompt
        String json = String.format("{\"contents\": [{\"parts\": [{\"text\": \"%s\"}]}]}", GEMINI_TKB_CONCEPT_PROMPT);
        RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));

        // Build request to Gemini API
        Request request = new Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent")
                .addHeader("x-goog-api-key", "") // TODO: Create secrets for API key
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        // Execute request and get response as JSON string
        ResponseBody response = client.newCall(request).execute().body();
        String responseJson = response.string();

        // Convert JSON response string into JsonNodes, filter for Gemini response
        ObjectMapper mapper = new ObjectMapper();
        JsonNode responseNode = mapper.readTree(responseJson);
        String answer = responseNode.get("candidates").deepCopy().get(0).get("content").get("parts").deepCopy().get(0).get("text").asText();
        System.out.println(answer);

        if(responseNode.get("code") != null && Objects.equals(responseNode.get("code").asText(), "429")){ // handling Gemini rate limits
            throw new ResponseStatusException(HttpStatus.I_AM_A_TEAPOT);
        }

        // Split response into lines and clean up empty strings
        List<String> parts = new ArrayList<>(Arrays.asList(answer.split("\n")));
        parts.removeIf(String::isEmpty);

        // Extract object name
        String conceptName = parts.get(0);

        // Initialize record fields for parsing
        ArrayList<String> attrs = new ArrayList<>();
        ArrayList<String> behaviorNames = new ArrayList<>();
        boolean parseBehaviors = false;
        System.out.println("PARTS: " + parts);

        // Parse attributes and behaviors from response
        for(int i = 3; i < parts.size(); i++){
            // Sanitize fields for unwanted chars
            for(String c : INVALID_CHARS){
                parts.set(i, parts.get(i).replace(c, ""));
            }
            if(parts.get(i).equals("Behaviors:")){
                parseBehaviors = true;
                continue;
            }
            if(parseBehaviors){
                behaviorNames.add(parts.get(i));
                continue;
            }
            attrs.add(parts.get(i));
        }

        // Construct and return record
        return new Concept(conceptName, attrs, behaviorNames);
    }
}
