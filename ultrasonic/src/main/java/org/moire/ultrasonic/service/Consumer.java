package org.moire.ultrasonic.service;

/**
 * Deprecated: Should be replaced with lambdas
 * Abstract class for consumers with one parameter
 * @param <T> The type of the object to consume
 */
@Deprecated
public abstract class Consumer<T>
{
    public abstract void accept(T t);
}
