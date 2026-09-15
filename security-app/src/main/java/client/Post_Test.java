package client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class Post_Test {
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // 서버 기본 URL (환경에 맞춰 수정)
    private static final String BASE_URL = "http://localhost:8080";

    public static void main(String[] args) {
        // 1. 회원가입 (Signup) 테스트
//        sendSignupRequest();

        // 2. 로그인 (Signin) 테스트
        sendSigninRequest();
    }

    /**
     * 회원가입 요청 (SignupRequest)
     */
    public static void sendSignupRequest() {
        String url = BASE_URL + "/api/v1/auth/signup"; // 실제 엔드포인트 URL로 변경 필요

        String jsonPayload = """
                {
                    "username": "user1234",
                    "password": "Password123!",
                    "passwordConfirm": "Password123!",
                    "email": "user1234@example.com",
                    "nickname": "개발자",
                    "phoneNumber": "010-1234-5678"
                }
                """;

        sendPostRequest(url, jsonPayload, "회원가입 (Signup)");
    }

    /**
     * 로그인 요청 (SigninRequest)
     */
    public static void sendSigninRequest() {
        String url = BASE_URL + "/api/v1/auth/signin"; // 실제 엔드포인트 URL로 변경 필요

        // SigninRequest DTO 형식
        String jsonPayload = """
                {
                    "username": "user1234",
                    "password": "Password123!"
                }
                """;

        sendPostRequest(url, jsonPayload, "로그인 (Signin)");
    }

    /**
     * HTTP POST 요청 전송 공통 메서드
     */
    private static void sendPostRequest(String url, String jsonPayload, String requestType) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            System.out.println("=== [" + requestType + " 요청 전송] ===");
            System.out.println("URL: " + url);
            System.out.println("Payload:\n" + jsonPayload);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Response Status Code: " + response.statusCode());
            System.out.println("Response Body:\n" + response.body());
            System.out.println("=====================================\n");

        } catch (Exception e) {
            System.err.println("[" + requestType + "] 요청 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
