package org.mastodon.tomancak;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.mastodon.revised.mamut.MamutAppModel;
import org.mastodon.revised.model.mamut.Spot;
import org.mastodon.revised.ui.util.FileChooser;
import org.mastodon.spatial.SpatioTemporalIndex;
import org.mastodon.revised.ui.util.ExtensionFileFilter;

import net.imglib2.realtransform.AffineTransform3D;

public class WritePointsTXT
{
	public static void exportFourColumnPoints( final MamutAppModel appModel )
	{
		//open a folder choosing dialog
		File selectedFile = FileChooser.chooseFile(null, null,
				new ExtensionFileFilter("txt"),
				"Choose TXT file to write the spots as points to:",
				FileChooser.DialogType.SAVE,
				FileChooser.SelectionMode.FILES_ONLY);

		//cancel button ?
		if (selectedFile == null) return;

		//-------------------------------------------------
		//writing params:
		final String delim = "\t";
		final int timeF = appModel.getMinTimepoint();
		final int timeT = appModel.getMaxTimepoint();
		//-------------------------------------------------

		AffineTransform3D transform = new AffineTransform3D();
		appModel.getSharedBdvData().getSources().get(0).getSpimSource().getSourceTransform(0,0, transform);
		transform = transform.inverse();
		final double[] coords = new double[3];

		final SpatioTemporalIndex< Spot > spots = appModel.getModel().getSpatioTemporalIndex();
		BufferedWriter f;

		try
		{
			f = new BufferedWriter( new FileWriter(selectedFile.getAbsolutePath()) );

			for (int t = timeF; t <= timeT; ++t)
			for (final Spot s : spots.getSpatialIndex(t))
			{
				//convert spot's coordinate into underlying image coordinate system
				s.localize(coords);
				transform.apply(coords,coords);

				f.write(coords[0]+delim
				       +coords[1]+delim
				       +coords[2]+delim
				       +t);
				f.newLine();
			}
			f.close();
		}
		catch (IOException e) {
			//report the original error message further
			e.printStackTrace();
		}

		System.out.println("Wrote file: "+selectedFile.getAbsolutePath());
	}


	public static void exportThreeColumnPointsPerTimepoints( final MamutAppModel appModel )
	{
		//open a folder choosing dialog
		File selectedFolder = FileChooser.chooseFile(null, null,
				new ExtensionFileFilter("txt"),
				"Choose folder to write TXT files with the spots as points:",
				FileChooser.DialogType.SAVE,
				FileChooser.SelectionMode.DIRECTORIES_ONLY);

		//cancel button ?
		if (selectedFolder == null) return;

		//check we can open the file; and complain if not
		if (selectedFolder.canWrite() == false)
			throw new IllegalArgumentException("Cannot write to the selected folder: "+selectedFolder.getAbsolutePath());

		//-------------------------------------------------
		//writing params:
		final String delim = "\t";
		final int timeF = appModel.getMinTimepoint();
		final int timeT = appModel.getMaxTimepoint();
		final String fileNamePattern = "pointCloud_t%04d.txt";
		//-------------------------------------------------

		AffineTransform3D transform = new AffineTransform3D();
		appModel.getSharedBdvData().getSources().get(0).getSpimSource().getSourceTransform(0,0, transform);
		transform = transform.inverse();
		final double[] coords = new double[3];

		final SpatioTemporalIndex< Spot > spots = appModel.getModel().getSpatioTemporalIndex();
		BufferedWriter f;

		try
		{
			for (int t = timeF; t <= timeT; ++t)
			{
				f = new BufferedWriter( new FileWriter(
					selectedFolder.getAbsolutePath() + File.separator + String.format(fileNamePattern,t)
					) );

				for (final Spot s : spots.getSpatialIndex(t))
				{
					//convert spot's coordinate into underlying image coordinate system
					s.localize(coords);
					transform.apply(coords,coords);

					f.write(coords[0]+delim
					       +coords[1]+delim
					       +coords[2]);
					f.newLine();
				}

				f.close();
			}
		}
		catch (IOException e) {
			//report the original error message further
			e.printStackTrace();
		}

		System.out.println("Done exporting.");
	}
}
