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

    // FIX: Match frontend send destination: /app/room/{roomId}
    @MessageMapping("/room/{roomId}")
    public void sendRoomMessage(@DestinationVariable String roomId, @Payload ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {

        // Ensure the roomId in the payload matches the destination variable
        message.setRoomId(roomId);

        Principal principal = headerAccessor.getUser();
        if (principal != null && message.getSenderUsername() == null) {
            // Fallback to principal name if frontend didn't send it, though ideally it should.
            // Actually, the saveMessage service might handle fetching the user, but let's ensure
            // we have what we need.
            // Note: In your current implementation, messageService.saveMessage expects the ChatMessage to be fully formed.
        }

        try {
            Message savedMsg = roomMessageService.saveMessage(message);

            ChatMessage outMsg = ChatMessage.builder()
                    .id(savedMsg.getMessageId())
                    .roomId(savedMsg.getRoom().getId())
                    .senderId(savedMsg.getSender().getId())
                    .senderUsername(savedMsg.getSender().getUsername())
                    .content(savedMsg.getContent())
                    .timestamp(savedMsg.getTimestamp())
                    .build();

            // FIX: Broadcast to the exact topic the frontend is subscribed to: /topic/chat/{roomId}
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
            // Find sender by EMAIL (since principal.getName() is usually the email from JWT)
            User sender = userService.findByEmail(principal.getName()).orElseThrow();
            // Assuming receiverUsername in payload is actually the username.
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