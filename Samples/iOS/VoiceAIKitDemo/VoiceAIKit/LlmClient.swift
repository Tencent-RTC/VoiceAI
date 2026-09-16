// Copyright (c) 2026 Tencent. All rights reserved.
// VoiceAIKit - 大模型流式客户端 (OpenAI 兼容 /v1/chat/completions, stream=true)
//
// 与 Android TXAIChatClient 保持一致：
//   - streamChat(messages) 阻塞式发起流式请求，回调在后台线程触发
//   - 逐行解析 SSE：只处理 "data:" 行，遇到 "[DONE]" 结束，取 choices[0].delta.content
//   - cancel() 后不再触发 onDelta/onCompleted/onError

import Foundation

/// 一条对话消息。role: "system" / "user" / "assistant"
struct ChatMessage {
    let role: String
    let content: String
}

/// 流式回调（在后台线程触发，调用方需自行切主线程更新 UI）。
protocol LlmStreamCallback: AnyObject {
    func onDelta(_ delta: String)          // 每收到一段增量文本触发一次
    func onCompleted(_ fullText: String)   // 流正常结束，fullText 为完整回复
    func onError(_ message: String)        // 出错
}

final class LlmClient {

    private let apiUrl: String
    private let apiKey: String
    private let model: String

    private var cancelled = false
    private let lock = NSLock()
    private var task: URLSessionDataTask?

    init(apiUrl: String, apiKey: String, model: String = "default") {
        self.apiUrl = apiUrl
        self.apiKey = apiKey
        self.model = model
    }

    private var isCancelled: Bool {
        lock.lock(); defer { lock.unlock() }
        return cancelled
    }

    func cancel() {
        lock.lock()
        cancelled = true
        lock.unlock()
        task?.cancel()
    }

    /// 发起流式对话。`messages` 为完整上下文（system + 历史 + 最新 user）。
    /// 该方法为阻塞式，需在后台队列调用；回调也在该线程触发。
    func streamChat(messages: [ChatMessage], callback: LlmStreamCallback) {
        lock.lock(); cancelled = false; lock.unlock()

        guard let url = URL(string: apiUrl) else {
            callback.onError("非法的 API URL")
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 60
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        request.httpBody = buildRequestBody(messages: messages)

        // 用信号量把 URLSession 的异步流式回调转成阻塞式，回调转发给业务方。
        let semaphore = DispatchSemaphore(value: 0)
        let sb = NSMutableString()

        let delegate = SSEDelegate(
            onLine: { [weak self] line in
                guard let self = self else { return }
                if self.isCancelled { return }
                self.handleLine(line, into: sb, callback: callback)
            },
            onComplete: { [weak self] error in
                defer { semaphore.signal() }
                guard let self = self else { return }
                if self.isCancelled { return }
                if let error = error {
                    let nsErr = error as NSError
                    if nsErr.code == NSURLErrorCancelled { return }
                    callback.onError(error.localizedDescription)
                    return
                }
                callback.onCompleted(sb as String)
            }
        )

        let session = URLSession(configuration: .default, delegate: delegate, delegateQueue: nil)
        let dataTask = session.dataTask(with: request)
        self.task = dataTask
        dataTask.resume()
        semaphore.wait()
        session.finishTasksAndInvalidate()
    }

    // MARK: - Private

    private func buildRequestBody(messages: [ChatMessage]) -> Data? {
        let dict: [String: Any] = [
            "model": model,
            "stream": true,
            "messages": messages.map { ["role": $0.role, "content": $0.content] }
        ]
        return try? JSONSerialization.data(withJSONObject: dict, options: [])
    }

    /// 处理一行 SSE 文本。
    private func handleLine(_ rawLine: String, into sb: NSMutableString, callback: LlmStreamCallback) {
        let raw = rawLine.trimmingCharacters(in: CharacterSet(charactersIn: "\r"))
        if raw.isEmpty { return }
        guard raw.hasPrefix("data:") else { return }
        let data = String(raw.dropFirst("data:".count)).trimmingCharacters(in: .whitespaces)
        if data == "[DONE]" { return }
        guard let delta = parseDelta(data), !delta.isEmpty else { return }
        sb.append(delta)
        callback.onDelta(delta)
    }

    /// 从单条 SSE JSON 中提取 choices[0].delta.content。
    private func parseDelta(_ jsonText: String) -> String? {
        guard let data = jsonText.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let choices = obj["choices"] as? [[String: Any]],
              let first = choices.first,
              let delta = first["delta"] as? [String: Any],
              let content = delta["content"] as? String else {
            return nil
        }
        return content
    }
}

// MARK: - SSE 流式增量解析 Delegate

/// 按到达的字节增量切分出以 \n 分隔的文本行，逐行回调。
private final class SSEDelegate: NSObject, URLSessionDataDelegate {

    private let onLine: (String) -> Void
    private let onComplete: (Error?) -> Void
    private var buffer = Data()

    init(onLine: @escaping (String) -> Void, onComplete: @escaping (Error?) -> Void) {
        self.onLine = onLine
        self.onComplete = onComplete
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask,
                    didReceive response: URLResponse,
                    completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
        completionHandler(.allow)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        buffer.append(data)
        let newline = UInt8(0x0A) // \n
        while let idx = buffer.firstIndex(of: newline) {
            let lineData = buffer.subdata(in: buffer.startIndex..<idx)
            buffer.removeSubrange(buffer.startIndex...idx)
            if let line = String(data: lineData, encoding: .utf8) {
                onLine(line)
            }
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        // 处理最后一段无换行结尾的残留
        if !buffer.isEmpty, let line = String(data: buffer, encoding: .utf8) {
            onLine(line)
            buffer.removeAll()
        }
        onComplete(error)
    }
}
