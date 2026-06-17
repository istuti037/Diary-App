package mydiary;

import java.net.*; //Creates the API URL
import java.net.http.*; //Sends HTTP POST request
import java.nio.charset.StandardCharsets; //Encodes request as UTF-8
import java.util.ArrayList; //Stores conversation history
import java.util.List; //Interface for ArrayList

public class AIAssistant {
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL   = "llama-3.3-70b-versatile";

    private String             apiKey;
    private List<String[]>     conversationHistory; // [role, text]

    public AIAssistant(String apiKey) {
        this.apiKey = apiKey.trim();
        this.conversationHistory = new ArrayList<>();
    }

    //Regular chat
    public String chat(String userMessage) {
        conversationHistory.add(new String[]{"user", userMessage});
        String reply = callGroq(buildChatBody());
        conversationHistory.add(new String[]{"assistant", reply});
        return reply;
    }

    //Correct a diary entry
    public String correctEntry(String entryText) {
        String prompt =
            "You are a helpful writing assistant. The user has written a diary entry. " +
            "Please correct any spelling mistakes, grammar errors, and punctuation issues. " +
            "Keep the same tone, style, and meaning — do NOT change what the person is saying, " +
            "only fix the language. Return ONLY the corrected text with no explanation, " +
            "no preamble, and no commentary.\n\n" +
            "Diary entry to correct:\n" + entryText;
        return callGroq(buildSingleTurnBody(prompt));
    }

    //Summarize a diary entry
    public String summarizeEntry(String entryText) {
        String prompt =
            "Summarize the following diary entry in 2-3 sentences. " +
            "Be warm and empathetic. Return only the summary.\n\n" + entryText;
        return callGroq(buildSingleTurnBody(prompt));
    }

    //Internal: call Groq API
    private String callGroq(String requestBody) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Groq status: " + response.statusCode());

            if (response.statusCode() == 429)
                return "Rate limit reached. Please wait a moment and try again.";
            if (response.statusCode() != 200)
                return "API Error " + response.statusCode() + ". Check your key or internet.";

            return parseResponse(response.body());

        } catch (Exception e) {
            return "Connection error: " + e.getMessage();
        }
    }

    //Build JSON bodies (OpenAI-compatible format)
    private String buildChatBody() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"").append(MODEL).append("\",\"messages\":[");

        // System message first
        sb.append("{\"role\":\"system\",\"content\":\"You are a warm, friendly diary assistant. ");
        sb.append("Help the user reflect on their day, suggest what to write, summarize entries, ");
        sb.append("or just have a supportive conversation. Keep responses concise and empathetic.\"}");

        for (String[] turn : conversationHistory) {
            sb.append(",{\"role\":\"").append(turn[0]).append("\",");
            sb.append("\"content\":\"").append(escapeJson(turn[1])).append("\"}");
        }

        sb.append("]}");
        return sb.toString();
    }

    private String buildSingleTurnBody(String prompt) {
        return "{\"model\":\"" + MODEL + "\",\"messages\":[" +
               "{\"role\":\"user\",\"content\":\"" + escapeJson(prompt) + "\"}]}";
    }

    //Parse OpenAI-compatible response
    private String parseResponse(String json) {
        try {
            // Look for "content": "..." inside choices[0].message
            int choicesIdx = json.indexOf("\"choices\"");
            if (choicesIdx == -1) return "No response from AI.";

            int contentIdx = json.indexOf("\"content\":", choicesIdx);
            if (contentIdx == -1) return "No content in response.";

            int valueStart = json.indexOf("\"", contentIdx + 10);
            if (valueStart == -1) return "Parse error.";
            valueStart++;

            StringBuilder result = new StringBuilder();
            int i = valueStart;
            while (i < json.length()) {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < json.length()) {
                    char next = json.charAt(i + 1);
                    switch (next) {
                        case 'n':  result.append('\n'); i += 2; break;
                        case 't':  result.append('\t'); i += 2; break;
                        case '"':  result.append('"');  i += 2; break;
                        case '\\': result.append('\\'); i += 2; break;
                        default:   result.append(c);    i++;    break;
                    }
                } else if (c == '"') {
                    break;
                } else {
                    result.append(c);
                    i++;
                }
            }
            String text = result.toString().trim();
            return text.isEmpty() ? "Empty response." : text;

        } catch (Exception e) {
            return "Parse error: " + e.getMessage();
        }
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    public void clearHistory() { conversationHistory.clear(); }
}