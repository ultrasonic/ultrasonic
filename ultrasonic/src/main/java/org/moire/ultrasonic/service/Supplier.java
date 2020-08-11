package org.moire.ultrasonic.service;

/**
 * Abstract class for supplying items to a consumer
 * @param <T> The type of the item supplied
 */
public abstract class Supplier<T>
{
    public abstract T get();
}
