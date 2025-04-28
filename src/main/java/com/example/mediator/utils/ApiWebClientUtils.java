package com.example.mediator.utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.Objects;

@Component
public class ApiWebClientUtils {

    private final WebClient.Builder webClient;

    @Autowired
    public ApiWebClientUtils(WebClient.Builder webClientBuilder) {
        this.webClient = Objects.requireNonNull(webClientBuilder, "WebClient.Builder must not be null");
    }

    /**
     * 格式化API路徑，確保以/開頭
     * @param apiName API路徑
     * @return 格式化後的API路徑
     */
    private String formatApiPath(String apiName) {
        if (apiName == null || apiName.trim().isEmpty()) {
            throw new IllegalArgumentException("API名稱不能為空");
        }
        return apiName.startsWith("/") ? apiName : "/" + apiName;
    }

    /**
     * 在每個方法中構建新的 WebClient 實例
     * @param baseUrl   後端網域
     * @return
     */
    private WebClient buildWebClient(String baseUrl) {
        return webClient.baseUrl(baseUrl).build();
    }

    /**
     * 呼叫POST API並返回 DTO List
     * @param baseUrl  後端網域
     * @param apiName   後端 API 方法
     * @param requestData   請求輸入參數
     * @param responseType  響應的DTO類型
     * @return DTO列表
     */
    public <T> List<T> callPostApiAndGetDtoList(String baseUrl, String apiName,
                                                Map<String, Object> requestData,
                                                Class<T> responseType) {
        return buildWebClient(baseUrl).post()
                .uri(formatApiPath(apiName))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestData)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<T>>() {})
                .block(Duration.ofSeconds(10));
    }

    /**
     * 呼叫POST API並返回單個DTO對象
     * @param baseUrl  後端網域
     * @param apiName   後端 API 方法
     * @param requestData   請求輸入參數
     * @param responseType  響應的DTO類型
     * @return DTO對象
     */
    public <T> T callPostApiAndGetDto(String baseUrl, String apiName,
                                      Map<String, Object> requestData,
                                      Class<T> responseType) {
        return buildWebClient(baseUrl).post()
                .uri(formatApiPath(apiName))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestData)
                .retrieve()
                .bodyToMono(responseType)
                .block(Duration.ofSeconds(10));
    }

    /**
     * 呼叫GET API並返回 DTO List
     * @param baseUrl  後端網域
     * @param apiName   後端 API 方法
     * @param responseType 響應的DTO類型
     * @return DTO列表
     */
    public <T> List<T> callGetApiAndGetDtoList(String baseUrl, String apiName, Class<T> responseType) {
        return buildWebClient(baseUrl).get()
                .uri(formatApiPath(apiName))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<T>>() {})
                .block(Duration.ofSeconds(10));
    }

    /**
     * 呼叫GET API並返回單個DTO對象
     * @param baseUrl  後端網域
     * @param apiName   後端 API 方法
     * @param responseType 響應的DTO類型
     * @return DTO對象
     */
    public <T> T callGetApiAndGetDto(String baseUrl, String apiName, Class<T> responseType) {
        return buildWebClient(baseUrl).get()
                .uri(formatApiPath(apiName))
                .retrieve()
                .bodyToMono(responseType)
                .block(Duration.ofSeconds(10));
    }
    /**
     * 呼叫DELETE API並返回 DTO List
     * @param baseUrl  後端網域
     * @param apiName   後端 API 方法
     * @param responseType 響應的DTO類型
     * @return DTO列表
     */
    public <T> List<T> callDeleteApiAndGetDtoList(String baseUrl, String apiName, Class<T> responseType) {
        return buildWebClient(baseUrl).delete()
                .uri(formatApiPath(apiName))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<T>>() {})
                .block(Duration.ofSeconds(10));
    }

    /**
     * 呼叫DELETE API並返回單個DTO對象
     * @param baseUrl  後端網域
     * @param apiName   後端 API 方法
     * @param responseType 響應的DTO類型
     * @return DTO對象
     */
    public <T> T callDeleteApiAndGetDto(String baseUrl, String apiName, Class<T> responseType) {
        return buildWebClient(baseUrl).delete()
                .uri(formatApiPath(apiName))
                .retrieve()
                .bodyToMono(responseType)
                .block(Duration.ofSeconds(10));
    }
}