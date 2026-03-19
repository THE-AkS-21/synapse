package com.skaeht.synapse.controller;

import com.skaeht.synapse.dto.event.ChatMessage;
import com.skaeht.synapse.entity.DirectMessage;
import com.skaeht.synapse.entity.Message;
import com.skaeht.synapse.entity.User;
import com.skaeht.synapse.service.DirectMessageService;
import com.skaeht.synapse.service.MessageService;
import com.skaeht.synapse.service.UserService;
import com.skaeht.synapse.util.MessageMapperUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * WebSocket Controller handling real-time message routing.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService roomMessageService;
    private final DirectMessageService directMessageService;
    private final UserService userService;

    @MessageMapping("/room/{roomId}")
    public void sendRoomMessage(@DestinationVariable String roomId,
                                @Payload ChatMessage message,
                                SimpMessageHeaderAccessor headerAccessor) {

        Principal principal = headerAccessor.getUser();
        if (principal == null || principal.getName() == null) {
            log.warn("SECURITY WARNING: Unauthenticated WebSocket message attempt to room {}", roomId);
            return;
        }

        try {
            User sender = userService.findByEmail(principal.getName()).orElseThrow();

            message.setRoomId(roomId);
            message.setSenderId(sender.getId());
            message.setSenderUsername(sender.getUsername());

            // Ensure server-authoritative timestamps
            if (message.getTimestamp() == 0) {
                message.setTimestamp(System.currentTimeMillis());
            }

            Message savedMsg = roomMessageService.saveMessage(message);
            ChatMessage outMsg = MessageMapperUtil.toRoomMessageDto(savedMsg);

            messagingTemplate.convertAndSend("/topic/chat/" + roomId, outMsg);

        } catch (Exception e) {
            log.error("Failed to save and broadcast message in room {}. Sender: {}", roomId, principal.getName(), e);
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

            DirectMessage savedDm = directMessageService.saveDirectMessage(
                    sender.getId(), receiver.getId(), dm.getContent());

            ChatMessage outDm = MessageMapperUtil.toDirectMessageDto(savedDm, sender, receiver);

            // Broadcast to both participants' private queues
            messagingTemplate.convertAndSendToUser(dm.getReceiverUsername(), "/queue/messages", outDm);
            messagingTemplate.convertAndSendToUser(sender.getUsername(), "/queue/messages", outDm);

        } catch (Exception e) {
            log.error("Error processing DM from {} to {}", principal.getName(), dm.getReceiverUsername(), e);
        }
    }
}