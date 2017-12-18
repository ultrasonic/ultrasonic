package org.moire.ultrasonic.domain;

import java.io.Serializable;

public class Genre implements Serializable
{
	/**
	 *
	 */
	private static final long serialVersionUID = -3943025175219134028L;
	private String name;
	private String index;

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public String getIndex()
	{
		return index;
	}

	public void setIndex(String index)
	{
		this.index = index;
	}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Genre genre = (Genre) o;

        if (name != null ? !name.equals(genre.name) : genre.name != null) return false;
        return index != null ? index.equals(genre.index) : genre.index == null;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (index != null ? index.hashCode() : 0);
        return result;
    }

    @Override
	public String toString()
	{
		return name;
	}
}
