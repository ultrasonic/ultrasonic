package org.moire.ultrasonic.service;

public abstract class BiConsumer<T, U>
{
    public abstract void accept(T t, U u);
}
