package com.thejoshwa.ultrasonic.androidapp.domain;

import java.io.Serializable;

public class ChatMessage implements Serializable
{
	/**
	 *
	 */
	private static final long serialVersionUID = 496544310289324167L;
	private String username;
	private Long time;
	private String message;

	public String getUsername()
	{
		return username;
	}

	public void setUsername(String username)
	{
		this.username = username;
	}

	public Long getTime()
	{
		return time;
	}

	public void setTime(Long time)
	{
		this.time = time;
	}

	public String getMessage()
	{
		return message;
	}

	public void setMessage(String message)
	{
		this.message = message;
	}
}
