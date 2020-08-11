package org.moire.ultrasonic.service;

/**
 * Abstract class for consumers with one parameter
 * @param <T> The type of the object to consume
 */
public abstract class Consumer<T>
{
    public abstract void accept(T t);
}
