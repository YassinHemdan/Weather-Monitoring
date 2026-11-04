package org.example.application.command;

public record ArgumentCount(int minimum, int maximum) {

    public ArgumentCount {
        if (minimum < 0 || maximum < minimum) {
            throw new IllegalArgumentException(
                    "Invalid argument-count range");
        }
    }

    public static ArgumentCount exactly(int count) {
        return new ArgumentCount(count, count);
    }

    public boolean accepts(int count) {
        return count >= minimum && count <= maximum;
    }

    public String description() {
        if (minimum == maximum) {
            return Integer.toString(minimum);
        }

        return minimum + ".." + maximum;
    }
}
