package org.moire.ultrasonic.service;

/**
 * Abstract class for consumers with two parameters
 * @param <T> The type of the first object to consume
 * @param <U> The type of the second object to consume
 */
public abstract class BiConsumer<T, U>
{
    public abstract void accept(T t, U u);
}
