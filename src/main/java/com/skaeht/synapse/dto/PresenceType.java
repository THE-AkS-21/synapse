package com.skaeht.synapse.dto;

/**
 * Enum representing different types of presence events
 */
public enum PresenceType {
    /**
     * User came online / joined a room
     */
    ONLINE,

    /**
     * User went offline / left a room
     */
    OFFLINE,

    /**
     * User started typing in a room
     */
    TYPING,

    /**
     * User stopped typing in a room
     */
    STOPPED_TYPING
}
