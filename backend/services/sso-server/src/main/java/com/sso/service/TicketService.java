package com.sso.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TicketService {

    private final Map<String, String> ticketStore = new ConcurrentHashMap<>();

    public String generateTicket(String username) {
        String ticket = "ST-" + UUID.randomUUID().toString();
        ticketStore.put(ticket, username);
        return ticket;
    }

    public String validateTicket(String ticket) {
        return ticketStore.get(ticket);
    }

    public boolean isValidTicket(String ticket) {
        return ticketStore.containsKey(ticket);
    }

    public void removeTicket(String ticket) {
        ticketStore.remove(ticket);
    }
}
