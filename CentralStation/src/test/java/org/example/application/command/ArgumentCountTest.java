package org.example.application.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArgumentCountTest {
    @Test
    void acceptsExactCount() {
        ArgumentCount count = ArgumentCount.exactly(2);
        assertTrue(count.accepts(2));
        assertFalse(count.accepts(1));
        assertFalse(count.accepts(3));
        assertEquals("2", count.description());
    }

    @Test
    void acceptsInclusiveRange() {
        ArgumentCount count = new ArgumentCount(1, 3);
        assertFalse(count.accepts(0));
        assertTrue(count.accepts(1));
        assertTrue(count.accepts(2));
        assertTrue(count.accepts(3));
        assertFalse(count.accepts(4));
        assertEquals("1..3", count.description());
    }

    @Test
    void rejectsInvalidRanges() {
        assertThrows(IllegalArgumentException.class, () -> new ArgumentCount(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ArgumentCount(2, 1));
    }
}