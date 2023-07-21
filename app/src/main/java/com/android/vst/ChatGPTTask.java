package com.android.vst;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatGPTTask extends AsyncTask<String, Void, HashMap<String, String>> {
    private static final String API_KEY = "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";
    private static final String API_URL = "https://api.openai.com/v1/engines/davinci/completions";

    @Override
    protected HashMap<String, String> doInBackground(String... strings) {
        String rawText = strings[0];
        String prompt = "Organize the following data in proper order: " + rawText;

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("prompt", prompt);
            requestBody.put("max_tokens", 1024);
            requestBody.put("temperature", 0.5);

            OkHttpClient client = new OkHttpClient.Builder()
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build();
            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(RequestBody.create(MediaType.parse("application/json"), requestBody.toString()))
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .build();
            Response response = client.newCall(request).execute();
            String responseBody = response.body().string();
            response.close();

            JSONObject jsonResponse = new JSONObject(responseBody);
            Log.d("JSON Response", jsonResponse.toString());

            HashMap<String, String> keyValuePairs = new HashMap<String, String>();
            JSONArray choices = jsonResponse.getJSONArray("choices");

            for (int i = 0; i < choices.length(); i++) {
                JSONObject choice = choices.getJSONObject(i);
                String text = choice.getString("text");
                if (choice.has("data")) {
                    JSONObject data = choice.getJSONObject("data");
                    String key = data.getString("key");
                    String value = data.getString("value");
                    keyValuePairs.put(key, value);
                }
            }

            return keyValuePairs;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected void onPostExecute(HashMap<String, String> result) {
        if (result != null) {
            for (Map.Entry<String, String> entry : result.entrySet()) {
                Log.d("Key", entry.getKey());
                Log.d("Value", entry.getValue());
            }
        } else {
            Log.e("ChatGPTTask", "API call failed");
        }
    }
}
