package com.sso.grpc;

import com.sso.service.TicketService;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;

@GrpcService
public class SsoGrpcServiceImpl extends SsoServiceGrpc.SsoServiceImplBase {

    @Autowired
    private TicketService ticketService;

    @Override
    public void validateTicket(SsoServiceProto.TicketValidationRequest request, StreamObserver<SsoServiceProto.TicketValidationResponse> responseObserver) {
        SsoServiceProto.TicketValidationResponse.Builder response = SsoServiceProto.TicketValidationResponse.newBuilder();
        
        String username = ticketService.validateTicket(request.getTicket());
        
        if (username != null) {
            ticketService.removeTicket(request.getTicket());
            response.setValid(true)
                    .setUsername(username)
                    .setMessage("Ticket验证成功");
        } else {
            response.setValid(false)
                    .setMessage("Ticket无效或已过期");
        }
        
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }
}
