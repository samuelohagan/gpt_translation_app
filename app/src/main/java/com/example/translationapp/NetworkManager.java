package com.example.translationapp;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;

public class NetworkManager {
    private OkHttpClient okHttpClient;

    public NetworkManager() {
        okHttpClient = new OkHttpClient();
    }

    public void makeRequest(String url, Callback callback) {
        Request request = new Request.Builder()
            .url(url)
            .build();
        Call call = okHttpClient.newCall(request);
        call.enqueue(callback);
    }

}
