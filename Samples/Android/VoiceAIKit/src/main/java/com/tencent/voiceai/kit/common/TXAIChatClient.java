/**
 * Copyright (c) 2026 Tencent. All rights reserved.
 * Module:   AIChat @ TXLiteAVSDK
 */

package com.tencent.voiceai.kit.common;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * OpenAI 兼容协议的流式对话客户端。
 * 使用 HttpURLConnection 直接请求 /v1/chat/completions，stream=true，
 * 逐行解析 SSE（data: {json}），把 delta.content 通过回调吐出。
 *
 * 线程模型：{@link #streamChat} 为阻塞调用，请在工作线程执行；回调也在该工作线程触发。
 */
public class TXAIChatClient {

    private static final String TAG = "TXAIChatClient";

    /** 一条对话消息。role: "system" / "user" / "assistant"。 */
    public static class ChatMessage {
        public final String role;
        public final String content;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    /** 流式回调。 */
    public interface StreamCallback {
        /** 每收到一段增量文本触发一次。 */
        void onDelta(String delta);

        /** 流正常结束（收到 [DONE] 或流关闭）。fullText 为完整回复。 */
        void onCompleted(String fullText);

        /** 出错。 */
        void onError(String message);
    }

    private final String apiUrl;
    private final String apiKey;
    private final String model;

    private volatile boolean cancelled = false;

    public TXAIChatClient(String apiUrl, String apiKey) {
        this(apiUrl, apiKey, "default");
    }

    public TXAIChatClient(String apiUrl, String apiKey, String model) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    public void cancel() {
        cancelled = true;
    }

    /**
     * 发起一次流式对话。阻塞直到结束或出错。
     *
     * @param messages 完整的上下文消息（含 system / 历史 / 最新 user）。
     */
    public void streamChat(List<ChatMessage> messages, StreamCallback callback) {
        cancelled = false;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(apiUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Accept", "text/event-stream");

            byte[] body = buildRequestBody(messages).getBytes(StandardCharsets.UTF_8);
            OutputStream os = conn.getOutputStream();
            os.write(body);
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            if (code < 200 || code > 299) {
                callback.onError("HTTP " + code + ": " + readErrorBody(conn));
                return;
            }

            StringBuilder sb = new StringBuilder();
            InputStream inputStream = conn.getInputStream();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (cancelled) {
                    break;
                }
                if (line.isEmpty()) {
                    continue;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring("data:".length()).trim();
                if ("[DONE]".equals(data)) {
                    break;
                }
                String delta = parseDelta(data);
                if (delta == null || delta.isEmpty()) {
                    continue;
                }
                sb.append(delta);
                callback.onDelta(delta);
            }
            reader.close();

            if (!cancelled) {
                callback.onCompleted(sb.toString());
            }
        } catch (Exception e) {
            Log.e(TAG, "streamChat error", e);
            if (!cancelled) {
                callback.onError(e.getMessage() == null ? "网络错误" : e.getMessage());
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 构造 OpenAI chat/completions 请求体。 */
    private String buildRequestBody(List<ChatMessage> messages) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("model", model);
        root.put("stream", true);
        JSONArray messageArray = new JSONArray();
        for (ChatMessage message : messages) {
            JSONObject item = new JSONObject();
            item.put("role", message.role);
            item.put("content", message.content);
            messageArray.put(item);
        }
        root.put("messages", messageArray);
        return root.toString();
    }

    /** 从单条 SSE data JSON 中取 choices[0].delta.content。 */
    private String parseDelta(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray choices = root.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                return null;
            }
            JSONObject first = choices.optJSONObject(0);
            if (first == null) {
                return null;
            }
            JSONObject delta = first.optJSONObject("delta");
            if (delta == null) {
                return null;
            }
            return delta.optString("content", null);
        } catch (JSONException e) {
            return null;
        }
    }

    private String readErrorBody(HttpURLConnection conn) {
        try {
            InputStream stream = conn.getErrorStream();
            if (stream == null) {
                return "(no body)";
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return "(read error body failed)";
        }
    }
}
