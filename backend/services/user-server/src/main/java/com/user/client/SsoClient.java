package com.user.client;

import com.sso.grpc.SsoServiceGrpc;
import com.sso.grpc.SsoServiceProto;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Component
public class SsoClient {

    @GrpcClient("sso-server")
    private SsoServiceGrpc.SsoServiceBlockingStub ssoServiceStub;

    public String validateTicket(String ticket) {
        try {
            SsoServiceProto.TicketValidationRequest request = SsoServiceProto.TicketValidationRequest.newBuilder()
                    .setTicket(ticket)
                    .build();
            
            SsoServiceProto.TicketValidationResponse response = ssoServiceStub.validateTicket(request);
            
            if (response.getValid()) {
                return response.getUsername();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
