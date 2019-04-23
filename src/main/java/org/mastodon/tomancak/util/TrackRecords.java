package org.mastodon.tomancak.util;

import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;

/**
 * Collection of individual tracks available for a time lapse data.
 * Every track is represented with the embedded class TrackDataCache.Track.
 *
 * @author Vladimir Ulman, 2018
 */
public class TrackRecords
{
	/** All tracks are stored here. Must hold: tracks.get(id).ID == id */
	protected HashMap<Integer,Track> tracks = new HashMap<>();
	//------------------------------------------------------------------------


	/** Declares existence of a new track starting from time point 'curTime'
	    with 'parentID', returns unique ID of this new track. */
	public int startNewTrack(final int curTime, final int parentID)
	{
		final int ID = this.getNextAvailTrackID();
		tracks.put(ID, new Track(ID, curTime, parentID));
		return ID;
	}

	/** Declares existence of a new track starting from time point 'curTime'
	    with no parent, returns unique ID of this new track. */
	public int startNewTrack(final int curTime)
	{
		return this.startNewTrack(curTime,0);
	}

	/** Updates the last time point of the track 'ID'.
	    A track becomes finished/closed by stopping updating it. */
	public void updateTrack(final int ID, final int curTime)
	{
		tracks.get(ID).prolongTrack(curTime);
	}

	public int getParentOfTrack(int ID)
	{
		return ( tracks.get(ID) != null ? tracks.get(ID).m_parent : 0 );
	}

	public int getStartTimeOfTrack(int ID)
	{
		return ( tracks.get(ID) != null ? tracks.get(ID).m_begin : 0 );
	}

	public int getEndTimeOfTrack(int ID)
	{
		return ( tracks.get(ID) != null ? tracks.get(ID).m_end : 0 );
	}

	/** Removes entire record about the track. */
	public void removeTrack(final int ID)
	{
		tracks.remove(ID);
	}
	//------------------------------------------------------------------------


	public void exportToConsole()
	{
	    exportToConsole(0);
	}

	public void exportToConsole(final int timeShift)
	{
		for (final Track t : tracks.values())
			System.out.println(t.exportToString(timeShift));
	}

	/** Writes the current content into 'outFileName', possibly overwriting it.
	    The method can throw RuntimeException if things go wrong. */
	public void exportToFile(final String outFileName)
	{
		exportToFile(outFileName,0);
	}

	/** Writes the current content into 'outFileName', possibly overwriting it.
	    The reported times are adjusted (incremented) with 'timeShift'.
	    The method can throw RuntimeException if things go wrong. */
	public void exportToFile(final String outFileName, final int timeShift)
	{
		try
		{
			final BufferedWriter f = new BufferedWriter( new FileWriter(outFileName) );
			for (final Track t : tracks.values())
			{
				f.write(t.exportToString(timeShift));
				f.newLine();
			}
			f.close();
		}
		catch (IOException e) {
			//just forward the exception to whom it may concern
			throw new RuntimeException(e);
		}
	}
	//------------------------------------------------------------------------


	/** Helper track ID tracker... */
	private int lastUsedTrackID = 0;

	/** Returns next available non-colliding track ID. */
	public int getNextAvailTrackID()
	{
		++lastUsedTrackID;
		return lastUsedTrackID;
	}


	//------------------------------------------------------------------------
	/**
	 * Record of just one track. It stores exactly all attributes that
	 * are used in the text file that accompanies the image data. This
	 * file typically contains suffix track.txt, e.g. man_track.txt is
	 * used for ground truth data.
	 */
	public static class Track
	{
		/** Track identifier (ID), this value one should find in the image data.
		 The value must be strictly positive. */
		final int m_id;

		/** The number of time point (frame) in which the track begins.
		 The track is supposed to exist since this time point (inclusive). */
		final int m_begin;

		/** The number of time point (frame) in which the track ends.
		 The track is supposed to exist up to this time point (inclusive). */
		int m_end;

		/** Identifier (ID) of the parent track, leave 0 if no parent exists. */
		final int m_parent;

		/** Explicit constructor. */
		Track(final int id, final int begin, final int end, final int parent)
		{
			m_id = id;
			m_begin = begin;
			m_end = end;
			m_parent = parent;
		}

		/** Starts up a new, one-time-point-long track record. */
		public
		Track(final int ID, final int curTime, final int parentID)
		{
			this.m_id = ID;
			this.m_begin = curTime;
			this.m_end   = curTime;
			this.m_parent = parentID;
		}

		/** Updates the life span of a track record up to the given time point.
		 The method checks that track time span should be a continuous
		 interval and throws RuntimeException if 'curTime' would introduce
		 a hole in the interval. */
		public
		void prolongTrack(final int curTime)
		{
			if (curTime != m_end && curTime != m_end+1)
				throw new RuntimeException("Attempted to prolong the track "+m_id
						+" from time "+m_end+" to time "+curTime+".");
			this.m_end = curTime;
		}

		/** Exports tab-delimited four-column string: ID m_begin m_end parentID */
		public
		String exportToString()
		{
			return ( m_id+" "+m_begin+" "+m_end+" "+m_parent );
		}

		/** Exports tab-delimited four-column string: ID m_begin m_end parentID,
		 but report begin and end time adjusted (incremented) with the 'timeShift' param. */
		public
		String exportToString(final int timeShift)
		{
			return ( m_id+" "+(m_begin+timeShift)+" "+(m_end+timeShift)+" "+m_parent );
		}
	}
}
