package com.babymonitor.entity;

/**
 * The kind of alert a notification came from.
 *
 * <p>Persisted as the PostgreSQL enum {@code babymonitor.event_type}, so the
 * constant names must match the labels exactly: {@code Video}, {@code Audio},
 * {@code System} or {@code Environment}.
 */
public enum EventType {

    /** A video/motion alert; carries a link to the relevant clip. */
    Video,

    /** An audio-only alert; has no video link. */
    Audio,

    /** An app health alert, such as a camera's Motion process dying; no video link. */
    System,

    /** A nursery reading outside the Environment page's bounds; no video link. */
    Environment
}
