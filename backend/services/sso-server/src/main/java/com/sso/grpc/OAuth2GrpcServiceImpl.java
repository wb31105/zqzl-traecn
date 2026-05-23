package com.sso.grpc;

import com.sso.entity.Client;
import com.sso.repository.ClientRepository;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * OAuth2 gRPC 服务实现（内部服务间调用）
 * 
 * 协议策略：
 * - 内部微服务：使用 gRPC（高性能、强类型、服务治理）
 * - 第三方应用：使用 HTTP OAuth2 标准接口（兼容性、标准化）
 */
@GrpcService
public class OAuth2GrpcServiceImpl extends SsoServiceGrpc.SsoServiceImplBase {

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${sso.oauth2.issuer-uri:http://localhost:8080}")
    private String issuerUri;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void exchangeToken(SsoServiceProto.TokenExchangeRequest request, 
                              StreamObserver<SsoServiceProto.TokenExchangeResponse> responseObserver) {
        SsoServiceProto.TokenExchangeResponse.Builder response = SsoServiceProto.TokenExchangeResponse.newBuilder();
        
        try {
            Client client = clientRepository.findByClientId(request.getClientId());
            if (client == null) {
                response.setSuccess(false)
                        .setMessage("客户端不存在");
                responseObserver.onNext(response.build());
                responseObserver.onCompleted();
                return;
            }

            if (!passwordEncoder.matches(request.getClientSecret(), client.getClientSecret())) {
                response.setSuccess(false)
                        .setMessage("客户端密钥错误");
                responseObserver.onNext(response.build());
                responseObserver.onCompleted();
                return;
            }

            if (!client.getRedirectUri().equals(request.getRedirectUri())) {
                response.setSuccess(false)
                        .setMessage("回调地址与注册地址不匹配");
                responseObserver.onNext(response.build());
                responseObserver.onCompleted();
                return;
            }

            String tokenUrl = issuerUri + "/oauth2/token";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(request.getClientId(), request.getClientSecret());

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", request.getGrantType());
            params.add("code", request.getCode());
            params.add("redirect_uri", request.getRedirectUri());

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);
            ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(tokenUrl, entity, Map.class);

            if (tokenResponse.getStatusCode().is2xxSuccessful() && tokenResponse.getBody() != null) {
                Map<String, Object> body = tokenResponse.getBody();
                response.setSuccess(true)
                        .setAccessToken((String) body.get("access_token"))
                        .setRefreshToken(body.get("refresh_token") != null ? (String) body.get("refresh_token") : "")
                        .setTokenType((String) body.get("token_type"))
                        .setExpiresIn(body.get("expires_in") != null ? ((Number) body.get("expires_in")).intValue() : 0)
                        .setScope(body.get("scope") != null ? (String) body.get("scope") : "")
                        .setMessage("令牌交换成功");
            } else {
                response.setSuccess(false)
                        .setMessage("令牌交换失败");
            }
        } catch (Exception e) {
            response.setSuccess(false)
                    .setMessage("令牌交换失败: " + e.getMessage());
        }
        
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void validateToken(SsoServiceProto.TokenValidationRequest request,
                              StreamObserver<SsoServiceProto.TokenValidationResponse> responseObserver) {
        SsoServiceProto.TokenValidationResponse.Builder response = SsoServiceProto.TokenValidationResponse.newBuilder();
        
        try {
            String introspectUrl = issuerUri + "/oauth2/introspect";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("token", request.getAccessToken());

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);
            ResponseEntity<Map> introspectResponse = restTemplate.postForEntity(introspectUrl, entity, Map.class);

            if (introspectResponse.getBody() != null) {
                Map<String, Object> body = introspectResponse.getBody();
                boolean active = (Boolean) body.getOrDefault("active", false);
                response.setValid(active)
                        .setUsername(body.get("sub") != null ? (String) body.get("sub") : "")
                        .setClientId(body.get("client_id") != null ? (String) body.get("client_id") : "")
                        .setScope(body.get("scope") != null ? (String) body.get("scope") : "")
                        .setExpiresAt(body.get("exp") != null ? ((Number) body.get("exp")).longValue() : 0)
                        .setMessage(active ? "Token有效" : "Token无效或已过期");
            } else {
                response.setValid(false)
                        .setMessage("Token验证失败");
            }
        } catch (Exception e) {
            response.setValid(false)
                    .setMessage("Token验证失败: " + e.getMessage());
        }
        
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void revokeToken(SsoServiceProto.RevokeTokenRequest request,
                            StreamObserver<SsoServiceProto.RevokeTokenResponse> responseObserver) {
        SsoServiceProto.RevokeTokenResponse.Builder response = SsoServiceProto.RevokeTokenResponse.newBuilder();
        
        try {
            String revokeUrl = issuerUri + "/oauth2/revoke";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(request.getClientId(), request.getClientSecret());

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("token", request.getToken());

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);
            restTemplate.postForEntity(revokeUrl, entity, Void.class);

            response.setSuccess(true)
                    .setMessage("Token已撤销");
        } catch (Exception e) {
            response.setSuccess(false)
                    .setMessage("Token撤销失败: " + e.getMessage());
        }
        
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getClientInfo(SsoServiceProto.ClientInfoRequest request,
                              StreamObserver<SsoServiceProto.ClientInfoResponse> responseObserver) {
        SsoServiceProto.ClientInfoResponse.Builder response = SsoServiceProto.ClientInfoResponse.newBuilder();
        
        try {
            Client client = clientRepository.findByClientId(request.getClientId());
            if (client != null) {
                response.setSuccess(true)
                        .setExists(true)
                        .setClientName(client.getClientName())
                        .setRedirectUri(client.getRedirectUri())
                        .setScope(client.getScope())
                        .setRequireConsent(client.isRequireConsent())
                        .setMessage("查询成功");
            } else {
                response.setSuccess(true)
                        .setExists(false)
                        .setMessage("客户端不存在");
            }
        } catch (Exception e) {
            response.setSuccess(false)
                    .setMessage("查询失败: " + e.getMessage());
        }
        
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }
}
