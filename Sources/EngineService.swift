import Foundation

struct TopMove: Codable {
    let move: String
    let move_san: String
    let evaluation: [String: Int]?
    let depth: Int?
}

struct AnalyzeResponse: Codable {
    let top_moves: [TopMove]
}

class EngineService {
    let baseURL: URL

    init(baseURL: URL) {
        self.baseURL = baseURL
    }

    func analyze(fen: String, multiPV: Int = 3, time: Double = 0.15, completion: @escaping (Result<[TopMove], Error>) -> Void) {
        let url = baseURL.appendingPathComponent("analyze")
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: Any] = [
            "fen": fen,
            "multi_pv": multiPV,
            "time": time
        ]

        do {
            req.httpBody = try JSONSerialization.data(withJSONObject: body, options: [])
        } catch {
            completion(.failure(error))
            return
        }

        let task = URLSession.shared.dataTask(with: req) { data, resp, err in
            if let err = err {
                completion(.failure(err))
                return
            }
            guard let data = data else {
                completion(.failure(NSError(domain: "EngineService", code: 0, userInfo: [NSLocalizedDescriptionKey: "no data"])))
                return
            }
            do {
                let decoder = JSONDecoder()
                let result = try decoder.decode(AnalyzeResponse.self, from: data)
                completion(.success(result.top_moves))
            } catch {
                completion(.failure(error))
            }
        }
        task.resume()
    }
}
