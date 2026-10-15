package com.babymonitor.entity;

/**
 * The kind of alert a notification came from.
 *
 * <p>Persisted as the PostgreSQL enum {@code babymonitor.event_type}, so the
 * constant names must match the labels exactly: {@code Video} or {@code Audio}.
 */
public enum EventType {

    /** A video/motion alert; carries a link to the relevant clip. */
    Video,

    /** An audio-only alert; has no video link. */
    Audio
}
