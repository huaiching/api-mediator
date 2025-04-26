# API Gateway 中台

## 項目概述

API Gateway 中台是一個基於 Spring Boot 的代理服務，用於統一管理後端 API 的訪問。<br/>
前端只需透過中台的統一網址（例如 `http://localhost:9000/proxy/{backendName}/{path}` ）即可呼叫所有後端服務，無需直接訪問後端實際 URL。

### 主要功能
- **API 代理**：將前端請求轉發到指定的後端服務，支持 `GET`、`POST`、`PUT`、`DELETE` 等 HTTP 方法。
- **Swagger UI 整合**：展示所有後端服務的 API 文檔，並確保 API 請求使用中台的代理 URL。
- **動態後端配置**：透過 `application.yml` 配置後端服務，易於新增或修改。

## 環境要求
- **Java**: 11 或以上
- **Maven**: 3.6 或以上
- **後端服務**：需提供有效的 OpenAPI JSON（例如 `http://localhost:9091/api-docs`）

## 目錄結構
```
src/main/java/com/example/api_mediator/
├── config/                # 配置類
├── controller/            # 控制器層
├── service/               # 服務層
├── client/                # HTTP 客戶端層
├── dto/                   # 資料傳輸對象
├── util/                  # 工具類
└── exception/             # 異常處理
```

## 安裝與啟動

### 1. 檢查 `application.yml` 配置 後端 `swagger api json` 和 `url 設定` 
- 確保 `src/main/resources/application.yml` 包含正確的後端配置。默認配置如下：
  ```yaml
  server:
    port: 9000
  proxy:
    apis:     # 這裡 配置 後端 swagger api json
      - name: proxy1                    # 後端 api 名稱 (要呈現於網址中，不可使用中文)
        url: http://localhost:9091      # 後端 api 網址
        swagger: /api-docs
      - name: proxy2
        url: http://localhost:9092
        swagger: /api-docs
  springdoc:
    api-docs:
      path: /api-docs
    swagger-ui:
      path: /swagger-ui
      urls:               # 這裡 配置 後端 api 要在 中台 呈現的 swagger 設定
        - name: proxy1                  # 右上角的名稱
          url: /proxy/proxy1/api-docs   # 中台 swagger json 網址，網址的中間名(proxy1) 需要跟 proxy.apis.name 的名稱相同 才能進行對應
        - name: proxy2
          url: /proxy/proxy2/api-docs
  ```

- `server.port`: 中台運行端口（默認 9000）。
- `backend.apis`: 後端服務列表，每個服務包含 `name`（服務名稱）、`url`（後端基礎 URL）、`swagger`（OpenAPI 路徑）。
- `springdoc.swagger-ui.urls`: 配置 Swagger UI 中顯示的後端 API 文檔。

### 2. 啟動後端服務
- 使用 IDE（例如 IntelliJ IDEA）直接運行 `ApiGatewayApplication`。

### 3. 訪問 Swagger UI
啟動成功後，訪問以下網址查看和測試 API：
- **Swagger UI**: http://localhost:9000/swagger-ui/index.html
  - 在下拉選單中選擇後端服務（例如 `proxy1`），即可查看其 API 文檔。
  - 所有 API 請求將透過中台代理（例如 `http://localhost:9000/proxy/proxy1/...`）。
- **中台 OpenAPI JSON**: `http://localhost:9000/api-docs`
