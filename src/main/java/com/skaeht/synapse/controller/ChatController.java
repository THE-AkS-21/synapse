package com.skaeht.synapse.controller;

import com.skaeht.synapse.dto.event.ChatMessage;
import com.skaeht.synapse.entity.DirectMessage;
import com.skaeht.synapse.entity.Message;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.service.DirectMessageService;
import com.skaeht.synapse.service.MessageService;
import com.skaeht.synapse.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.security.Principal;

@Slf4j
@Controller
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService roomMessageService;
    private final DirectMessageService directMessageService;
    private final UserService userService;

    public ChatController(SimpMessagingTemplate messagingTemplate, MessageService roomMessageService, DirectMessageService directMessageService, UserService userService) {
        this.messagingTemplate = messagingTemplate;
        this.roomMessageService = roomMessageService;
        this.directMessageService = directMessageService;
        this.userService = userService;
    }

    @MessageMapping("/room/{roomId}")
    public void sendRoomMessage(@DestinationVariable String roomId, @Payload ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
        message.setRoomId(roomId);

        Principal principal = headerAccessor.getUser();
        if (principal == null || principal.getName() == null) {
            log.warn("Unauthenticated message attempt to room {}", roomId);
            return;
        }

        try {
            User sender = userService.findByEmail(principal.getName()).orElseThrow();

            message.setSenderId(sender.getId());
            message.setSenderUsername(sender.getUsername());

            // FIXED: Ensure timestamp is set to server time before passing to the DB Service
            if (message.getTimestamp() == 0) {
                message.setTimestamp(System.currentTimeMillis());
            }

            Message savedMsg = roomMessageService.saveMessage(message);

            ChatMessage outMsg = ChatMessage.builder()
                    .id(savedMsg.getMessageId())
                    .roomId(savedMsg.getRoom().getId())
                    .senderId(savedMsg.getSender().getId())
                    .senderUsername(savedMsg.getSender().getUsername())
                    .content(savedMsg.getContent())
                    .timestamp(savedMsg.getTimestamp())
                    .build();

            messagingTemplate.convertAndSend("/topic/chat/" + roomId, outMsg);

        } catch (Exception e) {
            log.error("Failed to save and broadcast message in room {}", roomId, e);
        }
    }

    @MessageMapping("/dm.send")
    public void sendDirectMessage(@Payload ChatMessage dm, SimpMessageHeaderAccessor headerAccessor) {
        Principal principal = headerAccessor.getUser();
        if (principal == null || principal.getName() == null) return;
        if (dm.getReceiverUsername() == null || dm.getContent() == null) return;

        try {
            User sender = userService.findByEmail(principal.getName()).orElseThrow();
            User receiver = userService.findByUsername(dm.getReceiverUsername()).orElseThrow();

            DirectMessage savedDm = directMessageService.saveDirectMessage(sender.getId(), receiver.getId(), dm.getContent());

            ChatMessage outDm = ChatMessage.builder()
                    .id(String.valueOf(savedDm.getId()))
                    .roomId("dm")
                    .senderId(sender.getId())
                    .senderUsername(sender.getUsername())
                    .receiverUsername(receiver.getUsername())
                    .content(savedDm.getContent())
                    .timestamp(savedDm.getTimestamp().toEpochMilli())
                    .build();

            messagingTemplate.convertAndSendToUser(dm.getReceiverUsername(), "/queue/messages", outDm);
            messagingTemplate.convertAndSendToUser(sender.getUsername(), "/queue/messages", outDm);

        } catch (Exception e) {
            log.error("Error processing DM", e);
        }
    }
}