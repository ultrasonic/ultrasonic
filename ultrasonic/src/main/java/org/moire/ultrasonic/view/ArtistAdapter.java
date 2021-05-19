/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2010 (C) Sindre Mehus
 */
package org.moire.ultrasonic.view;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.SectionIndexer;
import android.widget.TextView;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.domain.Artist;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * @author Sindre Mehus
 */
public class ArtistAdapter extends ArrayAdapter<Artist> implements SectionIndexer
{
    private final LayoutInflater layoutInflater;

	// Both arrays are indexed by section ID.
	private final Object[] sections;
	private final Integer[] positions;

	public ArtistAdapter(Context context, List<Artist> artists)
	{
		super(context, R.layout.generic_text_list_item, artists);

		layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		Collection<String> sectionSet = new LinkedHashSet<String>(30);
		List<Integer> positionList = new ArrayList<Integer>(30);

		for (int i = 0; i < artists.size(); i++)
		{
			Artist artist = artists.get(i);
			String index = artist.getIndex();

			if (!sectionSet.contains(index))
			{
				sectionSet.add(index);
				positionList.add(i);
			}
		}

		sections = sectionSet.toArray(new Object[0]);
		positions = positionList.toArray(new Integer[0]);
	}

    @NonNull
    @Override
    public View getView(
            int position,
            @Nullable View convertView,
            @NonNull ViewGroup parent
    ) {
        View rowView = convertView;
        if (rowView == null) {
            rowView = layoutInflater.inflate(R.layout.generic_text_list_item, parent, false);
        }
        ((TextView) rowView).setText(getItem(position).getName());

        return rowView;
    }

	@Override
	public Object[] getSections()
	{
		return sections;
	}

	@Override
	public int getPositionForSection(int section)
	{
		return positions.length > section ? positions[section] : 0;
	}

	@Override
	public int getSectionForPosition(int pos)
	{
		for (int i = 0; i < sections.length - 1; i++)
		{
			if (pos < positions[i + 1])
			{
				return i;
			}
		}

		return sections.length - 1;
	}
}
