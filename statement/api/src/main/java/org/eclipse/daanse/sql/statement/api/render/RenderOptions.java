/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.sql.statement.api.render;

/**
 * Options controlling SQL output.
 *
 * @param formatted whether to format the SQL onto multiple indented lines
 * @param indent    the indent unit used when {@code formatted} is true
 * @param comments  whether to emit explanatory comments carried by the
 *                  statement nodes (a header comment, per-column and per-join
 *                  comments). Defaults to {@code false}; the executed SQL is
 *                  rendered with comments off so it stays byte-identical.
 * @param commentStyle how comments are spelled when {@code comments} is on:
 *                  {@code LINE} ({@code -- ...} on its own line) or
 *                  {@code BLOCK} (inline {@code /* ... *}{@code /}).
 */
public record RenderOptions(boolean formatted, String indent, boolean comments, CommentStyle commentStyle) {

    /**
     * How comments are rendered. {@code BLOCK} = inline {@code /* … *}{@code /}
     * (safe anywhere, used for compact output); {@code LINE} = end-of-line
     * {@code -- …} on its own line before each element (only meaningful with
     * {@code formatted}, much more readable).
     */
    public enum CommentStyle {
        BLOCK, LINE
    }

    /** Backwards-compatible 2-arg form (comments off). */
    public RenderOptions(boolean formatted, String indent) {
        this(formatted, indent, false, CommentStyle.BLOCK);
    }

    /** Backwards-compatible 3-arg form (BLOCK style). */
    public RenderOptions(boolean formatted, String indent, boolean comments) {
        this(formatted, indent, comments, CommentStyle.BLOCK);
    }

    public static RenderOptions compact() {
        return new RenderOptions(false, "    ", false, CommentStyle.BLOCK);
    }

    public static RenderOptions multiLine() {
        return new RenderOptions(true, "    ", false, CommentStyle.BLOCK);
    }

    /**
     * A copy of these options with comment emission turned on/off (keeps the
     * current style).
     */
    public RenderOptions withComments(boolean emit) {
        return new RenderOptions(formatted, indent, emit, commentStyle);
    }

    /** A copy of these options with comment emission and an explicit style. */
    public RenderOptions withComments(boolean emit, CommentStyle style) {
        return new RenderOptions(formatted, indent, emit, style);
    }

    /**
     * The style actually used: {@code LINE} only when {@code formatted}; otherwise
     * {@code BLOCK}.
     */
    public CommentStyle effectiveCommentStyle() {
        return (commentStyle == CommentStyle.LINE && formatted) ? CommentStyle.LINE : CommentStyle.BLOCK;
    }
}
