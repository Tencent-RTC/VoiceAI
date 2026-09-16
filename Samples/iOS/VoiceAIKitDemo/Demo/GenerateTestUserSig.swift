/*
 * Module:   GenerateTestUserSig
 *
 * Function: 用于生成测试用的 UserSig。UserSig 是腾讯云为其云服务设计的一种安全保护签名。
 *           其计算方法是对 SDKAppID、UserID 和 EXPIRETIME 进行加密，加密算法为 HMAC-SHA256。
 *
 * Attention: 请不要将如下代码发布到您的线上正式版本的 App 中。
 *            正确的做法是将 UserSig 的计算代码和加密密钥放在您的业务服务器上，
 *            然后由 App 按需向您的服务器获取实时算出的 UserSig。
 *
 * Reference：https://cloud.tencent.com/document/product/269/32688#Server
 */

import Foundation
import CommonCrypto
import zlib

public class GenerateTestUserSig {

    /// 腾讯云 SDKAppId，取自 Config.sdkAppId（请在 Config.swift 中填写）。
    public static private(set) var SDKAPPID: Int = Config.sdkAppId

    /// 签名过期时间，单位秒。默认 7 天。
    public static let EXPIRETIME: Int = 604800

    /// 计算签名用的加密密钥，取自 Config.secretKey（仅供调试，正式环境务必迁移到服务器）。
    public static private(set) var SECRETKEY = Config.secretKey

    /// 计算 UserSig 签名（HMAC-SHA256 + Deflate + URL-safe Base64）。
    public class func genTestUserSig(identifier: String) -> String {
        let current = CFAbsoluteTimeGetCurrent() + kCFAbsoluteTimeIntervalSince1970
        let TLSTime: CLong = CLong(floor(current))
        var obj: [String: Any] = [
            "TLS.ver": "2.0",
            "TLS.identifier": identifier,
            "TLS.sdkappid": SDKAPPID,
            "TLS.expire": EXPIRETIME,
            "TLS.time": TLSTime
        ]
        let keyOrder = ["TLS.identifier", "TLS.sdkappid", "TLS.time", "TLS.expire"]
        var stringToSign = ""
        keyOrder.forEach { key in
            if let value = obj[key] {
                stringToSign += "\(key):\(value)\n"
            }
        }
        let sig = hmac(stringToSign)
        obj["TLS.sig"] = sig!
        guard let jsonData = try? JSONSerialization.data(withJSONObject: obj, options: .sortedKeys) else { return "" }

        let bytes = jsonData.withUnsafeBytes { (result) -> UnsafePointer<Bytef> in
            return result.bindMemory(to: Bytef.self).baseAddress!
        }
        let srcLen: uLongf = uLongf(jsonData.count)
        let upperBound: uLong = compressBound(srcLen)
        let capacity: Int = Int(upperBound)
        let dest: UnsafeMutablePointer<Bytef> = UnsafeMutablePointer<Bytef>.allocate(capacity: capacity)
        var destLen = upperBound
        let ret = compress2(dest, &destLen, bytes, srcLen, Z_BEST_SPEED)
        if ret != Z_OK {
            dest.deallocate()
            return ""
        }
        let count = Int(destLen)
        let result = self.base64URL(data: Data(bytesNoCopy: dest, count: count, deallocator: .free))
        return result
    }

    class func hmac(_ plainText: String) -> String? {
        let cKey = SECRETKEY.cString(using: .ascii)
        let cData = plainText.cString(using: .ascii)
        let cKeyLen = SECRETKEY.lengthOfBytes(using: .ascii)
        let cDataLen = plainText.lengthOfBytes(using: .ascii)
        var cHMAC = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        cHMAC.withUnsafeMutableBufferPointer { buffer in
            CCHmac(CCHmacAlgorithm(kCCHmacAlgSHA256), cKey!, cKeyLen, cData, cDataLen, buffer.baseAddress)
        }
        return Data(cHMAC).base64EncodedString(options: [])
    }

    class func base64URL(data: Data) -> String {
        let result = data.base64EncodedString(options: Data.Base64EncodingOptions(rawValue: 0))
        var final = ""
        result.forEach { char in
            switch char {
            case "+": final += "*"
            case "/": final += "-"
            case "=": final += "_"
            default: final += "\(char)"
            }
        }
        return final
    }
}
