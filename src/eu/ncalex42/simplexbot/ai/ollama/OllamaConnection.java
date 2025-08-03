package eu.ncalex42.simplexbot.ai.ollama;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.json.JSONObject;

import eu.ncalex42.simplexbot.TimeUtil;
import eu.ncalex42.simplexbot.Util;
import eu.ncalex42.simplexbot.simplex.SimplexConnection;

public class OllamaConnection {

    public static String generateResponse(String model, String systemPrompt, String prompt, int readTimeoutMilliseconds,
            SimplexConnection simplexConnection, List<String> contactsForReporting, List<String> groupsForReporting) {

        if ((null == model) || model.isBlank()) {
            Util.logError("A.I. model is missing, ignoring request!", simplexConnection, contactsForReporting,
                    groupsForReporting);
            return null;
        }

        if (null == prompt) {
            Util.logError("Prompt is 'null', ignoring request!", simplexConnection, contactsForReporting,
                    groupsForReporting);
            return null;
        }

        HttpURLConnection connection = null;
        try {
            final JSONObject request = buildOllamaRequest(model, systemPrompt, prompt);

            final URL url = new URL(OllamaConstants.OLLAMA_GENERATE_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);
            connection.setReadTimeout(readTimeoutMilliseconds);

            try (BufferedOutputStream outputStream = new BufferedOutputStream(connection.getOutputStream())) {
                final byte[] data = request.toString().getBytes(StandardCharsets.UTF_8);
                outputStream.write(data, 0, data.length);
            }

            final long start = System.currentTimeMillis();

            String response = null;
            final int responseCode = connection.getResponseCode();

            if (HttpURLConnection.HTTP_OK == responseCode) {

                final StringBuilder responseBuilder = new StringBuilder();
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        responseBuilder.append(line);
                    }
                }
                response = responseBuilder.toString();

            } else {

                final StringBuilder errorBuilder = new StringBuilder();
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        errorBuilder.append(line);
                    }
                }
                final String errorMessage = errorBuilder.toString();

                Util.logError(
                        "Ollama returned an error for model '" + model + "': [" + responseCode + " "
                                + connection.getResponseMessage() + "] " + errorMessage + "\n\n*Prompt (length = "
                                + prompt.length() + " characters) started with:*\n"
                                + (prompt.length() <= 100 ? prompt : prompt.substring(0, 100) + " [...]"),
                        simplexConnection, contactsForReporting, groupsForReporting);
                return null;
            }

            final long stop = System.currentTimeMillis();

            final long durationInSeconds = (stop - start) / TimeUtil.MILLISECONDS_PER_SECOND;
            final long durationInMinutes = durationInSeconds / 60L;
            final String duration;
            if (durationInSeconds <= 60) {
                duration = "!2 " + durationInSeconds + "! second(s)";
            } else if (durationInMinutes < 5) {
                duration = "!5 " + durationInMinutes + "! minute(s)";
            } else if (durationInMinutes < 10) {
                duration = "!4 " + durationInMinutes + "! minutes";
            } else {
                duration = "!1 " + durationInMinutes + "! minutes";
            }

            Util.log(model + " response took " + duration + " for prompt with " + prompt.length() + " characters",
                    simplexConnection, contactsForReporting, groupsForReporting);

            if (null != response) {
                response = parseOllamaResponse(response);
            }

            return response;

        } catch (final Exception ex) {
            Util.logError(
                    "Unexpected exception while communicating with ollama model '" + model + "': "
                            + Util.getStackTraceAsString(ex) + "\n\n*Prompt (length = " + prompt.length()
                            + " characters) started with:*\n"
                            + (prompt.length() <= 100 ? prompt : prompt.substring(0, 100) + " [...]"),
                    simplexConnection, contactsForReporting, groupsForReporting);
            return null;

        } finally {
            if (null != connection) {
                connection.disconnect();
            }
        }
    }

    private static JSONObject buildOllamaRequest(String model, String systemPrompt, String prompt) {

        final JSONObject request = new JSONObject();
        request.put(OllamaConstants.MODEL_KEY, model.strip());
        request.put(OllamaConstants.PROMPT_KEY, prompt);
        request.put(OllamaConstants.STREAM_KEY, false);
        request.put(OllamaConstants.THINK_KEY, false);
        request.put(OllamaConstants.KEEP_ALIVE_KEY, 60);

        if ((null != systemPrompt) && !systemPrompt.isEmpty()) {
            request.put(OllamaConstants.SYSTEM_KEY, systemPrompt);
        }

        return request;
    }

    private static String parseOllamaResponse(String response) {
        final JSONObject responseJson = new JSONObject(response);
        return responseJson.getString(OllamaConstants.RESPONSE_KEY);
    }
}
