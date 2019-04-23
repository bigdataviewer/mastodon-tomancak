package org.mastodon.tomancak;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;

import org.mastodon.revised.mamut.MamutAppModel;
import org.mastodon.revised.model.AbstractModelImporter;
import org.mastodon.revised.model.mamut.Link;
import org.mastodon.revised.model.mamut.Model;
import org.mastodon.revised.model.mamut.ModelGraph;
import org.mastodon.revised.model.mamut.Spot;
import org.mastodon.revised.ui.util.FileChooser;
import org.mastodon.revised.ui.util.ExtensionFileFilter;

import net.imglib2.realtransform.AffineTransform3D;

public class ReadPointsTXT
{
	public static void importThreeColumnPoints( final MamutAppModel appModel )
	{ importPoints(appModel,false); }

	public static void importFourColumnPoints( final MamutAppModel appModel )
	{ importPoints(appModel,true); }

	static void importPoints(final MamutAppModel appModel, final boolean fourthColumnIsTime)
	{
		//open a folder choosing dialog
		File selectedFile = FileChooser.chooseFile(null, null,
				new ExtensionFileFilter("txt"),
				"Choose TXT file to read the spots from points from it:",
				FileChooser.DialogType.LOAD,
				FileChooser.SelectionMode.FILES_ONLY);

		//cancel button ?
		if (selectedFile == null) return;

		//check we can open the file; and complain if not
		if (selectedFile.canRead() == false)
			throw new IllegalArgumentException("Cannot read the selected file: "+selectedFile.getAbsolutePath());

		Scanner s = null;

		//-------------------------------------------------
		//scanning params:
		final String delim = "\t";
		final double spotRadius = 10;
		//-------------------------------------------------

		//define the spot size/radius
		final double[][] cov = new double[3][3];
		cov[0][0] = spotRadius*spotRadius;
		cov[1][1] = spotRadius*spotRadius;
		cov[2][2] = spotRadius*spotRadius;

		final Model model = appModel.getModel();
		final ModelGraph graph = model.getGraph();
		Spot spot = graph.vertices().createRef();
		final Spot oSpot = graph.vertices().createRef();
		final Link linkRef = graph.edgeRef();

		final AffineTransform3D transform = new AffineTransform3D();
		appModel.getSharedBdvData().getSources().get(0).getSpimSource().getSourceTransform(0,0, transform);

		final int timeF = appModel.getMinTimepoint();
		final int timeT = appModel.getMaxTimepoint();

		final double[] coords = new double[3];
		int time;

		new AbstractModelImporter< Model >( model ){{ startImport(); }};

		try {
			s = new Scanner(new BufferedReader(new FileReader(selectedFile.getAbsolutePath())));

			while (s.hasNext())
			{
				//read and prepare the spot spatial coordinate
				s.useDelimiter(delim);
				coords[0] = Float.parseFloat(s.next());
				coords[1] = Float.parseFloat(s.next());
				if (!fourthColumnIsTime) s.reset();
				coords[2] = Float.parseFloat(s.next());
				transform.apply(coords,coords);

				if (fourthColumnIsTime)
				{
					//add to the parsed-out time
					s.reset();
					time = Integer.parseInt(s.next());
					graph.addVertex( spot ).init( time, coords, cov );
				}
				else
				{
					//add to all time points available, connect them with edges
					spot = graph.addVertex( spot ).init( timeF, coords, cov );
					oSpot.refTo(spot);

					for (int t = timeF+1; t <= timeT; ++t)
					{
						spot = graph.addVertex( spot ).init( t, coords, cov );
						graph.addEdge( oSpot, spot, linkRef ).init();
						oSpot.refTo(spot);
					}
				}
			}
		} catch (IOException e) {
			//report the original error message further
			e.printStackTrace();
		} finally {
			if (s != null)
			{
				s.close();
			}
			graph.vertices().releaseRef(spot);
			graph.vertices().releaseRef(oSpot);
			graph.releaseRef(linkRef);
			new AbstractModelImporter< Model >( model ){{ finishImport(); }};
		}

		//this.context().getService(LogService.class).log().info("Loaded file: "+selectedFile.getAbsolutePath());
		System.out.println("Loaded file: "+selectedFile.getAbsolutePath());
	}
}
