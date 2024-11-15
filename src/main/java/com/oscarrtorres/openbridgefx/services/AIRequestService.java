package com.oscarrtorres.openbridgefx.services;

import com.oscarrtorres.openbridgefx.models.YamlData;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AIRequestService extends Service<String> {

    private final YamlData yamlData;
    private final String prompt;

    public AIRequestService(String prompt, YamlData yamlData) {
        this.yamlData = yamlData;
        this.prompt = prompt;
    }

    @Override
    protected Task<String> createTask() {
        return new Task<>() {
            @Override
            protected String call() throws Exception {
                HttpURLConnection connection = getHttpURLConnection();

                int responseCode = connection.getResponseCode();
                String response;
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    StringBuilder responseBuilder = new StringBuilder();
                    try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(connection.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            responseBuilder.append(line);
                        }
                    }
                    response = parseGptResponse(responseBuilder.toString());
                } else {
                    response = "Error: " + responseCode;
                }

                // Save input, parameters, and output to a JSON log file
                return response;
            }
        };
    }

    private @NotNull HttpURLConnection getHttpURLConnection() throws IOException {
        URL url = new URL(yamlData.getChatGpt().getApiUrl());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + yamlData.getChatGpt().getApiKey());
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        // Create a JSON object for the request
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", yamlData.getChatGpt().getModel());

        // Create the messages array
        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", prompt);
        messages.put(message);

        requestBody.put("messages", messages);
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        return connection;
    }

    public String getCurlCommand() {
        String apiUrl = yamlData.getChatGpt().getApiUrl();
        String apiKey = yamlData.getChatGpt().getApiKey();
        JSONObject requestBody = getRequestBody();

        // Convert the JSON string and escape single quotes for the cURL command
        String requestBodyString = requestBody.toString().replace("'", "'\\''");

        // Construct the cURL command

        return "curl -X POST " +
                "'" + apiUrl + "' " +
                "-H 'Authorization: Bearer " + apiKey + "' " +
                "-H 'Content-Type: application/json' " +
                "-d '" + requestBodyString + "'";
    }

    private @NotNull JSONObject getRequestBody() {
        String model = yamlData.getChatGpt().getModel();

        // Escape special characters in the prompt for JSON
        String safePrompt = prompt.replace("\"", "\\\""); // Escape double quotes

        // Create a JSON object for the request body
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", model);

        // Create the messages array
        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", safePrompt);
        messages.put(message);

        requestBody.put("messages", messages);
        return requestBody;
    }


    private String parseGptResponse(String jsonResponse) {
        // Parse the JSON response
        JSONObject jsonObject = new JSONObject(jsonResponse);

        // Get the "choices" array from the JSON object
        JSONArray choices = jsonObject.getJSONArray("choices");

        // Extract the content from the first choice

        return choices.getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
    }

}

