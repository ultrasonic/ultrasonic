package org.moire.ultrasonic.service;

public abstract class Consumer<T>
{
    public abstract void accept(T t);
}
