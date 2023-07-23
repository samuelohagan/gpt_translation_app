package com.example.translationapp;

import java.util.ArrayList;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class OpenAiManager {
    public class OpenAiAPiCaller {
        private OkHttpClient okHttpClient;

        public OpenAiAPiCaller() {
            okHttpClient = new OkHttpClient();
        }

        public void callOpenAiChatAPi(String systemPrompt, String userPrompt, ArrayList<String> prevConversation, String api_key, Callback callback) {
            ObjectMapper mapper = new ObjectMapper();
            ArrayNode messagesArray = mapper.createArrayNode();
            boolean currentAgentUser = true;
            messagesArray.add(createMessage("system", systemPrompt));
            for (String prevConversationMessage : prevConversation) {
                if(currentAgentUser) {
                    messagesArray.add(createMessage("user", prevConversationMessage));
                    currentAgentUser = false;
                }
                else {
                    messagesArray.add(createMessage("assistant", prevConversationMessage));
                    currentAgentUser = true;
                }
            }
            messagesArray.add(createMessage("user", userPrompt));


            ObjectNode jsonBody = mapper.createObjectNode();
            jsonBody.put("model", "gpt-3.5-turbo");
            jsonBody.set("messages", messagesArray);



            String url = "https://api.openai.com/v1/chat/completions";
            String jsonBodyString  = jsonBody.toString();

            RequestBody requestBody = RequestBody.create(jsonBodyString, MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + api_key)
                    .post(requestBody)
                    .build();
            okHttpClient.newCall(request).enqueue(callback);
        }

        private static ObjectNode createMessage(String role, String content) {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode message = mapper.createObjectNode();
            message.put("role", role);
            message.put("content", content);
            return message;
        }

    }
    public void callGptTranslation(String userPrompt, String language, Callback callback){
        OpenAiAPiCaller apiCaller = new OpenAiAPiCaller();
        String OPENAI_API_KEY = "HIDDEN";
        String systemPrompt = "I have a text string that was converted from an image and it may contain unwanted characters or spaces. Please identify the language, clean up the text, and then translate it into " + language +", make sure translation is same length as original.";
        ArrayList<String> prevConversation = new ArrayList<String>(2);
        prevConversation.add("Excuse me, but do you think it would be possible for me to enter through this door?");
        prevConversation.add("(pointing at the door) Can ah?");
        prevConversation.add("I left the bicycle in the shop for repairing. I should be able to get it back on Sunday and we can go cycling at Teluk Kumbar then. Otherwise I guess we can go and watch a movie.");
        prevConversation.add("My bike repairing leh. So Sunday only cycle at Teluk Kumbar can ah. Or we just watch movie lah.");
        apiCaller.callOpenAiChatAPi(systemPrompt, removeApostrophes(userPrompt), prevConversation, OPENAI_API_KEY, callback);
    }

    public static String removeApostrophes(String input) {
        // Replace all apostrophes with an empty string
        return input.replace("'", "");
    }



}
